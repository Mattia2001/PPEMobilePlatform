package it.polito.ppemobile.models

data class BoundingBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {

    val centerX: Float
        get() = x + width / 2

    val centerY: Float
        get() = y + height / 2
}