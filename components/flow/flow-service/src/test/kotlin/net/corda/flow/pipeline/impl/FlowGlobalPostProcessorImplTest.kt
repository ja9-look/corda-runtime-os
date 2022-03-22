package net.corda.flow.pipeline.impl

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.pipeline.FlowEventContext
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.session.manager.SessionManager
import net.corda.test.flow.util.buildSessionState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

class FlowGlobalPostProcessorImplTest {

    private companion object {
        const val FLOW_ID = "flow id"
        const val SESSION_ID = "session id"
        const val ANOTHER_SESSION_ID = "another session id"
        const val DATA = "data"
        const val MORE_DATA = "more data"
        val HOLDING_IDENTITY = HoldingIdentity("x500 name", "group id")
        val FLOW_KEY = FlowKey(FLOW_ID, HOLDING_IDENTITY)
    }

    private val sessionManager = mock<SessionManager>()

    private val flowGlobalPostProcessor = FlowGlobalPostProcessorImpl(sessionManager)

    @Test
    fun `Adds output records containing session events to send to peers`() {
        val sessionState = SessionState().apply {
            sessionId = SESSION_ID
        }
        val anotherSessionState = SessionState().apply {
            sessionId = ANOTHER_SESSION_ID
        }
        val sessionEvent = SessionEvent().apply {
            sessionId = SESSION_ID
            payload = SessionData(ByteBuffer.wrap(DATA.toByteArray()))
            sequenceNum = 1
        }
        val anotherSessionEvent = SessionEvent().apply {
            sessionId = ANOTHER_SESSION_ID
            payload = SessionData(ByteBuffer.wrap(MORE_DATA.toByteArray()))
            sequenceNum = 1
        }

        whenever(sessionManager.getMessagesToSend(eq(sessionState), any(), any()))
            .thenReturn(sessionState to listOf(sessionEvent))
        whenever(sessionManager.getMessagesToSend(eq(anotherSessionState), any(), any()))
            .thenReturn(anotherSessionState to listOf(anotherSessionEvent))

        val inputContext: FlowEventContext<Any> = FlowEventContext(
            checkpoint = Checkpoint().apply {
                sessions = listOf(sessionState, anotherSessionState)
            },
            inputEvent = FlowEvent(FLOW_KEY, Unit),
            inputEventPayload = Unit,
            outputRecords = emptyList()
        )

        val outputContext = flowGlobalPostProcessor.postProcess(inputContext)

        val expected = listOf(
            Record(Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC, SESSION_ID, FlowMapperEvent(sessionEvent)),
            Record(Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC, ANOTHER_SESSION_ID, FlowMapperEvent(anotherSessionEvent))
        )

        assertEquals(expected, outputContext.outputRecords)
    }

    @Test
    fun `Updates session states`() {
        val sessionState = buildSessionState(
            SessionStateType.CONFIRMED, 0, mutableListOf(), 0, mutableListOf(), sessionId = SESSION_ID
        )
        val anotherSessionState = buildSessionState(
            SessionStateType.CONFIRMED, 0, mutableListOf(), 0, mutableListOf(), sessionId = ANOTHER_SESSION_ID
        )
        val updatedSessionState = buildSessionState(
            SessionStateType.CONFIRMED, 1, mutableListOf(), 0, mutableListOf(), sessionId = SESSION_ID
        )
        val updatedAnotherSessionState = buildSessionState(
            SessionStateType.CONFIRMED, 1, mutableListOf(), 0, mutableListOf(), sessionId = ANOTHER_SESSION_ID
        )
        val sessionEvent = SessionEvent().apply {
            sessionId = SESSION_ID
            payload = SessionData(ByteBuffer.wrap(DATA.toByteArray()))
            sequenceNum = 1
        }
        val anotherSessionEvent = SessionEvent().apply {
            sessionId = ANOTHER_SESSION_ID
            payload = SessionData(ByteBuffer.wrap(MORE_DATA.toByteArray()))
            sequenceNum = 1
        }
        val checkpoint = Checkpoint().apply {
            sessions = listOf(sessionState, anotherSessionState)
        }
        val checkpointCopy = Checkpoint().apply {
            sessions = checkpoint.sessions
        }

        whenever(sessionManager.getMessagesToSend(eq(sessionState), any(), any()))
            .thenReturn(updatedSessionState to listOf(sessionEvent))
        whenever(sessionManager.getMessagesToSend(eq(anotherSessionState), any(), any()))
            .thenReturn(updatedAnotherSessionState to listOf(anotherSessionEvent))

        val inputContext: FlowEventContext<Any> = FlowEventContext(
            checkpoint = checkpointCopy,
            inputEvent = FlowEvent(FLOW_KEY, Unit),
            inputEventPayload = Unit,
            outputRecords = emptyList()
        )

        val outputContext = flowGlobalPostProcessor.postProcess(inputContext)

        assertNotEquals(checkpoint.sessions, outputContext.checkpoint?.sessions)
    }

    @Test
    fun `Does nothing when there is no checkpoint`() {
        val inputContext: FlowEventContext<Any> = FlowEventContext(
            checkpoint = null,
            inputEvent = FlowEvent(FLOW_KEY, Unit),
            inputEventPayload = Unit,
            outputRecords = emptyList()
        )

        val outputContext = flowGlobalPostProcessor.postProcess(inputContext)

        assertEquals(inputContext, outputContext)
    }
}