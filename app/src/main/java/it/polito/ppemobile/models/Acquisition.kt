package it.polito.ppemobile.models

data class Acquisition(
    val acquisitionId: String,
    val startTime: Long,
    val finishTime: Long?,
    val deviceName: String,
    val configuration: AcquisitionConfig,
    val summary: AcquisitionSummary?
)