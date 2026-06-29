package it.polito.ppemobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import it.polito.ppemobile.models.enums.ProcessingSegment

@Composable
fun ProcessingStatusOverlay(
    processingSegment: ProcessingSegment,
    modifier: Modifier = Modifier
) {
    val label = when (processingSegment) {
        ProcessingSegment.LOCAL -> "LOCAL"
        ProcessingSegment.REMOTE -> "REMOTE"
    }

    Box(
        modifier = modifier
            .wrapContentSize()
            .background(
                color = Color.Black.copy(alpha = 0.65f),
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge
        )
    }
}