package net.corda.flow.service

import com.typesafe.config.ConfigValueFactory
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.pipeline.factory.FlowEventProcessorFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MultiSourceEventMediator
import net.corda.messaging.api.mediator.RoutingDestination.Companion.routeTo
import net.corda.messaging.api.mediator.config.EventMediatorConfigBuilder
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactoryFactory
import net.corda.messaging.api.mediator.factory.MediatorProducerFactoryFactory
import net.corda.messaging.api.mediator.factory.MessageRouterFactory
import net.corda.messaging.api.mediator.factory.MultiSourceEventMediatorFactory
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.schema.Schemas.Flow.FLOW_EVENT_TOPIC
import net.corda.schema.Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.MessagingConfig.MAX_ALLOWED_MSG_SIZE
import net.corda.schema.configuration.MessagingConfig.Subscription.PROCESSOR_TIMEOUT
import net.corda.utilities.trace
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
// TODO @Component(service = [FlowExecutor::class])
@Component(service = [FlowExecutor::class])
class FlowExecutorMediatorImpl (
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val flowEventProcessorFactory: FlowEventProcessorFactory,
    private val eventMediatorFactory: MultiSourceEventMediatorFactory,
    private val mediatorConsumerFactoryFactory: MediatorConsumerFactoryFactory,
    private val mediatorProducerFactoryFactory: MediatorProducerFactoryFactory,
    private val toMessagingConfig: (Map<String, SmartConfig>) -> SmartConfig
) : FlowExecutor {

    @Activate
    constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = FlowEventProcessorFactory::class)
        flowEventProcessorFactory: FlowEventProcessorFactory,
        @Reference(service = MultiSourceEventMediatorFactory::class)
        eventMediatorFactory: MultiSourceEventMediatorFactory,
        @Reference(service = MediatorConsumerFactoryFactory::class)
        mediatorConsumerFactoryFactory: MediatorConsumerFactoryFactory,
        @Reference(service = MediatorProducerFactoryFactory::class)
        mediatorProducerFactoryFactory: MediatorProducerFactoryFactory,
    ) : this(
        coordinatorFactory,
        flowEventProcessorFactory,
        eventMediatorFactory,
        mediatorConsumerFactoryFactory,
        mediatorProducerFactoryFactory,
        { cfg -> cfg.getConfig(MESSAGING_CONFIG) }
    )

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val CONSUMER_GROUP = "FlowEventConsumer"
        private const val MESSAGE_BUS_PRODUCER = "MessageBusProducer"
    }

    private val coordinator = coordinatorFactory.createCoordinator<FlowExecutor> { event, _ -> eventHandler(event) }
    private var subscriptionRegistrationHandle: RegistrationHandle? = null
    private var multiSourceEventMediator: MultiSourceEventMediator<String, Checkpoint, FlowEvent>? = null

    override fun onConfigChange(config: Map<String, SmartConfig>) {
        try {
            val messagingConfig = toMessagingConfig(config)
            val updatedConfigs = updateConfigsWithFlowConfig(config, messagingConfig)

            // close the lifecycle registration first to prevent down being signaled
            subscriptionRegistrationHandle?.close()
            multiSourceEventMediator?.close()

            multiSourceEventMediator = eventMediatorFactory.create(
                createEventMediatorConfig(
                    messagingConfig,
                    flowEventProcessorFactory.create(updatedConfigs),
                )
            )

            subscriptionRegistrationHandle = coordinator.followStatusChangesByName(
                setOf(multiSourceEventMediator!!.subscriptionName)
            )

            multiSourceEventMediator?.start()
        } catch (ex: Exception) {
            val reason = "Failed to configure the flow executor using '${config}'"
            log.error(reason, ex)
            coordinator.updateStatus(LifecycleStatus.ERROR, reason)
        }
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    private fun updateConfigsWithFlowConfig(
        initialConfigs: Map<String, SmartConfig>,
        messagingConfig: SmartConfig
    ): Map<String, SmartConfig> {
        val flowConfig = initialConfigs.getConfig(FLOW_CONFIG)
        val updatedFlowConfig = flowConfig
            .withValue(PROCESSOR_TIMEOUT, ConfigValueFactory.fromAnyRef(messagingConfig.getLong(PROCESSOR_TIMEOUT)))
            .withValue(MAX_ALLOWED_MSG_SIZE, ConfigValueFactory.fromAnyRef(messagingConfig.getLong(MAX_ALLOWED_MSG_SIZE)))

        return initialConfigs.mapValues {
            if (it.key == FLOW_CONFIG) {
                updatedFlowConfig
            } else {
                it.value
            }
        }
    }

    private fun eventHandler(event: LifecycleEvent) {
        when (event) {
            is StartEvent -> {
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            is StopEvent -> {
                log.trace { "Flow executor is stopping..." }
                subscriptionRegistrationHandle?.close()
                multiSourceEventMediator?.close()
                log.trace { "Flow executor stopped" }
            }
        }
    }

    private fun createEventMediatorConfig(
        messagingConfig: SmartConfig,
        messageProcessor: StateAndEventProcessor<String, Checkpoint, FlowEvent>,
    ) = EventMediatorConfigBuilder<String, Checkpoint, FlowEvent>()
            .name("FlowEventMediator")
            .messagingConfig(messagingConfig)
            .consumerFactories(
                mediatorConsumerFactoryFactory.createMessageBusConsumerFactory(
                    FLOW_EVENT_TOPIC, CONSUMER_GROUP, messagingConfig
                ),
            )
            .producerFactories(
                mediatorProducerFactoryFactory.createMessageBusProducerFactory(
                    MESSAGE_BUS_PRODUCER, messagingConfig
                ),
                //RpcProducerFactory(CRYPTO_RPC_PRODUCER, messagingConfig, cordaRpcBuilder),
            )
            .messageProcessor(messageProcessor)
            .messageRouterFactory(createMessageRouterFactory())
            .build()

    private fun createMessageRouterFactory() = MessageRouterFactory { producerFinder ->
        val messageBusProducer = producerFinder.find(MESSAGE_BUS_PRODUCER)

        MessageRouter { message ->
            when (message.payload) {
                is FlowMapperEvent -> routeTo(messageBusProducer, FLOW_MAPPER_EVENT_TOPIC)
                else -> throw IllegalStateException("No route defined for message $message")
            }
        }
    }
}