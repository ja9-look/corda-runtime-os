package net.corda.flow.pipeline

import net.corda.data.flow.event.FlowEvent
import net.corda.flow.state.FlowCheckpoint
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record

/**
 * [FlowEventContext] contains information about a received [FlowEvent] and state that should be modified when passed through a
 * [FlowEventPipeline].
 *
 * When a [FlowEventPipeline] completes the [checkpoint] and [outputRecords] of the context will be written to the message bus. Setting the
 * [checkpoint] to null will cause its mapping to be removed from the compacted topic.
 *
 * @param checkpoint The [FlowCheckpoint] of a flow that should be modified by the pipeline.
 * @param inputEvent The received [FlowEvent].
 * @param inputEventPayload The received [FlowEvent.payload].
 * @param outputRecords The [Record]s that should be sent back to the message bus when the pipeline completes.
 * @param T The type of [FlowEvent.payload].
 * @param mdcProperties properties to set the flow fibers MDC with.
 */
data class FlowEventContext<T>(
    val checkpoint: FlowCheckpoint,
    val inputEvent: FlowEvent,
    var inputEventPayload: T,
    val config: SmartConfig,
    val outputRecords: List<Record<*, *>>,
    val mdcProperties: Map<String, String>,
    val processingStatus: ProcessingStatus = ProcessingStatus.SUCCESS
) {
    enum class ProcessingStatus {
        SUCCESS, SEND_TO_DLQ, STRAY_EVENT
    }
}
