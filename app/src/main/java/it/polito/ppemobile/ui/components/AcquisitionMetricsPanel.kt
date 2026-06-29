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
import it.polito.ppemobile.models.MetricsSnapshot

@Composable
fun AcquisitionMetricsPanel(
    metrics: MetricsSnapshot?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Live Metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("FPS: --")
                    Text("Latency: -- ms")
                    Text("CPU: ${metrics?.cpuUsage?.format(1) ?: "--"} %")
                }

                Column {
                    Text("RAM: ${metrics?.ramUsage?.format(0) ?: "--"} MB")
                    Text("Battery: ${metrics?.batteryLevel?.format(0) ?: "--"} %")
                    Text("Temp: ${metrics?.batteryTemperature?.format(1) ?: "--"} °C")
                }
            }
        }
    }
}

private fun Float.format(decimals: Int): String {
    return "%.${decimals}f".format(this)
}