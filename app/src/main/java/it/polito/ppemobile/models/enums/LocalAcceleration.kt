package it.polito.ppemobile.models.enums

enum class LocalAcceleration(val label: String) {
    CPU("CPU — baseline"),
    AUTO("Auto — NPU, GPU, CPU"),
    GPU("GPU preferred (CPU fallback)"),
    NPU("NPU preferred (CPU fallback)");

    fun candidates(): List<String> = when (this) {
        CPU -> listOf("CPU")
        GPU -> listOf("GPU", "CPU")
        NPU -> listOf("NPU", "CPU")
        AUTO -> listOf("NPU", "GPU", "CPU")
    }
}
