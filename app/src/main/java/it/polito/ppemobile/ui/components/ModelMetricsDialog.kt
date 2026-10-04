package it.polito.ppemobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.polito.ppemobile.inference.LocalModelCatalog
import it.polito.ppemobile.models.enums.CVModel
import it.polito.ppemobile.ui.formatters.displayName
import java.util.Locale

@Composable
fun ModelMetricsDialog(
    model: CVModel,
    onDismissRequest: () -> Unit
) {
    val definition = LocalModelCatalog.require(model)
    val evaluation = definition.evaluation

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(model.displayName()) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = definition.datasetLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "FP32 LiteRT/TFLite - input ${definition.inputSize} x ${definition.inputSize}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    text = "Offline evaluation",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = evaluation.testSetLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${evaluation.images} images - ${evaluation.annotations} annotations",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                MetricRow("Precision", evaluation.precision)
                MetricRow("Recall", evaluation.recall)
                MetricRow("mAP50", evaluation.map50)
                MetricRow("mAP50-95", evaluation.map50To95)

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    text = "mAP50-95 by class",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                evaluation.perClass.forEach { metric ->
                    MetricRow(metric.className, metric.map50To95)
                }

                Text(
                    text = "These values describe the held-out dataset test. Device latency and energy are measured separately during acquisitions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun MetricRow(label: String, value: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = String.format(Locale.US, "%.3f", value),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
