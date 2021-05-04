package net.corda.messaging.kafka.subscription.consumer.wrapper.impl

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.properties.KafkaProperties
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_POLL_TIMEOUT
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.TopicPartition
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException
import java.time.Duration

/**
 * Wrapper for a Kafka Consumer.
 */
class CordaKafkaConsumerImpl<K, V>(
    kafkaConfig: Config,
    subscriptionConfig: SubscriptionConfig,
    override val consumer: Consumer<K, V>,
    private val listener: ConsumerRebalanceListener
) : CordaKafkaConsumer<K, V> {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private val consumerPollTimeout = Duration.ofMillis(kafkaConfig.getLong(CONSUMER_POLL_TIMEOUT))
    private val consumerCloseTimeout = Duration.ofMillis(kafkaConfig.getLong(KafkaProperties.CONSUMER_CLOSE_TIMEOUT))
    private val consumerSubscribeMaxRetries = kafkaConfig.getLong(KafkaProperties.CONSUMER_SUBSCRIBE_MAX_RETRIES)
    private val groupName = subscriptionConfig.groupName
    private val topicPrefix = kafkaConfig.getString(KafkaProperties.KAFKA_TOPIC_PREFIX)
    private val topic = subscriptionConfig.eventTopic

    @Suppress("TooGenericExceptionCaught")
    override fun close() {
        try {
            consumer.close(consumerCloseTimeout)
        } catch (ex: Exception) {
            log.error("CordaKafkaConsumer failed to close consumer from group $groupName for topic $topic.", ex)
        }
    }

    override fun poll(): List<ConsumerRecord<K, V>> {
        val consumerRecords = consumer.poll(consumerPollTimeout)
        return consumerRecords.sortedBy { it.timestamp() }
    }

    override fun resetToLastCommittedPositions(offsetStrategy: OffsetResetStrategy) {
        val committed = consumer.committed(consumer.assignment())
        for (assignment in consumer.assignment()) {
            val offsetAndMetadata = committed[assignment]
            when {
                offsetAndMetadata != null -> {
                    consumer.seek(assignment, offsetAndMetadata.offset())
                }
                offsetStrategy == OffsetResetStrategy.LATEST -> {
                    consumer.seekToEnd(setOf(assignment))
                }
                else -> {
                    consumer.seekToBeginning(setOf(assignment))
                }
            }
        }
    }

    override fun getRecord(consumerRecord: ConsumerRecord<K, V>) : Record<K, V> {
        val topic = consumerRecord.topic().substringAfter(topicPrefix)
        return Record(topic, consumerRecord.key(), consumerRecord.value())
    }

    override fun commitSyncOffsets(event: ConsumerRecord<K, V>, metaData: String?) {
        val offsets = mutableMapOf<TopicPartition, OffsetAndMetadata>()
        val topicPartition = TopicPartition(event.topic(), event.partition())
        offsets[topicPartition] = OffsetAndMetadata(event.offset() + 1, metaData)
        consumer.commitSync(offsets);
    }

    override fun subscribeToTopic() {
        var attempts = 0
        var attemptSubscription = true
        while (attemptSubscription) {
            try {
                consumer.subscribe(listOf(topicPrefix + topic), listener)
                attemptSubscription = false
            } catch (ex: IllegalStateException) {
                val message = "CordaKafkaConsumer failed to subscribe a consumer from group $groupName to topic $topic. " +
                        "Consumer is already subscribed to this topic. Closing subscription."
                log.error(message, ex)
                throw CordaMessageAPIFatalException(message, ex)
            } catch (ex: IllegalArgumentException) {
                val message = "CordaKafkaConsumer failed to subscribe a consumer from group $groupName to topic $topic. " +
                        "Illegal args provided. Closing subscription."
                log.error(message, ex)
                throw CordaMessageAPIFatalException(message, ex)
            } catch (ex: KafkaException) {
                attempts++
                if (attempts < consumerSubscribeMaxRetries) {
                    log.error("CordaKafkaConsumer failed to subscribe a consumer from group $groupName to topic $topic. " +
                                "retrying.", ex)
                } else {
                    val message =
                        "CordaKafkaConsumer failed to subscribe a consumer from group $groupName to topic $topic. " +
                                "Max retries exceeded. Closing subscription."
                    log.error(message, ex)
                    throw CordaMessageAPIFatalException(message, ex)
                }
            }
        }
    }
}
