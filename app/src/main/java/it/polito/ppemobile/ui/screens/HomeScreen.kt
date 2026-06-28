package it.polito.ppemobile.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.polito.ppemobile.ui.components.AppHeader
import it.polito.ppemobile.ui.components.ConfigurationCard
import it.polito.ppemobile.ui.components.NavigationButtons
import it.polito.ppemobile.ui.components.StatusCard

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        AppHeader()

        Spacer(modifier = Modifier.height(24.dp))

        StatusCard()

        Spacer(modifier = Modifier.height(16.dp))

        ConfigurationCard()

        Spacer(modifier = Modifier.height(24.dp))

        NavigationButtons()
    }
}