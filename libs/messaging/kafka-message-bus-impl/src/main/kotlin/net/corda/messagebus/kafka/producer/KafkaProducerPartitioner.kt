package net.corda.messagebus.kafka.producer

import net.corda.data.chunking.ChunkKey
import net.corda.data.flow.FlowKey
import net.corda.utilities.trace
import net.corda.v5.base.util.ByteArrays.toHexString
import net.corda.v5.crypto.DigestAlgorithmName
import org.apache.kafka.clients.producer.Partitioner
import org.apache.kafka.clients.producer.internals.BuiltInPartitioner
import org.apache.kafka.common.Cluster
import org.apache.kafka.common.utils.Utils
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

/**
 * Custom partitioner to be used with Kafka Producers.
 */
class KafkaProducerPartitioner : Partitioner {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun configure(configs: MutableMap<String, *>?) {
        logger.info("Config = $configs")
    }

    override fun close() {}

    /**
     * To ensure chunked records have unique keys, the real key is wrapped in a ChunkKey object.
     * This means kafka compaction will not delete chunks as the keys will be different.
     * However, we still want the records to end up on the same partition so when the key is a ChunkKey we pass the real key bytes to the
     * kafka built in partitioner logic. This function is what is called by default within the normal kafkaProducers.
     * @param topic topic
     * @param key the key of the record. Could be the real key or an object of type ChunkKey
     * @param keyBytes bytes of the key
     * @param value value of the record
     * @param valueBytes serialized value bytes
     * @param cluster cluster object provided by kafka client with cluster information
     * @return partition to send record to
     */
    @Suppress("EmptyCatchBlock")
    override fun partition(topic: String, key: Any, keyBytes: ByteArray, value: Any?, valueBytes: ByteArray?, cluster: Cluster): Int {
        val partitionCount = cluster.partitionsForTopic(topic).size

        fun logPartition(partition: Int) {
            logger.trace { "Sending record on topic $topic to partition $partition (of $partitionCount) with key $key" }
        }

        fun partitionFor(hex: String, partitionCountToUse: Int = partitionCount): Int {
            return (Utils.toPositive(hex.toUInt(16).toInt()) % partitionCountToUse).also { logPartition(it) }
        }

        fun deserializeString(bytes: ByteArray): String? {
            return try {
                String(bytes, StandardCharsets.UTF_8)
            } catch (e: Exception) {
                null
            }
        }

        fun partitionFromHash(
            withHash: String,
            invert: Boolean = false,
            partitionCountToUse: Int = partitionCount
        ): Int {
            return if (invert) {
                // Invert for p2p.in
                if (withHash.contains("-INITIATED")) {
                    // Just immediately mod the hash of the flow ID
                    partitionFor(withHash.substring(1, 9), partitionCountToUse)
                } else {
                    // Just immediately mod the hash of the receiver flow ID
                    partitionFor(withHash.substring(9, 17), partitionCountToUse)
                }
            } else {
                if (withHash.contains("-INITIATED")) {
                    // Just immediately mod the hash of the receiver flow ID
                    partitionFor(withHash.substring(9, 17), partitionCountToUse)
                } else {
                    // Just immediately mod the hash of the flow ID
                    partitionFor(withHash.substring(1, 9), partitionCountToUse)
                }
            }
        }

        val (keyBytesToPartition: ByteArray, keyToPartition: String?) = if (key is ChunkKey) {
            logger.trace { "Found ChunkKey. Using real bytes $keyBytes for partitioning" }
            val originalKeyBytes = key.realKey.array().clone()
            originalKeyBytes to deserializeString(originalKeyBytes)
        } else {
            keyBytes to ((key as? String) ?: deserializeString(keyBytes))
        }

        if (key is FlowKey && topic.endsWith("flow.status")) {
            val keyId = key.id
            if (keyId.startsWith("#") && keyId.indexOf('/') == 17) return partitionFromHash(keyId)
            val clientIDSecureHash =
                MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name).digest(keyId.encodeToByteArray())
            return partitionFromHash("#" + toHexString(clientIDSecureHash).substring(0, 8))
        }
        if (keyToPartition is String) {
            if (keyToPartition.startsWith("#") && keyToPartition.indexOf('/') == 17) {
                return partitionFromHash(keyToPartition, topic.endsWith("p2p.in"))
            } else if (keyToPartition.length == 36) {
                try {
                    UUID.fromString(keyToPartition)
                    return partitionFor(keyToPartition.substring(0, 8))
                } catch (e: Exception) {
                }
            } else if (topic.contains("flow.mapper.event") && keyToPartition.startsWith("{\"id\": \"")) {
                @Suppress("MaxLineLength")
                // {"id": "3c45da84-0c9a-469b-b495-ff2b78269253", "identity": {"x500Name": "CN=Alice-506d57d0-6512-48b2-a49e-c292faa38656, OU=Application, O=R3, L=London, C=GB", "groupId": "b1f0b906-60c2-442f-b23b-c5eb47e923ff"}}
                val keyId = keyToPartition.substring(8).substringBeforeLast("\", \"identity\": {")
                val clientIDSecureHash =
                    MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name).digest(keyId.encodeToByteArray())
                return partitionFromHash("#" + toHexString(clientIDSecureHash).substring(0, 8))
            }
        }
        val partition = BuiltInPartitioner.partitionForKey(keyBytesToPartition, partitionCount)
        return partition.also { logPartition(it) }
    }

}
