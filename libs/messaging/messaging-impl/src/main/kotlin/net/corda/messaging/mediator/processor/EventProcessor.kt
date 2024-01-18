package net.corda.messaging.mediator.processor

import net.corda.data.messaging.mediator.MediatorState
import net.corda.libs.statemanager.api.State
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.RoutingDestination
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.mediator.StateManagerHelper
import net.corda.tracing.addTraceContextToRecord

/**
 * Class to process records received from the consumer.
 * Passes each record to process along with its state to the [config]s [StateAndEventProcessor].
 * Synchronous outputs from the processor are sent immediately and the responses are processed as new inputs.
 * Asynchronous outputs destined for the message bus and states to be saved to the state manager are returned.
 *
 */
class EventProcessor<K : Any, S : Any, E : Any>(
    private val config: EventMediatorConfig<K, S, E>,
    private val stateManagerHelper: StateManagerHelper<S>,
    private val messageRouter: MessageRouter,
    private val mediatorReplayService: MediatorReplayService,
) {

    /**
     * Process a group of events.
     * If the mediator has previously processed a record, then the asynchronous outputs from the previous invocation
     * of the processor will be retrieved from the [MediatorState].In this case, the message processor is not executed again,
     * and the previously retrieved outputs are returned for resending.
     *
     * Otherwise, the message processor is executed and any synchronous calls are sent and responses are processed immediately.
     *
     * Finally, any asynchronous outputs are returned, as well as the [State] object to update.
     *
     * @param group Group of consumer records of various keys
     * @param retrievedStates states for each key
     * @return The asynchronous outputs and state updates grouped by record key
     */
    fun processEvents(
        group: Map<K, List<Record<K, E>>>,
        retrievedStates: Map<String, State>
    ): Map<K, EventProcessingOutput> {
        return group.mapValues { groupEntry ->
            val allConsumerInputs = groupEntry.value
            val groupKey = groupEntry.key.toString()
            val state = retrievedStates.getOrDefault(groupKey, null)
            val mediatorState = stateManagerHelper.deserializeMediatorState(state) ?: createNewMediatorState()
            var processorState = stateManagerHelper.deserializeValue(mediatorState)?.let { stateValue ->
                StateAndEventProcessor.State(
                    stateValue,
                    state?.metadata
                )
            }
            val (nonReplayConsumerInputs, replayOutputs) = getReplayOutputsAndNonReplayInputs(allConsumerInputs, mediatorState)
            if (nonReplayConsumerInputs.isEmpty()) {
                EventProcessingOutput(replayOutputs, StateChangeAndOperation.Noop)
            } else {
                val asyncOutputs = mutableMapOf<Record<K, E>, MutableList<MediatorMessage<Any>>>()
                val processed = try {
                    nonReplayConsumerInputs.onEach { consumerInputEvent ->
                        val queue = ArrayDeque(listOf(consumerInputEvent))
                        while (queue.isNotEmpty()) {
                            val response = config.messageProcessor.onNext(processorState, queue.removeFirst())
                            processorState = response.updatedState
                            val (syncEvents, asyncEvents) = response.responseEvents.map { convertToMessage(it) }.partition {
                                messageRouter.getDestination(it).type == RoutingDestination.Type.SYNCHRONOUS
                            }
                            asyncOutputs.addOutputs(consumerInputEvent, asyncEvents)
                            queue.addAll(processSyncEvents(groupEntry.key, syncEvents))
                        }
                    }
                    mediatorState.outputEvents = mediatorReplayService.getOutputEvents(mediatorState.outputEvents, asyncOutputs)
                    stateManagerHelper.createOrUpdateState(groupKey, state, mediatorState, processorState)
                } catch (e: CordaMessageAPIIntermittentException) {
                    // If an intermittent error occurs here, the RPC client has failed to deliver a message to another part
                    // of the system despite the retry loop implemented there. This should trigger individual processing to
                    // fail.
                    asyncOutputs.clear()
                    stateManagerHelper.failStateProcessing(groupKey, state)
                }

                val stateChangeAndOperation = stateChangeAndOperation(state, processed)
                EventProcessingOutput(replayOutputs + asyncOutputs.values.flatten(), stateChangeAndOperation)
            }
        }
    }

    private fun stateChangeAndOperation(
        state: State?,
        processed: State?
    ) = when {
        state == null && processed != null -> StateChangeAndOperation.Create(processed)
        state != null && processed != null -> StateChangeAndOperation.Update(processed)
        state != null && processed == null -> StateChangeAndOperation.Delete(state)
        else -> StateChangeAndOperation.Noop
    }

    private fun getReplayOutputsAndNonReplayInputs(
        allConsumerInputs: List<Record<K, E>>,
        mediatorState: MediatorState
    ): Pair<List<Record<K, E>>, List<MediatorMessage<Any>>> {
        val cachedOutputs = mutableListOf<MediatorMessage<Any>>()
        val nonReplayOutputs = allConsumerInputs.filter { inputEvent ->
            mediatorReplayService.getReplayEvents(inputEvent, mediatorState)?.let {
                cachedOutputs.addAll(it)
            } == null
        }
        return Pair(nonReplayOutputs, cachedOutputs)
    }

    private fun MutableMap<Record<K, E>, MutableList<MediatorMessage<Any>>>.addOutputs(
        inputEvent: Record<K, E>,
        asyncEvents: List<MediatorMessage<Any>>
    ) = computeIfAbsent(inputEvent) { mutableListOf() }.addAll(asyncEvents)


    private fun createNewMediatorState(): MediatorState {
        return MediatorState.newBuilder()
            .setState(null)
            .setOutputEvents(mutableListOf())
            .build()
    }

    /**
     * Send any synchronous events immediately and feed results back onto the queue.
     */
    private fun processSyncEvents(
        key: K,
        syncEvents: List<MediatorMessage<Any>>
    ): List<Record<K, E>> {
        return syncEvents.mapNotNull { message ->
            val destination = messageRouter.getDestination(message)

            @Suppress("UNCHECKED_CAST")
            val reply = with(destination) {
                message.addProperty(MessagingClient.MSG_PROP_ENDPOINT, endpoint)
                client.send(message) as MediatorMessage<E>?
            }
            reply?.let {
                addTraceContextToRecord(
                    Record(
                        "",
                        key,
                        reply.payload
                    ),
                    message.properties
                )
            }
        }
    }

    private fun convertToMessage(record: Record<*, *>): MediatorMessage<Any> {
        return MediatorMessage(
            record.value!!,
            record.headers.toMessageProperties().also { properties ->
                properties[MessagingClient.MSG_PROP_KEY] = record.key
                if (record.topic != null && record.topic!!.isNotEmpty()) {
                    properties[MessagingClient.MSG_PROP_TOPIC] = record.topic!!
                }
            },
        )
    }

    private fun List<Pair<String, String>>.toMessageProperties() =
        associateTo(mutableMapOf()) { (key, value) -> key to (value as Any) }
}