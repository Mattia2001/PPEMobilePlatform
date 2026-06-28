package it.polito.ppemobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NavigationButtons(
    onNewAcquisitionClick: () -> Unit,
    onResultsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onNewAcquisitionClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("New Acquisition")
        }

        Button(
            onClick = onResultsClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Results")
        }

        Button(
            onClick = onSettingsClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Settings")
        }
    }
}