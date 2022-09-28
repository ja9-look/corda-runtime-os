@file:JvmName("FlowSandboxServiceUtils")

package net.corda.flow.pipeline.sandbox.impl

import net.corda.common.json.serializers.SerializationCustomizer
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.pipeline.sandbox.exception.VirtualNodeStateException
import net.corda.flow.pipeline.sandbox.factory.SandboxDependencyInjectorFactory
import net.corda.flow.pipeline.sessions.FlowProtocolStoreFactory
import net.corda.internal.serialization.AMQP_P2P_CONTEXT
import net.corda.internal.serialization.SerializationServiceImpl
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.internal.serialization.registerCustomSerializers
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.CORDA_SANDBOX_FILTER
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.SANDBOX_SINGLETONS
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.sandboxgroupcontext.putObjectByKey
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.serialization.InternalCustomSerializer
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import net.corda.serialization.checkpoint.factory.CheckpointSerializerBuilderFactory
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.marshalling.json.JsonDeserializer
import net.corda.v5.application.marshalling.json.JsonSerializer
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.loggerFor
import net.corda.v5.serialization.SerializationCustomSerializer
import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.VirtualNodeState
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.framework.Constants.SCOPE_PROTOTYPE
import org.osgi.framework.Constants.SERVICE_SCOPE
import org.osgi.service.component.ComponentContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality.MULTIPLE
import org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC

