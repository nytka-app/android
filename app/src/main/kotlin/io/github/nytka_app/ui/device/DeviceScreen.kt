package io.github.nytka_app.ui.device

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.capture.PairedPendant
import io.github.nytka_app.pendant.PendantConnection
import io.github.nytka_app.ui.LocalNetworkHint
import io.github.nytka_app.ui.LocalNetworkPrompt
import io.github.nytka_app.ui.PairButton
import io.github.nytka_app.ui.StatusUiState

@Composable
fun DeviceScreen(
    status: StatusUiState,
    onMute: (Boolean) -> Unit,
    onOpenDeveloper: () -> Unit,
    viewModel: DeviceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pairError by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    var backlogDismissed by rememberSaveable { mutableStateOf(false) }
    // A later question is a new one: show it again.
    LaunchedEffect(state.backlogPackets == null) { if (state.backlogPackets == null) backlogDismissed = false }

    LocalNetworkPrompt(state.askLocalNetwork, viewModel::localNetworkAnswered)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PendantSection(
                state,
                status,
                pairError,
                onPaired = viewModel::paired,
                onPairError = { pairError = it },
                onMute = onMute,
                onForget = { confirmForget = true },
            )
        }
        state.storage?.let { card ->
            item {
                PendantStorageCard(
                    card,
                    onSyncNow = viewModel::syncNow,
                    onStop = viewModel::stopSync,
                    onAnswerBacklog = { backlogDismissed = false },
                )
            }
        }
        item { ServerSection(state, status, viewModel::checkServer) }
        item { SettingsSection(state, viewModel::save) }
        item {
            Section("About") {
                Text(
                    "Nytka ${state.version}",
                    modifier = Modifier.clickable(onClick = viewModel::tapVersion),
                )
                if (!state.developerMode && state.tapsToDeveloper in 1..3) {
                    Text(
                        "${state.tapsToDeveloper} more taps for developer mode",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (state.developerMode) OutlinedButton(onClick = onOpenDeveloper) { Text("Developer mode") }
                Text(
                    "An independent project for the Omi pendant; not affiliated with Based Hardware.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    state.backlogPackets?.let { packets ->
        BacklogDialog(
            packets,
            dismissed = backlogDismissed,
            onDismiss = { backlogDismissed = true },
            onImport = viewModel::importBacklog,
            onDiscard = viewModel::discardBacklog,
        )
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Forget this pendant?") },
            text = { Text("Capture stops, and the pendant has to be paired again. Queued audio still uploads.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    viewModel.forgetPendant()
                }) { Text("Forget") }
            },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PendantSection(
    state: DeviceUiState,
    status: StatusUiState,
    pairError: String?,
    onPaired: (PairedPendant) -> Unit,
    onPairError: (String) -> Unit,
    onMute: (Boolean) -> Unit,
    onForget: () -> Unit,
) {
    Section("Pendant") {
        val connection = status.capture.connection
        val info = (connection as? PendantConnection.Connected)?.info
        Text(state.pendantName ?: "No pendant paired")
        Text(
            when (connection) {
                is PendantConnection.Connected -> "Connected"
                is PendantConnection.Connecting -> "Connecting…"
                is PendantConnection.Refused -> connection.reason
                PendantConnection.Disconnected -> "Not connected"
            },
        )
        status.capture.battery?.let { Text("Battery $it%") }
        info?.firmware?.let { Text("Firmware $it") }
        pairError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.pendantAddress == null) {
                PairButton(onPaired = onPaired, onError = onPairError)
            } else {
                FilledTonalButton(
                    onClick = { onMute(!status.muted) },
                ) { Text(if (status.muted) "Unmute" else "Mute") }
                OutlinedButton(onClick = { onForget() }) { Text("Forget") }
            }
        }
    }
}

@Composable
private fun ServerSection(
    state: DeviceUiState,
    status: StatusUiState,
    onCheck: () -> Unit,
) {
    Section("Server") {
        Text(state.serverUrl.ifEmpty { "No server set" })
        Text(state.serverState)
        state.apiVersion?.let { Text("API version $it") }
        if (state.apiMismatch) {
            Text(
                "This server speaks API version ${state.apiVersion}; this app speaks 1. " +
                    "Update whichever is older.",
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text("Queued chunks: ${status.queuedChunks}")
        Text(status.serverLine)
        LocalNetworkHint(state.serverUnreachable || status.serverUnreachable, onAllowed = onCheck)
        OutlinedButton(onClick = onCheck) { Text("Check again") }
    }
}

@Composable
private fun SettingsSection(
    state: DeviceUiState,
    onSave: (String, String, Boolean) -> Unit,
) {
    var url by rememberSaveable(state.serverUrl) { mutableStateOf(state.serverUrl) }
    // Not saveable: the saved-instance Bundle is plain text, and the token is stored only encrypted.
    var token by remember { mutableStateOf("") }
    var privateNetwork by rememberSaveable(state.privateNetwork) { mutableStateOf(state.privateNetwork) }
    var confirmPrivate by rememberSaveable { mutableStateOf(false) }

    Section("Settings") {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Server URL") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text(if (state.tokenSet) "Token (leave empty to keep)" else "Token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = privateNetwork, onCheckedChange = {
                if (it) {
                    confirmPrivate = true
                } else {
                    privateNetwork =
                        false
                }
            })
            Text("Private network (allow plain HTTP)")
        }
        state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        FilledTonalButton(onClick = {
            onSave(url, token, privateNetwork)
            token = ""
        }) { Text("Save") }
    }

    if (confirmPrivate) {
        AlertDialog(
            onDismissRequest = { confirmPrivate = false },
            title = { Text("Allow plain HTTP?") },
            text = {
                Text(
                    "Without HTTPS your audio and transcripts cross the network unencrypted. Turn this on only for a " +
                        "server you reach through a VPN or tailnet you trust.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmPrivate = false
                    privateNetwork = true
                }) { Text("Allow") }
            },
            dismissButton = { TextButton(onClick = { confirmPrivate = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
