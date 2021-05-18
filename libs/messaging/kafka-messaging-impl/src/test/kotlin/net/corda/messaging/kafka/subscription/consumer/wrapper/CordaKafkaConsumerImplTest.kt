package net.corda.messaging.kafka.subscription.consumer.wrapper

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.anyOrNull
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.properties.KafkaProperties
import net.corda.messaging.kafka.subscription.consumer.wrapper.impl.CordaKafkaConsumerImpl
import net.corda.messaging.kafka.subscription.createMockConsumerAndAddRecords
import net.corda.messaging.kafka.subscription.generateMockConsumerRecordsList
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.TimeoutException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.Mockito
import java.nio.ByteBuffer
import java.time.Duration

class CordaKafkaConsumerImplTest {
    private lateinit var cordaKafkaConsumer : CordaKafkaConsumer<String, ByteBuffer>
    private lateinit var kafkaConfig : Config
    private lateinit var subscriptionConfig : SubscriptionConfig
    private val listener : ConsumerRebalanceListener = mock()
    private val eventTopic = "eventTopic1"
    private val numberOfRecords = 10L
    private lateinit var consumer: MockConsumer<String, ByteBuffer>
    private lateinit var partition: TopicPartition
    private val avroSchemaRegistry : AvroSchemaRegistry = mock()
    private val consumerRecord = ConsumerRecord("prefixtopic", 1, 1, "key", ByteBuffer.wrap("value".toByteArray()))

