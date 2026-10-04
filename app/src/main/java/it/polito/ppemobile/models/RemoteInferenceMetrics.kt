package it.polito.ppemobile.models

data class RemoteInferenceMetrics(
    val apiVersion: String,
    val modelId: String,
    val modelVersion: String,
    val device: String,
    val inputSize: Int,
    val encodedBytes: Long,
    val responseBytes: Long,
    val encodeMs: Float,
    val roundTripMs: Float,
    val transportAndApiOverheadMs: Float,
    val queueMs: Float,
    val decodeMs: Float,
    val preprocessMs: Float,
    val inferenceMs: Float,
    val postprocessMs: Float,
    val totalServerMs: Float,
    val serverReceiveTimestampMs: Long,
    val serverCompleteTimestampMs: Long
)
