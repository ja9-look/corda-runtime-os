package net.corda.interop.identity.write.impl

import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.INTEROP_GROUP_POLICY_TOPIC
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference


class InteropGroupPolicyProducer(
    private val publisher: AtomicReference<Publisher?>
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun publishInteropGroupPolicy(groupId: String, groupPolicy: String) {
        if (publisher.get() == null) {
            logger.error("Interop group policy publisher is null, not publishing.")
            return
        }

        val futures = publisher.get()!!.publish(listOf(Record(INTEROP_GROUP_POLICY_TOPIC, groupId, groupPolicy)))

        futures.forEach { it.get() }

        logger.info("Interop group policy published with key : $groupId and value : $groupPolicy")
    }
}