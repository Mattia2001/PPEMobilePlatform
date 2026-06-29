package it.polito.ppemobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.polito.ppemobile.models.enums.AcquisitionState

@Composable
fun AcquisitionControls(
    acquisitionState: AcquisitionState,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val startButtonText = when (acquisitionState) {
        AcquisitionState.RUNNING -> "Running"
        else -> "Start"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onStartClick,
            enabled = acquisitionState != AcquisitionState.RUNNING,
            modifier = Modifier.weight(1f)
        ) {
            Text(startButtonText)
        }

        OutlinedButton(
            onClick = onStopClick,
            enabled = acquisitionState == AcquisitionState.RUNNING,
            modifier = Modifier.weight(1f)
        ) {
            Text("Stop")
        }
    }
}