package it.polito.ppemobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import it.polito.ppemobile.ui.screens.AcquisitionConfigurationScreen
import it.polito.ppemobile.ui.screens.AcquisitionScreen
import it.polito.ppemobile.ui.screens.HomeScreen
import it.polito.ppemobile.ui.screens.ResultsScreen
import it.polito.ppemobile.ui.screens.SettingsScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

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
                onStartAcquisitionClick = {
                    navController.navigate("acquisition")
                }
            )
        }

        composable("acquisition") {
            AcquisitionScreen()
        }

        composable("results") {
            ResultsScreen()
        }

        composable("settings") {
            SettingsScreen()
        }
    }
}