package it.polito.ppemobile.models

data class DetectionResult(
    val persons: List<PersonDetection>,
    val frameWidth: Int? = null,
    val frameHeight: Int? = null,
    val inferenceDiagnostics: InferenceDiagnostics? = null,
    val localExecution: LocalExecutionInfo? = null
)

data class InferenceDiagnostics(
    val maxConfidenceByClass: Map<String, Float>,
    val candidatesAboveThreshold: Int,
    val detectionsAfterNms: Int
)
