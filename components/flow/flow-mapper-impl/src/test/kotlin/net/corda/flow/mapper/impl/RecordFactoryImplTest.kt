package net.corda.flow.mapper.impl

import com.typesafe.config.ConfigValueFactory
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.schema.Schemas
import net.corda.schema.configuration.FlowConfig
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant

internal class RecordFactoryImplTest {

    companion object {
        private const val SESSION_ID = "session-id"
        private const val FLOW_ID = "flow-id"
        private val alice = HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "1")
        private val bob = HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "1")
    }

    private lateinit var recordFactoryImplSameCluster: RecordFactoryImpl
    private lateinit var recordFactoryImplDifferentCluster: RecordFactoryImpl

    private val locallyHostedIdentitiesServiceSameCluster = mock<LocallyHostedIdentitiesService>()
    private val cordaAvroSerializer = mock<CordaAvroSerializer<SessionEvent>>()
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory>().also {
        whenever(it.createAvroSerializer<SessionEvent>(anyOrNull())).thenReturn(
            cordaAvroSerializer
        )
    }

    @BeforeEach
    fun setup() {
        val byteArray = "SessionEventSerialized".toByteArray()

        whenever(cordaAvroSerializer.serialize(any<SessionEvent>())).thenReturn(byteArray)
        whenever(locallyHostedIdentitiesServiceSameCluster.getIdentityInfo(any())).thenReturn(mock())

        val locallyHostedIdentitiesServiceDifferentCluster: LocallyHostedIdentitiesService = mock()
        whenever(locallyHostedIdentitiesServiceDifferentCluster.getIdentityInfo(any())).thenReturn(null)

        recordFactoryImplSameCluster = RecordFactoryImpl(cordaAvroSerializationFactory, locallyHostedIdentitiesServiceSameCluster)
        recordFactoryImplDifferentCluster = RecordFactoryImpl(cordaAvroSerializationFactory, locallyHostedIdentitiesServiceDifferentCluster)
    }
    private val flowConfig = SmartConfigImpl.empty().withValue(FlowConfig.SESSION_P2P_TTL, ConfigValueFactory.fromAnyRef(10000))

    @Test
    fun `forwardError returns record for same cluster`() {
        val bobId = HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "1")
        val sessionEvent = SessionEvent(
            MessageDirection.OUTBOUND,
            Instant.now(), SESSION_ID, null,
            alice,
            bob,
            SessionError(
                ExceptionEnvelope(
                    "FlowMapper-SessionError",
                    "Received SessionError with sessionId 1"
                )
            ),
            null
        )

        val record = recordFactoryImplSameCluster.forwardError(
            sessionEvent,
            ExceptionEnvelope(
            "FlowMapper-SessionError",
            "Received SessionError with sessionId 1"),
            Instant.now(),
            flowConfig,
            "my-flow-id"
        )
        assertThat(record).isNotNull
        assertThat(record.topic).isEqualTo(Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC)
        assertThat(record.value!!::class).isEqualTo(FlowMapperEvent::class)
        verify(locallyHostedIdentitiesServiceSameCluster).getIdentityInfo(bobId.toCorda())
        val sessionOutput = (record.value as FlowMapperEvent).payload as SessionEvent
        assertThat(sessionOutput.messageDirection).isEqualTo(MessageDirection.INBOUND)
        assertThat(sessionOutput.sessionId).isEqualTo("$SESSION_ID-INITIATED")
    }


    @Test
    fun `forwardError returns record for different cluster`() {
        val sessionEvent = SessionEvent(
            MessageDirection.OUTBOUND,
            Instant.now(), SESSION_ID, null,
            alice,
            bob,
            SessionError(
                ExceptionEnvelope(
                    "FlowMapper-SessionError",
                    "Received SessionError with sessionId 1"
                )
            ),
            null
        )

        val record = recordFactoryImplDifferentCluster.forwardError(
            sessionEvent,
            ExceptionEnvelope(
                "FlowMapper-SessionError",
                "Received SessionError with sessionId 1"),
            Instant.now(),
            flowConfig,
            FLOW_ID
        )
        assertThat(record).isNotNull
        assertThat(record.topic).isEqualTo(Schemas.P2P.P2P_OUT_TOPIC)
        assertThat(record.value!!::class).isEqualTo(AppMessage::class)
        val sessionOutput = ((record.value as AppMessage).message as AuthenticatedMessage).payload
        assertThat(sessionOutput).isEqualTo(ByteBuffer.wrap("SessionEventSerialized".toByteArray()))
        verify(cordaAvroSerializer).serialize(sessionEvent)
    }

    @Test
    fun `forwardError returns a record for the flow engine for inbound session events`() {
        val sessionEvent = SessionEvent(
            MessageDirection.INBOUND,
            Instant.now(),
            SESSION_ID,
            1,
            alice,
            bob,
            SessionData(ByteBuffer.wrap("data".toByteArray()), null),
            null
        )
        val record = recordFactoryImplDifferentCluster.forwardError(
            sessionEvent,
            ExceptionEnvelope(
                "FlowMapper-SessionError",
                "Received SessionError with sessionId 1"),
            Instant.now(),
            flowConfig,
            FLOW_ID
        )
        assertThat(record.topic).isEqualTo(Schemas.Flow.FLOW_EVENT_TOPIC)
        assertThat(record.key).isEqualTo(FLOW_ID)
        assertThat(record.value!!::class.java).isEqualTo(FlowEvent::class.java)
        val sessionOutput = (record.value as FlowEvent).payload as SessionEvent
        assertThat(sessionOutput.sessionId).isEqualTo(SESSION_ID)
        assertThat(sessionOutput.messageDirection).isEqualTo(MessageDirection.INBOUND)
    }

    @Test
    fun `forwardEvent returns record for same cluster`() {
        val sessionEvent = SessionEvent(
            MessageDirection.OUTBOUND,
            Instant.now(), SESSION_ID, 1,
            alice,
            bob,
            SessionData(),
            null
        )

        val record = recordFactoryImplSameCluster.forwardEvent(
            sessionEvent,
            Instant.now(),
            flowConfig,
            FLOW_ID
        )
        assertThat(record).isNotNull
        assertThat(record.topic).isEqualTo(Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC)
        assertThat(record.value!!::class).isEqualTo(FlowMapperEvent::class)
        val sessionOutput = (record.value as FlowMapperEvent).payload as SessionEvent
        assertThat(sessionOutput.sessionId).isEqualTo("$SESSION_ID-INITIATED")
        assertThat(sessionOutput.messageDirection).isEqualTo(MessageDirection.INBOUND)
    }

    @Test
    fun `forwardEvent returns record for different cluster`() {
        val sessionEvent = SessionEvent(
            MessageDirection.OUTBOUND,
            Instant.now(),
            SESSION_ID,
            1,
            HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "1"),
            HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "1"),
            SessionData(
                ByteBuffer.wrap("data".toByteArray()), null)
            ,
            null
        )
        val record = recordFactoryImplDifferentCluster.forwardEvent(
            sessionEvent,
            Instant.now(),
            flowConfig,
            FLOW_ID
        )
        assertThat(record).isNotNull
        assertThat(record.topic).isEqualTo(Schemas.P2P.P2P_OUT_TOPIC)
        assertThat(record.key).isEqualTo(SESSION_ID)
        assertThat(record.value!!::class).isEqualTo(AppMessage::class)
        val sessionOutput = ((record.value as AppMessage).message as AuthenticatedMessage).payload
        assertThat(sessionOutput).isEqualTo(ByteBuffer.wrap("SessionEventSerialized".toByteArray()))
        verify(cordaAvroSerializer).serialize(sessionEvent)
    }

    @Test
    fun `forwardEvent returns a record for the flow engine for inbound session events`() {
        val sessionEvent = SessionEvent(
            MessageDirection.INBOUND,
            Instant.now(),
            SESSION_ID,
            1,
            alice,
            bob,
            SessionData(ByteBuffer.wrap("data".toByteArray()), null),
            null
        )
        val record = recordFactoryImplDifferentCluster.forwardEvent(
            sessionEvent,
            Instant.now(),
            flowConfig,
            FLOW_ID
        )
        assertThat(record.topic).isEqualTo(Schemas.Flow.FLOW_EVENT_TOPIC)
        assertThat(record.key).isEqualTo(FLOW_ID)
        assertThat(record.value!!::class.java).isEqualTo(FlowEvent::class.java)
        val sessionOutput = (record.value as FlowEvent).payload as SessionEvent
        assertThat(sessionOutput.sessionId).isEqualTo(SESSION_ID)
        assertThat(sessionOutput.messageDirection).isEqualTo(MessageDirection.INBOUND)
    }
}