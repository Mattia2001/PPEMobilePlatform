package it.polito.ppemobile.models

data class NetworkStatus(
    val networkType: String,
    val signalStrength: Float?,
    val rtt: Float?,
    val uplinkThroughput: Float?,
    val downlinkThroughput: Float?,
    val uploadedBytes: Long,
    val downloadedBytes: Long
)