package it.polito.ppemobile.models

data class AcquisitionSummary(
    val averageFps: Float?,
    val averageCpu: Float?,
    val averageRam: Float?,
    val average5GSignal: Float?,
    val averageRtt: Float?,
    val averageBattery: Float?,
    val averageTemperature: Float?,
    val totalBatteryConsumption: Float?,
    val averagePpeConfidence: Float?,
    val averageComplexity: Float?,
    val averageLatency: Float?,
    val totalUplinkData: Long,
    val totalDownlinkData: Long,
    val offloadedFrames: Int,
    val discardedFrames: Int
)