    @BeforeEach
    fun beforeEach() {
        doReturn(ByteBuffer::class.java).whenever(avroSchemaRegistry).getClassType(any())
        doReturn(consumerRecord.value()).whenever(avroSchemaRegistry).deserialize(any(), any(), anyOrNull())
        subscriptionConfig = SubscriptionConfig("groupName1", eventTopic )
        kafkaConfig = ConfigFactory.empty()
            .withValue(KafkaProperties.CONSUMER_POLL_TIMEOUT, ConfigValueFactory.fromAnyRef(1))
            .withValue(KafkaProperties.CONSUMER_SUBSCRIBE_MAX_RETRIES, ConfigValueFactory.fromAnyRef(3))
            .withValue(KafkaProperties.CONSUMER_CLOSE_TIMEOUT, ConfigValueFactory.fromAnyRef(1))
            .withValue(KafkaProperties.KAFKA_TOPIC_PREFIX, ConfigValueFactory.fromAnyRef("prefix"))
            .withValue(KafkaProperties.CONSUMER_COMMIT_OFFSET_MAX_RETRIES, ConfigValueFactory.fromAnyRef(3))

        val (mockConsumer, mockTopicPartition) = createMockConsumerAndAddRecords(eventTopic,  numberOfRecords, OffsetResetStrategy.EARLIEST)
        consumer = mockConsumer
        partition = mockTopicPartition
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            subscriptionConfig,
            consumer,
            listener,
            avroSchemaRegistry
        )
    }

    @Test
    fun testPollInvoked() {
        val consumerRecords = generateMockConsumerRecordsList(2, eventTopic, 1)

        consumer = mock()
        doReturn(consumerRecords).whenever(consumer).poll(Mockito.any(Duration::class.java))
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            subscriptionConfig,
            consumer,
            listener,
            avroSchemaRegistry
        )

        cordaKafkaConsumer.poll()
        verify(consumer, times(1)).poll(Mockito.any(Duration::class.java))
    }

    @Test
    fun testCloseInvoked() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            subscriptionConfig,
            consumer,
            listener,
            avroSchemaRegistry
        )

        cordaKafkaConsumer.close()
        verify(consumer, times(1)).close(Mockito.any(Duration::class.java))
    }

    @Test
    fun testCloseFailNoException() {
        consumer = mock()
        doThrow(KafkaException()).whenever(consumer).close(any())
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            subscriptionConfig,
            consumer,
            listener,
            avroSchemaRegistry
        )
        cordaKafkaConsumer.close()
        verify(consumer, times(1)).close(Mockito.any(Duration::class.java))
    }

    @Test
    fun testGetRecord() {
        val record = cordaKafkaConsumer.getRecord(consumerRecord)
        assertThat(record.topic).isEqualTo("topic")
        assertThat(record.key).isEqualTo(consumerRecord.key())
        assertThat(record.value).isEqualTo(consumerRecord.value())
        verify(avroSchemaRegistry, times(1)).getClassType(any())
        verify(avroSchemaRegistry, times(1)).deserialize(any(), any(), anyOrNull())
    }

    @Test
    fun testGetRecordFailDeserialization() {
        doThrow(CordaRuntimeException("")).whenever(avroSchemaRegistry).deserialize(any(), any(), anyOrNull())
        Assertions.assertThrows(CordaMessageAPIFatalException::class.java) { cordaKafkaConsumer.getRecord(consumerRecord) }
        verify(avroSchemaRegistry, times(1)).getClassType(any())
        verify(avroSchemaRegistry, times(1)).deserialize(any(), any(), anyOrNull())
    }

    @Test
    fun testCommitOffsets() {
        val record = ConsumerRecord<String, ByteBuffer>(eventTopic, 1, 5L, null, ByteBuffer.wrap("value".toByteArray()))
        assertThat(consumer.committed(setOf(partition))).isEmpty()

        cordaKafkaConsumer.commitSyncOffsets(record, "meta data")

        val committedPositionAfterCommit = consumer.committed(setOf(partition))
        assertThat(committedPositionAfterCommit.values.first().offset()).isEqualTo(6)
    }

    @Test
    fun testCommitOffsetsRetries() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            subscriptionConfig,
            consumer,
            listener,
            avroSchemaRegistry
        )

        val record = ConsumerRecord<String, ByteBuffer>(eventTopic, 1, 5L, null, ByteBuffer.wrap("value".toByteArray()))
        doThrow(TimeoutException()).whenever(consumer).commitSync(anyMap())
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.commitSyncOffsets(record, "meta data")
        }
        verify(consumer, times(3)).commitSync(anyMap())
    }

    @Test
    fun testCommitOffsetsFatal() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            subscriptionConfig,
            consumer,
            listener,
            avroSchemaRegistry
        )

        val record = ConsumerRecord<String, ByteBuffer>(eventTopic, 1, 5L, null, ByteBuffer.wrap("value".toByteArray()))
        doThrow(CommitFailedException()).whenever(consumer).commitSync(anyMap())
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.commitSyncOffsets(record, "meta data")
        }
        verify(consumer, times(1)).commitSync(anyMap())
    }


    @Test
    fun testSubscribeToTopic() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            subscriptionConfig,
            consumer,
            listener,
            avroSchemaRegistry
        )
        cordaKafkaConsumer.subscribeToTopic()
        verify(consumer, times(1)).subscribe(Mockito.anyList(), Mockito.any(ConsumerRebalanceListener::class.java))
    }

    @Test
    fun testSubscribeToTopicRetries() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            subscriptionConfig,
            consumer,
            listener,
            avroSchemaRegistry
        )
        doThrow(KafkaException()).whenever(consumer).subscribe(Mockito.anyList(), Mockito.any(ConsumerRebalanceListener::class.java))
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.subscribeToTopic()
        }
        verify(consumer, times(3)).subscribe(Mockito.anyList(), Mockito.any(ConsumerRebalanceListener::class.java))
    }

    @Test
    fun testSubscribeToTopicFatal() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            subscriptionConfig,
            consumer,
            listener,
            avroSchemaRegistry
        )
        doThrow(IllegalArgumentException()).whenever(consumer).subscribe(Mockito.anyList(), Mockito.any(ConsumerRebalanceListener::class.java))
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.subscribeToTopic()
        }
        verify(consumer, times(1)).subscribe(Mockito.anyList(), Mockito.any(ConsumerRebalanceListener::class.java))
    }

    @Test
    fun testResetToLastCommittedPositionsOffsetIsSet() {
        val offsetCommit = 5L
        commitOffsetForConsumer(offsetCommit)

        //Reset fetch position to the last committed record
        //Does not matter what reset strategy is passed here
        cordaKafkaConsumer.resetToLastCommittedPositions(OffsetResetStrategy.NONE)

        val positionAfterReset = consumer.position(partition)
        assertThat(positionAfterReset).isEqualTo(offsetCommit)
    }

    @Test
    fun testResetToLastCommittedPositionsStrategyLatest() {
        //Reset fetch position to the last committed record
        cordaKafkaConsumer.resetToLastCommittedPositions(OffsetResetStrategy.LATEST)

        val positionAfterReset = consumer.position(partition)
        assertThat(positionAfterReset).isEqualTo(numberOfRecords)
    }

    @Test
    fun testResetToLastCommittedPositionsStrategyEarliest() {
        //Reset fetch position to the last committed record
        cordaKafkaConsumer.resetToLastCommittedPositions(OffsetResetStrategy.EARLIEST)

        val positionAfterReset = consumer.position(partition)
        assertThat(positionAfterReset).isEqualTo(0L)
    }

    private fun commitOffsetForConsumer(offsetCommit: Long) {
        val positionBeforePoll = consumer.position(partition)
        assertThat(positionBeforePoll).isEqualTo(0)
        consumer.poll(Duration.ZERO)

        //get current position after poll for this partition/topic
        val positionAfterPoll = consumer.position(partition)
        assertThat(positionAfterPoll).isEqualTo(numberOfRecords)

        //Commit offset for half the records = offset of 5.
        val currentOffsets = mutableMapOf<TopicPartition, OffsetAndMetadata>()
        currentOffsets[partition] = OffsetAndMetadata(offsetCommit, "metaData")
        consumer.commitSync(currentOffsets)
        val positionAfterCommit = consumer.position(partition)
        assertThat(positionAfterCommit).isEqualTo(numberOfRecords)
    }
}