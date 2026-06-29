package it.polito.ppemobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import it.polito.ppemobile.storage.ExportedAcquisitionInfo
import it.polito.ppemobile.ui.components.ShareExportButton
import it.polito.ppemobile.ui.viewmodel.ResultsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ResultsScreen(
    modifier: Modifier = Modifier,
    viewModel: ResultsViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.loadExports()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Results",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (viewModel.exportedAcquisitions.isEmpty()) {
            Text("No exported acquisitions available.")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(viewModel.exportedAcquisitions) { acquisitionInfo ->
                    ExportedAcquisitionCard(
                        acquisitionInfo = acquisitionInfo,
                        onDeleteClick = {
                            viewModel.deleteExport(acquisitionInfo)
                        }

                    )
                }
            }
        }
    }
}

@Composable
private fun ExportedAcquisitionCard(
    acquisitionInfo: ExportedAcquisitionInfo,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = acquisitionInfo.acquisitionId,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text("Device: ${acquisitionInfo.deviceName}")
            Text("Start: ${formatTimestamp(acquisitionInfo.startTime)}")
            Text("Duration: ${formatDuration(acquisitionInfo.startTime, acquisitionInfo.finishTime)}")
            Text("Model: ${acquisitionInfo.model}")
            Text("Strategy: ${acquisitionInfo.strategy}")
            Text("Runtime: ${acquisitionInfo.runtime}")
            Text("Avg CPU: ${acquisitionInfo.averageCpu?.format(1) ?: "--"} %")
            Text("Avg RAM: ${acquisitionInfo.averageRam?.format(0) ?: "--"} MB")
            Text("Avg latency: ${acquisitionInfo.averageLatency?.format(1) ?: "--"} ms")
            Text("Avg PPE conf.: ${acquisitionInfo.averagePpeConfidence?.format(2) ?: "--"}")
            Text("Frames: ${acquisitionInfo.frameCount}")
            Text("First frame: ${acquisitionInfo.firstFrameTimestamp?.let { formatTimestamp(it) } ?: "--"}")
            Text("Last frame: ${acquisitionInfo.lastFrameTimestamp?.let { formatTimestamp(it) } ?: "--"}")
            Text("Avg inference/frame: ${acquisitionInfo.averageInferenceTimeFromFrames?.format(1) ?: "--"} ms")

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShareExportButton(exportedFile = acquisitionInfo.file)

                OutlinedButton(
                    onClick = onDeleteClick
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun formatDuration(startTime: Long, finishTime: Long?): String {
    if (finishTime == null) return "--"
    return "%.1f s".format((finishTime - startTime) / 1000f)
}

private fun Float.format(decimals: Int): String {
    return "%.${decimals}f".format(this)
}