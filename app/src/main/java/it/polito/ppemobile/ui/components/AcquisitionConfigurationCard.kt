package it.polito.ppemobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import it.polito.ppemobile.models.AcquisitionConfig
import it.polito.ppemobile.ui.formatters.displayName

@Composable
fun AcquisitionConfigurationCard(
    configuration: AcquisitionConfig,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Model: ${configuration.cvModel.displayName()}")
                    Text("Runtime: ${configuration.runtime.displayName()}")
                    Text("Strategy: ${configuration.offloadingStrategy.displayName()}")
                }

                Column {
                    Text("FPS: ${configuration.fps}")
                    Text("Quality: ${configuration.videoQuality}")
                    Text("JPEG: ${configuration.compressionLevel}")
                }
            }
        }
    }
}
