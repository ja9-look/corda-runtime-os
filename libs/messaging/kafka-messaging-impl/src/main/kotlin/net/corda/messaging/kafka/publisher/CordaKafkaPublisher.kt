package net.corda.messaging.kafka.publisher

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.KAFKA_TOPIC_PREFIX
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PRODUCER_CLOSE_TIMEOUT
import net.corda.v5.base.concurrent.CordaFuture
import net.corda.v5.base.internal.concurrent.OpenFuture
import net.corda.v5.base.internal.concurrent.openFuture
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.AuthorizationException
import org.apache.kafka.common.errors.InterruptException
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.errors.TimeoutException
import org.apache.kafka.common.errors.ProducerFencedException
import org.apache.kafka.common.errors.InvalidProducerEpochException
import org.apache.kafka.common.errors.AuthenticationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Kafka publisher will create a new Kafka instance of Publisher.
 * Publisher will use a kafka [producer] to communicate with kafka.
 * Records are sent via transactions if [publisherConfig].instanceId is not null.
 * Producer will automatically attempt resends based on [kafkaConfig].
 * Any Exceptions thrown during publish are returned in a CordaFuture.
 */
class CordaKafkaPublisher<K, V>(
    private val publisherConfig: PublisherConfig,
    private val kafkaConfig: Config,
    private val producer: Producer<K, V>) : Publisher<K, V> {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
        private val fatalSendExceptions = listOf(AuthenticationException::class.java, AuthorizationException::class.java,
            IllegalStateException::class.java, SerializationException::class.java, KafkaException::class.java)
    }

    private val closeTimeout = kafkaConfig.getLong(PRODUCER_CLOSE_TIMEOUT)
    private val topicPrefix = kafkaConfig.getString(KAFKA_TOPIC_PREFIX)
    private val instanceId = publisherConfig.instanceId
    private val clientId = publisherConfig.clientId
    private val topic = publisherConfig.topic

    /**
     * Publish a record.
     * Records are published via transactions if an [instanceId] is configured in the [publisherConfig]
     * Publish will retry recoverable transaction related errors based on [kafkaConfig]
     * Any fatal errors are returned in the future as [CordaMessageAPIFatalException]
     */
    @Suppress("TooGenericExceptionCaught")
    override fun publish(record: Record<K, V>): CordaFuture<Boolean> {
        val fut = openFuture<Boolean>()
        try {
            if (instanceId != null) {
                producer.beginTransaction()
            }

            producer.send(getProducerRecord(record)) { it, ex ->
                setFutureFromResponse(ex, fut)
            }

            if (instanceId != null) {
                producer.commitTransaction()
            }
        } catch (ex: IllegalStateException) {
            logErrorAndSetFuture("Kafka producer clientId $clientId, instanceId $instanceId, " +
                    "for topic $topic failed to send. Producer is in an illegal state. Closing producer", ex, fut, true)
        } catch (ex: ProducerFencedException) {
            logErrorAndSetFuture("Kafka producer clientId $clientId, instanceId $instanceId, " +
                    "for topic $topic failed to send. Another producer with the same transactional.id is active. " +
                    "Closing publisher.", ex, fut, true)
        } catch (ex: InvalidProducerEpochException) {
            val message ="Kafka producer clientId $clientId, instanceId $instanceId, " +
                    "for topic $topic failed to send. request sent to the partition leader contains " +
                    "a non-matching producer epoch."
            logErrorSetFutureAndAbortTransaction(message, ex, fut)
        } catch (ex: AuthorizationException) {
            logErrorAndSetFuture("Kafka producer clientId $clientId, instanceId $instanceId, " +
                    "for topic $topic failed to send. Producer is not authorized to write to this topic." +
                    "Closing producer", ex, fut, true)
        } catch (ex: InterruptException) {
            val message ="Kafka producer clientId $clientId, instanceId $instanceId, " +
                    "for topic $topic failed to send. Thread interrupted."
            logErrorSetFutureAndAbortTransaction(message, ex, fut)
        } catch (ex: TimeoutException) {
            val message = "Kafka producer clientId $clientId, instanceId $instanceId, " +
                    "for topic $topic failed to send. Timeout"
            logErrorSetFutureAndAbortTransaction(message, ex, fut)
        } catch (ex: KafkaException) {
            val message = "Kafka producer clientId $clientId, instanceId $instanceId, " +
                    "for topic $topic failed to send. Unknown Kafka error. Closing producer."
            logErrorAndSetFuture(message, ex, fut, true)
        } catch (ex: Exception) {
            val message = "Kafka producer clientId $clientId, instanceId $instanceId, " +
                    "for topic $topic failed to send. Unknown error. Closing producer."
            logErrorAndSetFuture(message, ex, fut, true)
        }

        return fut
    }

    /**
     * Helper function to set a [future] result based on the presence of an [exception]
     */
    private fun setFutureFromResponse(exception: Exception?, future: OpenFuture<Boolean>) {
        if (exception == null) {
            future.set(true)
        } else {
            val message = "Kafka producer clientId $clientId, instanceId $instanceId, " +
                    "for topic $topic failed to send."
            if (fatalSendExceptions.contains(exception::class.java)) {
                log.error("$message Fatal error occurred. Closing producer.", exception)
                future.setException(CordaMessageAPIFatalException(message, exception))
                close()
            } else if (exception is InterruptException) {
                log.warn("$message Thread interrupted.", exception)
                future.setException(CordaMessageAPIIntermittentException(message, exception))
            } else {
                log.error("$message Unknown error occurred. Closing producer.", exception)
                future.setException(CordaMessageAPIFatalException(message, exception))
                close()
            }
        }
    }

    /**
     * Log the [message] and [exception]. Set the [exception] to the [future].
     * If [fatal] is set to true then the producer is closed safely.
     */
    private fun logErrorAndSetFuture(message: String, exception: Exception, future: OpenFuture<Boolean>, fatal: Boolean) {
        if (fatal) {
            log.error(message, exception, future)
            future.setException(CordaMessageAPIFatalException(message, exception))
            close()
        } else {
            log.warn(message, exception, future)
            future.setException(CordaMessageAPIIntermittentException(message, exception))
        }
    }

    /**
     * Log the [message] and [exception]. Set the [exception] to the [future].
     * If the error occurred as part of a transaction then abort the transaction to reinitialise the producer.
     */
    private fun logErrorSetFutureAndAbortTransaction(message: String, exception: Exception, future: OpenFuture<Boolean>) {
        if (instanceId != null) {
            producer.abortTransaction()
            logErrorAndSetFuture("$message Aborting transaction and reinitialising producer.", exception, future, false)
        } else {
            logErrorAndSetFuture(message, exception, future, false)
        }
    }

    /**
     * Safely close a producer. If an exception is thrown swallow the error to avoid double exceptions
     */
    @Suppress("TooGenericExceptionCaught")
    override fun close() {
        try {
            producer.close(Duration.ofMillis(closeTimeout))
        } catch (ex: Exception) {
            log.error("CordaKafkaPublisher failed to close producer safely. ClientId: $clientId, topic: $topic.", ex)
        }
    }

    /**
     * Convert a generic [record] to a Kafka ProducerRecord.
     * Attach the configured kafka topic prefix as a prefix to the [record] topic.
     * @return Producer record with kafka topic prefix attached.
     */
    private fun getProducerRecord(record: Record<K, V>): ProducerRecord<K, V> {
        return ProducerRecord(topicPrefix + record.topic, record.key, record.value)
    }
}
