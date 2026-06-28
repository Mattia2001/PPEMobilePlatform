package it.polito.ppemobile.models

import it.polito.ppemobile.models.enums.PPEType

data class PPEDetection(
    val ppeType: PPEType,
    val boundingBox: BoundingBox,
    val confidence: Float
)