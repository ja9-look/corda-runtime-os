package net.corda.messaging.mediator

import kotlinx.coroutines.runBlocking
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration

class MessageBusConsumerTest {
    companion object {
        private const val TOPIC = "topic"
    }

    private lateinit var cordaConsumer: CordaConsumer<String, String>
    private lateinit var mediatorConsumer: MessageBusConsumer<String, String>

    private val defaultHeaders: List<Pair<String, String>> = emptyList()
    private val messageProps: MutableMap<String, Any> = mutableMapOf(
        "topic" to "topic",
        "key" to "key",
        "headers" to defaultHeaders
    )
    private val message: MediatorMessage<Any> = MediatorMessage("value", messageProps)


    @BeforeEach
    fun setup() {
        cordaConsumer = mock()
        mediatorConsumer = MessageBusConsumer(TOPIC, cordaConsumer)
    }

    @Test
    fun testSubscribe() {
        mediatorConsumer.subscribe()

        verify(cordaConsumer).subscribe(eq(TOPIC))
    }

    @Test
    fun testPoll() {
        val timeout = Duration.ofMillis(123)
        mediatorConsumer.poll(timeout)

        verify(cordaConsumer).poll(eq(timeout))
    }

    @Test
    fun testCommitAsyncOffsets() {
        mediatorConsumer.commitAsyncOffsets()

        verify(cordaConsumer).commitAsyncOffsets(any())
    }

    @Test
    fun testCommitAsyncOffsetsWithError() {
        doReturn(CordaRuntimeException("")).whenever(cordaConsumer).commitAsyncOffsets(any())

        assertThrows<CordaRuntimeException> {
            runBlocking {
                mediatorConsumer.commitAsyncOffsets().await()
            }
        }
    }

    @Test
    fun testClose() {
        mediatorConsumer.close()
        verify(cordaConsumer, times(1)).close()
    }
}