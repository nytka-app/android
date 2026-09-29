package io.github.nytka_app.ui.device

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.nytka_app.ui.StatusUiState
import io.github.nytka_app.ui.developer.DeveloperScreen

/** The Device tab: the device screen, and developer mode opened from its About card. */
@Composable
fun DeviceTab(
    status: StatusUiState,
    onMute: (Boolean) -> Unit,
) {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "device") {
        composable("device") { DeviceScreen(status, onMute, onOpenDeveloper = { navController.navigate("developer") }) }
        composable("developer") { DeveloperScreen(onBack = { navController.popBackStack() }) }
    }
}
