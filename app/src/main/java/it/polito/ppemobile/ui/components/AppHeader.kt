package it.polito.ppemobile.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun AppHeader() {
    Text(
        text = "PPE Mobile Platform",
        style = MaterialTheme.typography.headlineMedium
    )
}