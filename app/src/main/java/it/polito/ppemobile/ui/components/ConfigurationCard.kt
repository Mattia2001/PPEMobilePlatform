package it.polito.ppemobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.polito.ppemobile.models.AcquisitionConfig
import it.polito.ppemobile.ui.formatters.displayName

@Composable
fun ConfigurationCard(configuration: AcquisitionConfig) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Current Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            ConfigurationRow("Model", configuration.cvModel.displayName())
            ConfigurationRow("Strategy", configuration.offloadingStrategy.displayName())
            ConfigurationRow("Runtime", configuration.runtime.displayName())
            if (configuration.runtime == it.polito.ppemobile.models.enums.Runtime.TFLITE) {
                ConfigurationRow("Accelerator", configuration.localAcceleration.name)
            }
        }
    }
}

@Composable
private fun ConfigurationRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label)
        Text(
            text = value,
            fontWeight = FontWeight.Medium
        )
    }
}
