package it.polito.ppemobile.models

import it.polito.ppemobile.models.enums.CVModel
import it.polito.ppemobile.models.enums.ExportFormat
import it.polito.ppemobile.models.enums.OffloadingStrategy
import it.polito.ppemobile.models.enums.PPEType
import it.polito.ppemobile.models.enums.Runtime

data class AcquisitionConfig(
    val offloadingStrategy: OffloadingStrategy,
    val cvModel: CVModel,
    val runtime: Runtime,
    val fps: Int,
    val videoQuality: String,
    val compressionLevel: Int,
    val selectedPPEs: List<PPEType>,
    val slidingWindowEnabled: Boolean,
    val majorityVotingEnabled: Boolean,
    val exportFormat: ExportFormat,
    val localAcceleration: it.polito.ppemobile.models.enums.LocalAcceleration = it.polito.ppemobile.models.enums.LocalAcceleration.CPU
) {
    companion object {
        fun default() = AcquisitionConfig(
            offloadingStrategy = OffloadingStrategy.ALWAYS_LOCAL,
            cvModel = CVModel.YOLO26N_V2,
            runtime = Runtime.TFLITE,
            fps = 30,
            videoQuality = "1080p",
            compressionLevel = 80,
            selectedPPEs = listOf(
                PPEType.HELMET,
                PPEType.SAFETY_VEST,
                PPEType.GLOVES,
                PPEType.SHOES,
                PPEType.BOOTS
            ),
            slidingWindowEnabled = false,
            majorityVotingEnabled = false,
            exportFormat = ExportFormat.JSONL
        )
    }
}
