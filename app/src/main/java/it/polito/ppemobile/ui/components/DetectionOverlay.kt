package it.polito.ppemobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import it.polito.ppemobile.models.BoundingBox
import it.polito.ppemobile.models.DetectionResult
import it.polito.ppemobile.models.enums.PPEType

@Composable
fun DetectionOverlay(
    detectionResult: DetectionResult?,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        if (detectionResult == null) return@Canvas

        val strokeWidth = 3.dp.toPx()

        detectionResult.persons.forEach { person ->

            drawBoundingBox(
                box = person.personBox,
                color = Color.Green,
                strokeWidth = strokeWidth,
                canvasWidth = size.width,
                canvasHeight = size.height
            )

            person.ppeDetections.forEach { ppe ->
                val color = when (ppe.ppeType) {
                    PPEType.HELMET -> Color.Yellow
                    PPEType.SAFETY_VEST -> Color.Cyan
                    else -> Color.White
                }

                drawBoundingBox(
                    box = ppe.boundingBox,
                    color = color,
                    strokeWidth = strokeWidth,
                    canvasWidth = size.width,
                    canvasHeight = size.height
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBoundingBox(
    box: BoundingBox,
    color: Color,
    strokeWidth: Float,
    canvasWidth: Float,
    canvasHeight: Float
) {
    drawRect(
        color = color,
        topLeft = Offset(
            x = box.x * canvasWidth,
            y = box.y * canvasHeight
        ),
        size = Size(
            width = box.width * canvasWidth,
            height = box.height * canvasHeight
        ),
        style = Stroke(width = strokeWidth)
    )
}