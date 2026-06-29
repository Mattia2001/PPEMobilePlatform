package it.polito.ppemobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import it.polito.ppemobile.models.AcquisitionConfig
import it.polito.ppemobile.ui.screens.AcquisitionConfigurationScreen
import it.polito.ppemobile.ui.screens.AcquisitionScreen
import it.polito.ppemobile.ui.screens.HomeScreen
import it.polito.ppemobile.ui.screens.ResultsScreen
import it.polito.ppemobile.ui.screens.SettingsScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    var currentConfig by remember { mutableStateOf<AcquisitionConfig?>(null) }

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                onNewAcquisitionClick = {
                    navController.navigate("acquisition_config")
                },
                onResultsClick = {
                    navController.navigate("results")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                }
            )
        }

        composable("acquisition_config") {
            AcquisitionConfigurationScreen(
                onStartAcquisitionClick = { config ->
                    currentConfig = config
                    navController.navigate("acquisition")
                }
            )
        }

        composable("acquisition") {
            AcquisitionScreen(
                configuration = currentConfig
            )
        }

        composable("results") {
            ResultsScreen()
        }

        composable("settings") {
            SettingsScreen()
        }
    }
}