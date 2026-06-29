package it.polito.ppemobile.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.polito.ppemobile.models.AcquisitionConfig
import it.polito.ppemobile.models.MetricsSnapshot

@Composable
fun InfoPanel(
    configuration: AcquisitionConfig?,
    metrics: MetricsSnapshot?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        AcquisitionMetricsPanel(metrics = metrics)

        if (configuration != null) {
            Spacer(modifier = Modifier.height(8.dp))
            AcquisitionConfigurationCard(configuration = configuration)
        }
    }
}