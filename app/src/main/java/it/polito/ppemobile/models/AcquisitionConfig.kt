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
    val exportFormat: ExportFormat
)