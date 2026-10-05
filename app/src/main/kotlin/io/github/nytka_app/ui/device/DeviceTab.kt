package io.github.nytka_app.ui.device

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.nytka_app.ui.StatusUiState
import io.github.nytka_app.ui.developer.DeveloperScreen
import io.github.nytka_app.ui.developer.server.ServerSettingsScreen
import io.github.nytka_app.ui.developer.server.TokensScreen
import io.github.nytka_app.ui.developer.server.WebhooksScreen
import io.github.nytka_app.ui.voice.VoiceScreen

/**
 * The Device tab: the device screen, Your voice, and developer mode opened from its About card, with its
 * server screens.
 */
@Composable
fun DeviceTab(
    status: StatusUiState,
    onMute: (Boolean) -> Unit,
) {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "device") {
        composable("device") {
            DeviceScreen(
                status,
                onMute,
                onOpenDeveloper = { navController.navigate("developer") },
                onOpenVoice = { navController.navigate("voice") },
            )
        }
        composable("voice") { VoiceScreen(onBack = { navController.popBackStack() }) }
        composable("developer") {
            DeveloperScreen(
                onBack = { navController.popBackStack() },
                onOpenServerSettings = { navController.navigate("developer/server/settings") },
                onOpenTokens = { navController.navigate("developer/server/tokens") },
                onOpenWebhooks = { navController.navigate("developer/server/webhooks") },
            )
        }
        composable("developer/server/settings") { ServerSettingsScreen(onBack = { navController.popBackStack() }) }
        composable("developer/server/tokens") { TokensScreen(onBack = { navController.popBackStack() }) }
        composable("developer/server/webhooks") { WebhooksScreen(onBack = { navController.popBackStack() }) }
    }
}
