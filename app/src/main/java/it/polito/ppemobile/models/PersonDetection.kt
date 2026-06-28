package it.polito.ppemobile.models

data class PersonDetection(
    val personId: String,
    val personBox: BoundingBox,
    val ppeDetections: List<PPEDetection>,
    val globalConfidence: Float
)