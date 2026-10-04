package it.polito.ppemobile.models

data class LocalExecutionInfo(
    val requested: String,
    val configuredBackend: String,
    val runtimeApi: String,
    val fallbackReason: String?,
    val warmUpMs: Double,
    val soc: String,
    val gpuRenderer: String,
    val runtimeAccelerators: List<String>,
    // CompiledModel 2.1.5 does not expose per-operator placement in the Kotlin API.
    val acceleratorPlacementVerified: Boolean = false
)
