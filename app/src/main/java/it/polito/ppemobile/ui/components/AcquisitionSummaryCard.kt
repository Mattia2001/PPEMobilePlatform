package it.polito.ppemobile.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.polito.ppemobile.models.Acquisition

@Composable
fun AcquisitionSummaryCard(
    acquisition: Acquisition,
    modifier: Modifier = Modifier
) {
    val summary = acquisition.summary
    val durationSeconds = acquisition.finishTime
        ?.let { (it - acquisition.startTime) / 1000f }

    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Acquisition Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Duration: ${durationSeconds?.format(1) ?: "--"} s")
                    Text("Avg CPU: ${summary?.averageCpu?.format(1) ?: "--"} %")
                    Text("Avg RAM: ${summary?.averageRam?.format(0) ?: "--"} MB")
                }

                Column {
                    Text("Avg latency: ${summary?.averageLatency?.format(1) ?: "--"} ms")
                    Text("Battery used: ${summary?.totalBatteryConsumption?.format(1) ?: "--"} %")
                    Text("Avg PPE conf.: ${summary?.averagePpeConfidence?.format(2) ?: "--"}")
                }
            }
        }
    }
}

private fun Float.format(decimals: Int): String {
    return "%.${decimals}f".format(this)
}