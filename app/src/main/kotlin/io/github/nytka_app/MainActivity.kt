package io.github.nytka_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.nytka_app.ui.AppViewModel
import io.github.nytka_app.ui.NytkaNavHost
import io.github.nytka_app.ui.StatusChip
import io.github.nytka_app.ui.StatusViewModel
import io.github.nytka_app.ui.conversations.ConversationsTab
import io.github.nytka_app.ui.device.DeviceScreen
import io.github.nytka_app.ui.firstrun.FirstRunScreen
import io.github.nytka_app.ui.theme.NytkaTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NytkaTheme {
                val app: AppViewModel = hiltViewModel()
                val onboarded by app.onboarded.collectAsStateWithLifecycle()
                when (onboarded) {
                    null -> Unit
                    false -> FirstRunScreen()
                    true -> {
                        val statusViewModel: StatusViewModel = hiltViewModel()
                        val status by statusViewModel.state.collectAsStateWithLifecycle()
                        NytkaNavHost(
                            topBar = {
                                CenterAlignedTopAppBar(
                                    title = { Text("Nytka") },
                                    actions = { StatusChip(status) },
                                )
                            },
                            conversations = { ConversationsTab(status, statusViewModel::setMuted) },
                            device = { DeviceScreen(status, statusViewModel::setMuted, onOpenDeveloper = {}) },
                        )
                    }
                }
            }
        }
    }
}
