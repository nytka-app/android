package io.github.nytka_app.ui.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.nytka_app.ui.StatusUiState
import io.github.nytka_app.ui.developer.DeveloperScreen
import io.github.nytka_app.ui.developer.server.ServerSettingsScreen
import io.github.nytka_app.ui.developer.server.TokensScreen
import io.github.nytka_app.ui.developer.server.WebhooksScreen
import io.github.nytka_app.ui.transcribe.PhoneTranscriptionScreen
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
                onOpenPendant = { navController.navigate("pendant") },
                onOpenSignals = { navController.navigate("signals") },
                onOpenAboutRecording = { navController.navigate("about-recording") },
                onOpenPhoneTranscription = { navController.navigate("phone-transcription") },
            )
        }
        composable("pendant") { entry ->
            // Shares the Device screen's view model: forgetting here updates what it shows.
            val device = remember(entry) { navController.getBackStackEntry("device") }
            PendantDetailScreen(hiltViewModel(device), onBack = { navController.popBackStack() })
        }
        composable("signals") { PendantSignalsScreen(onBack = { navController.popBackStack() }) }
        composable("about-recording") {
            AboutRecordingScreen(
                onBack = { navController.popBackStack() },
                onOpenSignals = { navController.navigate("signals") },
            )
        }
        composable("phone-transcription") { PhoneTranscriptionScreen(onBack = { navController.popBackStack() }) }
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
