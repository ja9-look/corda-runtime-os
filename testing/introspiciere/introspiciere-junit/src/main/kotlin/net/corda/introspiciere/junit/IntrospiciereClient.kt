package net.corda.introspiciere.junit

import net.corda.introspiciere.domain.KafkaMessage
import net.corda.introspiciere.domain.TopicDefinition
import net.corda.introspiciere.domain.TopicDefinition.Companion.DEFAULT_PARTITIONS
import net.corda.introspiciere.domain.TopicDefinition.Companion.DEFAULT_REPLICATION_FACTOR
import net.corda.introspiciere.http.CreateTopicReq
import net.corda.introspiciere.http.MessageWriterReq

@Suppress("UNUSED_PARAMETER")
class IntrospiciereClient(private val endpoint: String) {
    fun helloWorld() {
        println("I should call $endpoint/helloworld")
    }

    fun createTopic(
        name: String,
        partitions: Int = DEFAULT_PARTITIONS,
        replicationFactor: Short = DEFAULT_REPLICATION_FACTOR,
    ) {
        val topic = TopicDefinition(name, partitions, replicationFactor)
        CreateTopicReq(topic).request(endpoint)
    }

    fun write(topic: String, key: String, schema: ByteArray, schemaClass: String) {
        MessageWriterReq(
            KafkaMessage(
                topic = topic,
                key = key,
                schema = schema,
                schemaClass = schemaClass
            )
        ).request(endpoint)
    }

    fun read(topic: String, key: String, schemaClass: String) {

    }
}
