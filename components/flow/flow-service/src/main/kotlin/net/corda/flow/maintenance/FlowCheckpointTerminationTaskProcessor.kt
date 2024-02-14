package net.corda.flow.maintenance

import net.corda.data.flow.FlowCheckpointTermination
import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.Schemas.ScheduledTask
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID


/**
 * This processor is used to get checkpoints that have reached their terminal state within the flow engine.
 * Checkpoints are held onto for a configurable amount of time to allow for duplicates, and replayed inputs to be passed the flow engine
 * and for the flow engine to replay the outputs for each input.
 * This task will batch the checkpoints to be deleted and pass it to another topic
 * to be deleted by [FlowCheckpointTerminationCleanupProcessor]
 */
@Suppress("unused_parameter")
class FlowCheckpointTerminationTaskProcessor(
    private val stateManager: StateManager,
    config: SmartConfig,
    private val clock: Clock,
    private val batchSize: Int = ID_BATCH_SIZE
) : DurableProcessor<String, ScheduledTaskTrigger> {
    companion object {
        private const val ID_BATCH_SIZE = 500

        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun onNext(events: List<Record<String, ScheduledTaskTrigger>>): List<Record<*, *>> {
        // Filter to the task that this processor cares about. There can be other tasks on this topic.
        return if (events.any { it.value?.name == ScheduledTask.SCHEDULED_TASK_NAME_FLOW_CHECKPOINT_TERMINATION }) {
            val keys = stateManager.selectExpired()
            val batches = batchIds(keys)
            logger.info("Fanning out deletion of $keys checkpoints")
            batches.map {
                FlowCheckpointTermination(it)
            }.map {
                Record(Schemas.Flow.FLOW_CHECKPOINT_TERMINATION, UUID.randomUUID().toString(), it)
            }
        } else listOf()
    }

    private fun batchIds(ids: List<String>): List<List<String>> {
        return ids.chunked(batchSize)
    }

    override val keyClass = String::class.java
    override val valueClass = ScheduledTaskTrigger::class.java
}
