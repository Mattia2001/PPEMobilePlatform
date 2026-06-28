package it.polito.ppemobile.models

data class MetricsSnapshot(
    val cpuUsage: Float?,
    val ramUsage: Float?,
    val batteryLevel: Float?,
    val batteryTemperature: Float?,
    val deviceTemperature: Float?,
    val networkStatus: NetworkStatus
)