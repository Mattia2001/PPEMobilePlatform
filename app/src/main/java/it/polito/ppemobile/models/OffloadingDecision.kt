package it.polito.ppemobile.models

import it.polito.ppemobile.models.enums.ProcessingSegment

data class OffloadingDecision(
    val frameId: String,
    val decision: ProcessingSegment,
    val reason: String,
    val confidence: Float?,
    val timestamp: Long
)