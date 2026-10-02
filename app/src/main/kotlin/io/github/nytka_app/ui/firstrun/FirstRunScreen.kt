package io.github.nytka_app.ui.firstrun

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.core.settings.FirstRunStep
import io.github.nytka_app.ui.LocalNetworkHint
import io.github.nytka_app.ui.LocalNetworkPrompt
import io.github.nytka_app.ui.PairButton
import io.github.nytka_app.ui.PermissionAnswer
import io.github.nytka_app.ui.rememberPermissionRequest

@Composable
fun FirstRunScreen(viewModel: FirstRunViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.loaded) return

    Scaffold { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Set up Nytka", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Step ${state.step.ordinal + 1} of ${FirstRunStep.entries.size}",
                style = MaterialTheme.typography.labelLarge,
            )
            when (state.step) {
                FirstRunStep.Server -> ServerStep(state, viewModel)
                FirstRunStep.Permissions -> PermissionsStep(state, viewModel)
                FirstRunStep.Consent -> ConsentStep(state, viewModel)
                FirstRunStep.Pairing -> PairingStep(state, viewModel)
            }
        }
    }
}

@Composable
private fun ServerStep(
    state: FirstRunUiState,
    viewModel: FirstRunViewModel,
) {
    Text("Your Nytka server's address and token, from its .env file.")
    OutlinedTextField(
        value = state.url,
        onValueChange = { viewModel.edit(it, state.token, state.privateNetwork) },
        label = { Text("Server URL, e.g. https://nytka.example.com") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.token,
        onValueChange = { viewModel.edit(state.url, it, state.privateNetwork) },
        label = { Text("Token") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Switch(checked = state.privateNetwork, onCheckedChange = { viewModel.edit(state.url, state.token, it) })
        Text("Private network (allow plain HTTP)")
    }
    if (state.privateNetwork) {
        Text(
            "Plain HTTP sends audio unencrypted. Use it only on a VPN or tailnet you trust.",
            color = MaterialTheme.colorScheme.error,
        )
    }
    LocalNetworkPrompt(state.askLocalNetwork, viewModel::localNetworkAnswered)
    state.serverError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    LocalNetworkHint(state.serverUnreachable, onAllowed = viewModel::testConnection)
    Button(onClick = viewModel::testConnection, enabled = !state.testing) {
        Text(if (state.testing) "Testing…" else "Test connection")
    }
}

@Composable
private fun PermissionsStep(
    state: FirstRunUiState,
    viewModel: FirstRunViewModel,
) {
    val wanted =
        buildList {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
    val request =
        rememberPermissionRequest(
            wanted,
            required = listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
            onAnswer = viewModel::permissionsAnswered,
        )
    Text(
        "Nytka needs the nearby-devices permission to reach the pendant, and notifications to show that it is " +
            "recording. It never uses the phone's microphone.",
    )
    when (state.permissions) {
        PermissionAnswer.Denied ->
            Text(
                "Without nearby devices Nytka cannot reach the pendant. Tap Allow to be asked again.",
                color = MaterialTheme.colorScheme.error,
            )

        PermissionAnswer.Blocked ->
            Text(
                "Without nearby devices Nytka cannot reach the pendant. If Android does not ask again, allow it in " +
                    "the system settings for Nytka, then come back and tap Allow.",
                color = MaterialTheme.colorScheme.error,
            )

        else -> Unit
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = request) { Text("Allow") }
        if (state.permissions == PermissionAnswer.Blocked) {
            OutlinedButton(onClick = viewModel::openSettings) { Text("Open settings") }
        }
    }
}

@Composable
private fun ConsentStep(
    state: FirstRunUiState,
    viewModel: FirstRunViewModel,
) {
    Text(CONSENT_TEXT, style = MaterialTheme.typography.bodyLarge)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = state.consentChecked, onCheckedChange = viewModel::setConsent)
        Text("I understand")
    }
    Button(onClick = viewModel::acceptConsent, enabled = state.consentChecked) { Text("Continue") }
}

@Composable
private fun PairingStep(
    state: FirstRunUiState,
    viewModel: FirstRunViewModel,
) {
    Text("Turn the pendant on, keep it next to the phone, and pick it in the list that opens.")
    state.pairError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    PairButton(onPaired = viewModel::paired, onError = viewModel::pairingFailed)
    TextButton(onClick = viewModel::skipPairing) { Text("Set up later") }
}