@Suppress("LongParameterList")
@Component(
    service = [FlowSandboxService::class],
    reference = [
        Reference(
            name = FlowSandboxServiceImpl.INTERNAL_CUSTOM_SERIALIZERS,
            service = InternalCustomSerializer::class,
            cardinality = MULTIPLE,
            policy = DYNAMIC
        ),
        Reference(
            name = FlowSandboxServiceImpl.CHECKPOINT_INTERNAL_CUSTOM_SERIALIZERS,
            service = CheckpointInternalCustomSerializer::class,
            cardinality = MULTIPLE,
            policy = DYNAMIC
        ),
        Reference(
            name = FlowSandboxServiceImpl.INTERNAL_CUSTOM_JSON_SERIALIZERS,
            service = JsonSerializer::class,
            cardinality = MULTIPLE,
            policy = DYNAMIC
        ),
        Reference(
            name = FlowSandboxServiceImpl.INTERNAL_CUSTOM_JSON_DESERIALIZERS,
            service = JsonDeserializer::class,
            cardinality = MULTIPLE,
            policy = DYNAMIC
        )
    ]
)
class FlowSandboxServiceImpl @Activate constructor(
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReadService: CpiInfoReadService,
    @Reference(service = SandboxGroupContextComponent::class)
    private val sandboxGroupContextComponent: SandboxGroupContextComponent,
    @Reference(service = SandboxDependencyInjectorFactory::class)
    private val dependencyInjectionFactory: SandboxDependencyInjectorFactory,
    @Reference(service = CheckpointSerializerBuilderFactory::class)
    private val checkpointSerializerBuilderFactory: CheckpointSerializerBuilderFactory,
    @Reference(service = FlowProtocolStoreFactory::class)
    private val flowProtocolStoreFactory: FlowProtocolStoreFactory,
    private val componentContext: ComponentContext
) : FlowSandboxService {

    companion object {
        const val INTERNAL_CUSTOM_SERIALIZERS = "internalCustomSerializers"
        const val CHECKPOINT_INTERNAL_CUSTOM_SERIALIZERS = "checkpointInternalCustomSerializers"
        private const val NON_PROTOTYPE_SERVICES = "(!($SERVICE_SCOPE=$SCOPE_PROTOTYPE))"
        private val CORDAPP_CUSTOM_SERIALIZER = SerializationCustomSerializer::class.java

        const val INTERNAL_CUSTOM_JSON_DESERIALIZERS = "internalCustomJsonDeserializers"
        const val INTERNAL_CUSTOM_JSON_SERIALIZERS = "internalCustomJsonSerializers"

        private fun <T> ComponentContext.fetchServices(refName: String): List<T> {
            @Suppress("unchecked_cast")
            return (locateServices(refName) as? Array<T>)?.toList() ?: emptyList()
        }
    }

    private val log = loggerFor<FlowSandboxServiceImpl>()

    private val internalCustomSerializers
        get() = componentContext.fetchServices<InternalCustomSerializer<out Any>>(INTERNAL_CUSTOM_SERIALIZERS)

    private val checkpointInternalCustomSerializers
        get() = componentContext.fetchServices<CheckpointInternalCustomSerializer<out Any>>(
            CHECKPOINT_INTERNAL_CUSTOM_SERIALIZERS
        )

    override fun getWithVNodeMaintenanceValidation(holdingIdentity: HoldingIdentity): FlowSandboxGroupContext {
        val vNodeInfo = getVnodeFromInfoService(holdingIdentity)

        // Inactive virtual nodes are treated as active.
        if(vNodeInfo.state == VirtualNodeState.IN_MAINTENANCE) {
            throw VirtualNodeStateException(holdingIdentity.shortHash.value, VirtualNodeState.IN_MAINTENANCE)
        }
        if(vNodeInfo.state == VirtualNodeState.DRAINING) {
            throw VirtualNodeStateException(holdingIdentity.shortHash.value, VirtualNodeState.DRAINING)
        }
        return getContextFromCpi(vNodeInfo, holdingIdentity)
    }

    override fun get(holdingIdentity: HoldingIdentity): FlowSandboxGroupContext {
        val vNodeInfo = getVnodeFromInfoService(holdingIdentity)
        return getContextFromCpi(vNodeInfo, holdingIdentity)
    }

    private fun getVnodeFromInfoService(holdingIdentity: HoldingIdentity): VirtualNodeInfo {
        return checkNotNull(virtualNodeInfoReadService.get(holdingIdentity)) {
            "Failed to find the virtual node info for holder '${holdingIdentity}'"
        }
    }

    private fun getContextFromCpi(vNodeInfo: VirtualNodeInfo, holdingIdentity: HoldingIdentity): FlowSandboxGroupContext {
        val cpiMetadata = cpiInfoReadService.get(vNodeInfo.cpiIdentifier)
        checkNotNull(cpiMetadata) { "Failed to find the CPI meta data for '${vNodeInfo.cpiIdentifier}}'" }
        check(cpiMetadata.cpksMetadata.isNotEmpty()) { "No CPKs defined for CPI Meta data id='${cpiMetadata.cpiId}'" }

        val vNodeContext = VirtualNodeContext(
            holdingIdentity,
            cpiMetadata.cpksMetadata.mapTo(LinkedHashSet()) { it.fileChecksum },
            SandboxGroupType.FLOW,
            SingletonSerializeAsToken::class.java,
            null
        )

        if (!sandboxGroupContextComponent.hasCpks(vNodeContext.cpkFileChecksums)) {
            throw IllegalStateException("The sandbox can't find one or more of the CPKs for CPI '${cpiMetadata.cpiId}'")
        }

        val sandboxGroupContext = sandboxGroupContextComponent.getOrCreate(vNodeContext) { _, sandboxGroupContext ->
            initialiseSandbox(dependencyInjectionFactory, sandboxGroupContext, cpiMetadata)
        }

        return FlowSandboxGroupContextImpl.fromContext(sandboxGroupContext)
    }

    private fun initialiseSandbox(
        dependencyInjectionFactory: SandboxDependencyInjectorFactory,
        sandboxGroupContext: MutableSandboxGroupContext,
        cpiMetadata: CpiMetadata
    ): AutoCloseable {
        val sandboxGroup = sandboxGroupContext.sandboxGroup
        val customCrypto = sandboxGroupContextComponent.registerCustomCryptography(sandboxGroupContext)

        val injectorService = dependencyInjectionFactory.create(sandboxGroupContext)
        sandboxGroupContext.putObjectByKey(FlowSandboxGroupContextImpl.DEPENDENCY_INJECTOR, injectorService)

        // Identify singleton services outside the sandbox that may need checkpointing.
        // These services should not overlap with the injectable services, which should
        // all have PROTOTYPE scope outside the sandbox.
        val cleanupCordaSingletons = mutableListOf<AutoCloseable>()
        val nonInjectableSingletons = getNonInjectableSingletons(cleanupCordaSingletons)

        // These are the singleton services identified when creating the sandbox.
        // This set includes both injectable and non-injectable singletons.
        val sandboxSingletons = sandboxGroupContext.getObjectByKey<Set<Any>>(SANDBOX_SINGLETONS)
            ?.filterIsInstanceTo(LinkedHashSet<SingletonSerializeAsToken>())
            ?: emptySet()

        // Create and configure the checkpoint serializer
        val checkpointSerializer = checkpointSerializerBuilderFactory.createCheckpointSerializerBuilder(sandboxGroup).let { builder ->
            builder.addSingletonSerializableInstances(injectorService.getRegisteredSingletons())
            builder.addSingletonSerializableInstances(nonInjectableSingletons)
            builder.addSingletonSerializableInstances(sandboxSingletons)
            builder.addSingletonSerializableInstances(setOf(sandboxGroup))
            for (serializer in checkpointInternalCustomSerializers) {
                log.info("Registering internal checkpoint serializer {}", serializer::class.java.name)
                builder.addSerializer(serializer.type, serializer)
            }
            builder.build()
        }

        sandboxGroupContext.putObjectByKey(FlowSandboxGroupContextImpl.CHECKPOINT_SERIALIZER, checkpointSerializer)

        val customSerializers = sandboxGroupContextComponent.registerMetadataServices(
            sandboxGroupContext,
            serviceNames = { metadata -> metadata.cordappManifest.serializers },
            serviceMarkerType = CORDAPP_CUSTOM_SERIALIZER
        )
        sandboxGroupContext.putAMQPSerializationEnvironment()

        sandboxGroupContext.putObjectByKey(
            FlowSandboxGroupContextImpl.FLOW_PROTOCOL_STORE,
            flowProtocolStoreFactory.create(sandboxGroup, cpiMetadata)
        )

        sandboxGroupContext.registerCustomJsonSerialization()

        // Instruct all CustomMetadataConsumers to accept their metadata.
        sandboxGroupContextComponent.acceptCustomMetadata(sandboxGroupContext)

        return AutoCloseable {
            cleanupCordaSingletons.forEach(AutoCloseable::close)
            customSerializers.close()
            injectorService.close()
            customCrypto.close()
        }
    }

    private fun getNonInjectableSingletons(cleanups: MutableList<AutoCloseable>): Set<SingletonSerializeAsToken> {
        val bundleContext = componentContext.bundleContext
        // An OSGi singleton component can still register bundle-scoped services, so
        // select the non-prototype ones here. They should all be internal to Corda.
        return bundleContext.getServiceReferences(SingletonSerializeAsToken::class.java, NON_PROTOTYPE_SERVICES)
            .mapNotNullTo(HashSet()) { ref ->
                bundleContext.getService(ref)?.also {
                    cleanups.add(AutoCloseable { bundleContext.ungetService(ref) })
                }
            }
    }

    private fun MutableSandboxGroupContext.putAMQPSerializationEnvironment() {
        val factory = SerializerFactoryBuilder.build(sandboxGroup)

        registerCustomSerializers(factory)

        for (customSerializer in internalCustomSerializers) {
            log.info("Registering internal serializer {}", customSerializer::class.java.name)
            factory.register(customSerializer, factory)
        }
        // Build CorDapp serializers
        // Current implementation has unique serializers per CPI
        getObjectByKey<Iterable<SerializationCustomSerializer<*,*>>>(CORDAPP_CUSTOM_SERIALIZER.name)?.forEach { customSerializer ->
            // Register CorDapp serializers
            log.info("Registering CorDapp serializer {}", customSerializer::class.java.name)
            factory.registerExternal(customSerializer, factory)
        }

        val serializationOutput = SerializationOutput(factory)
        val deserializationInput = DeserializationInput(factory)

        val p2pSerializationService = SerializationServiceImpl(
            serializationOutput,
            deserializationInput,
            AMQP_P2P_CONTEXT.withSandboxGroup(sandboxGroup)
        )

        putObjectByKey(FlowSandboxGroupContextImpl.AMQP_P2P_SERIALIZATION_SERVICE, p2pSerializationService)
    }

    private fun MutableSandboxGroupContext.registerCustomJsonSerialization() {
        val jsonMarshallingService = sandboxGroup.getOsgiServiceByClass<JsonMarshallingService>()
        if (jsonMarshallingService == null) {
            log.debug { "JsonMarshallingService not required by this sandbox, skipping custom serialization scanning" }
            return
        }

        if (jsonMarshallingService !is SerializationCustomizer) {
            log.warn("registerCustomJsonSerialization failed: JsonMarshallingService does not support custom serialization")
            return
        }

        val sandboxJsonSerializationManager = SandboxJsonSerializationManager(this, jsonMarshallingService)

        // Add platform serialization support first, so that it takes precedence over user custom serialization
        componentContext.fetchServices<JsonSerializer<*>>(INTERNAL_CUSTOM_JSON_SERIALIZERS).forEach { jsonSerializer ->
            sandboxJsonSerializationManager.setSerializer(jsonSerializer) { errorMessage ->
                throw CordaRuntimeException(
                    "registerCustomJsonSerialization failed: ${jsonSerializer::class.java.canonicalName} $errorMessage"
                )
            }
        }
        componentContext.fetchServices<JsonDeserializer<*>>(INTERNAL_CUSTOM_JSON_DESERIALIZERS)
            .forEach { jsonDeserializer ->
                sandboxJsonSerializationManager.setDeserializer(jsonDeserializer) { errorMessage ->
                    throw CordaRuntimeException(
                        "registerCustomJsonSerialization failed: ${jsonDeserializer::class.java.canonicalName} $errorMessage"
                    )
                }
            }

        // User custom serialization support, no exceptions thrown so user code doesn't kill the flow service
        sandboxGroupContextComponent.registerMetadataServices(
            this,
            serviceNames = { metadata -> metadata.cordappManifest.jsonSerializerClasses },
            serviceMarkerType = JsonSerializer::class.java
        )
        sandboxGroupContextComponent.registerMetadataServices(
            this,
            serviceNames = { metadata -> metadata.cordappManifest.jsonDeserializerClasses },
            serviceMarkerType = JsonDeserializer::class.java
        )

        getObjectByKey<Iterable<JsonSerializer<*>>>(JsonSerializer::class.java.name)?.forEach { jsonSerializer ->
            sandboxJsonSerializationManager.setSerializer(jsonSerializer) { errorMessage ->
                log.warn(
                    "registerCustomJsonSerialization failed: ${jsonSerializer::class.java.canonicalName} $errorMessage"
                )
            }
        }
        getObjectByKey<Iterable<JsonDeserializer<*>>>(JsonDeserializer::class.java.name)?.forEach { jsonDeserializer ->
            sandboxJsonSerializationManager.setDeserializer(jsonDeserializer) { errorMessage ->
                log.warn(
                    "registerCustomJsonSerialization failed: ${jsonDeserializer::class.java.canonicalName} $errorMessage"
                )
            }
        }
    }

    private inline fun <reified T> SandboxGroup.getOsgiServiceByClass() =
        this.metadata.keys.firstOrNull()?.bundleContext?.let { bundleContext ->
            bundleContext.getServiceReferences(T::class.java, CORDA_SANDBOX_FILTER)?.maxOrNull()
                ?.let { serviceRef ->
                    bundleContext.getService(serviceRef)
                }
        }
}
