package it.polito.ppemobile.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import it.polito.ppemobile.models.AcquisitionConfig
import it.polito.ppemobile.ui.components.AppHeader
import it.polito.ppemobile.ui.components.ConfigurationCard
import it.polito.ppemobile.ui.components.NavigationButtons
import it.polito.ppemobile.ui.components.StatusCard
import it.polito.ppemobile.ui.viewmodel.SystemStatusViewModel

@Composable
fun HomeScreen(
    currentConfiguration: AcquisitionConfig,
    onNewAcquisitionClick: () -> Unit,
    onResultsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    statusViewModel: SystemStatusViewModel = viewModel()
) {
    val systemStatus by statusViewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        AppHeader()

        Spacer(modifier = Modifier.height(24.dp))

        StatusCard(
            status = systemStatus,
            onRefreshClick = statusViewModel::refresh
        )

        Spacer(modifier = Modifier.height(16.dp))

        ConfigurationCard(configuration = currentConfiguration)

        Spacer(modifier = Modifier.height(24.dp))

        NavigationButtons(
            onNewAcquisitionClick = onNewAcquisitionClick,
            onResultsClick = onResultsClick,
            onSettingsClick = onSettingsClick
        )
    }
}
