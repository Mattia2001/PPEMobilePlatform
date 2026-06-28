package it.polito.ppemobile.models

import it.polito.ppemobile.models.enums.ProcessingSegment

data class FrameResult(
    val frameId: String,
    val timestampGeneration: Long,
    val inferenceTime: Long?,
    val displayTime: Long?,
    val imageReference: String?,
    val processingSegment: ProcessingSegment,
    val detectionResult: DetectionResult,
    val complexity: Float?,
    val inOrder: Boolean,
    val metricsSnapshot: MetricsSnapshot
)