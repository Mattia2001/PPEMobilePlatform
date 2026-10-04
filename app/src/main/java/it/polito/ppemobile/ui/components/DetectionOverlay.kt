package it.polito.ppemobile.ui.components

import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import it.polito.ppemobile.models.BoundingBox
import it.polito.ppemobile.models.DetectionResult
import it.polito.ppemobile.models.enums.PPEType
import kotlin.math.max

@Composable
fun DetectionOverlay(
    detectionResult: DetectionResult?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val result = detectionResult ?: return@Canvas
        val transform = PreviewTransform.from(
            canvasWidth = size.width,
            canvasHeight = size.height,
            frameWidth = result.frameWidth,
            frameHeight = result.frameHeight
        )
        val strokeWidth = 3.dp.toPx()

        result.persons.forEach { person ->
            drawDetection(
                box = person.personBox,
                label = "person ${person.globalConfidence.asPercent()}",
                color = Color.Green,
                strokeWidth = strokeWidth,
                transform = transform
            )
            person.ppeDetections.forEach { ppe ->
                drawDetection(
                    box = ppe.boundingBox,
                    label = "${ppe.ppeType.displayName()} ${ppe.confidence.asPercent()}",
                    color = ppe.ppeType.overlayColor(),
                    strokeWidth = strokeWidth,
                    transform = transform
                )
            }
        }
    }
}

private data class PreviewTransform(
    val renderedWidth: Float,
    val renderedHeight: Float,
    val offsetX: Float,
    val offsetY: Float
) {
    companion object {
        fun from(
            canvasWidth: Float,
            canvasHeight: Float,
            frameWidth: Int?,
            frameHeight: Int?
        ): PreviewTransform {
            if (frameWidth == null || frameHeight == null || frameWidth <= 0 || frameHeight <= 0) {
                return PreviewTransform(canvasWidth, canvasHeight, 0f, 0f)
            }
            // PreviewView.ScaleType.FILL_CENTER uses a centered crop.
            val scale = max(canvasWidth / frameWidth, canvasHeight / frameHeight)
            val renderedWidth = frameWidth * scale
            val renderedHeight = frameHeight * scale
            return PreviewTransform(
                renderedWidth = renderedWidth,
                renderedHeight = renderedHeight,
                offsetX = (canvasWidth - renderedWidth) / 2f,
                offsetY = (canvasHeight - renderedHeight) / 2f
            )
        }
    }
}

private fun DrawScope.drawDetection(
    box: BoundingBox,
    label: String,
    color: Color,
    strokeWidth: Float,
    transform: PreviewTransform
) {
    val left = transform.offsetX + box.x * transform.renderedWidth
    val top = transform.offsetY + box.y * transform.renderedHeight
    val width = box.width * transform.renderedWidth
    val height = box.height * transform.renderedHeight

    drawRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(width, height),
        style = Stroke(width = strokeWidth)
    )

    val textPaint = AndroidPaint().apply {
        isAntiAlias = true
        textSize = 13.dp.toPx()
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val horizontalPadding = 4.dp.toPx()
    val verticalPadding = 2.dp.toPx()
    val textWidth = textPaint.measureText(label)
    val fontMetrics = textPaint.fontMetrics
    val textHeight = fontMetrics.bottom - fontMetrics.top
    val labelWidth = textWidth + horizontalPadding * 2
    val labelHeight = textHeight + verticalPadding * 2
    val labelLeft = left.coerceIn(
        minimumValue = 0f,
        maximumValue = (size.width - labelWidth).coerceAtLeast(0f)
    )
    val labelTop = if (top >= labelHeight) {
        top - labelHeight
    } else {
        (top + strokeWidth).coerceAtMost((size.height - labelHeight).coerceAtLeast(0f))
    }

    drawRect(
        color = color.copy(alpha = 0.85f),
        topLeft = Offset(labelLeft, labelTop),
        size = Size(labelWidth, labelHeight)
    )
    textPaint.color = android.graphics.Color.BLACK
    drawContext.canvas.nativeCanvas.drawText(
        label,
        labelLeft + horizontalPadding,
        labelTop + verticalPadding - fontMetrics.top,
        textPaint
    )
}

private fun PPEType.overlayColor(): Color = when (this) {
    PPEType.HELMET -> Color.Yellow
    PPEType.SAFETY_VEST -> Color.Cyan
    PPEType.GLOVES -> Color.Magenta
    PPEType.SHOES, PPEType.BOOTS -> Color(0xFFFF9800)
    else -> Color.White
}

private fun PPEType.displayName(): String = name.lowercase().replace('_', ' ')

private fun Float.asPercent(): String = "%.0f%%".format(this * 100f)
