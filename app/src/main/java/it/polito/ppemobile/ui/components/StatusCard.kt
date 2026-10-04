package it.polito.ppemobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.polito.ppemobile.ui.viewmodel.SystemCheck
import it.polito.ppemobile.ui.viewmodel.SystemCheckState
import it.polito.ppemobile.ui.viewmodel.SystemStatusUiState

@Composable
fun StatusCard(
    status: SystemStatusUiState,
    onRefreshClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "System Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = onRefreshClick,
                    enabled = !status.refreshing
                ) {
                    Text(if (status.refreshing) "Checking…" else "Refresh")
                }
            }

            StatusRow(status.device)
            StatusRow(status.camera)
            StatusRow(status.hardware)
            StatusRow(status.runtime)
            StatusRow(status.server)
        }
    }
}

@Composable
private fun StatusRow(check: SystemCheck) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = check.label)
            Row {
                Text(
                    text = "●",
                    color = check.state.statusColor(),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = check.value,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        if (check.detail.isNotBlank()) {
            Text(
                text = check.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SystemCheckState.statusColor(): Color = when (this) {
    SystemCheckState.CHECKING -> MaterialTheme.colorScheme.onSurfaceVariant
    SystemCheckState.READY -> Color(0xFF2E7D32)
    SystemCheckState.WARNING -> Color(0xFFEF6C00)
    SystemCheckState.UNAVAILABLE -> MaterialTheme.colorScheme.error
}
