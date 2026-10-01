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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
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
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.set_up_nytka), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.step_of_format, state.step.ordinal + 1, FirstRunStep.entries.size),
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

@Composable
private fun ServerStep(
    state: FirstRunUiState,
    viewModel: FirstRunViewModel,
) {
    Text(stringResource(R.string.server_step_description))
    OutlinedTextField(
        value = state.url,
        onValueChange = { viewModel.edit(it, state.token, state.privateNetwork) },
        label = { Text(stringResource(R.string.server_url_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.token,
        onValueChange = { viewModel.edit(state.url, it, state.privateNetwork) },
        label = { Text(stringResource(R.string.token_hint)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Switch(checked = state.privateNetwork, onCheckedChange = { viewModel.edit(state.url, state.token, it) })
        Text(stringResource(R.string.private_network_label))
    }
    if (state.privateNetwork) {
        Text(
            stringResource(R.string.private_network_warning),
            color = MaterialTheme.colorScheme.error,
        )
    }
    LocalNetworkPrompt(state.askLocalNetwork, viewModel::localNetworkAnswered)
    state.serverError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    LocalNetworkHint(state.serverUnreachable, onAllowed = viewModel::testConnection)
        Button(onClick = viewModel::testConnection, enabled = !state.testing) {
        Text(if (state.testing) stringResource(R.string.testing) else stringResource(R.string.test_connection))
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
    Text(stringResource(R.string.permissions_description))
    when (state.permissions) {
        PermissionAnswer.Denied ->
            Text(
                stringResource(R.string.permissions_denied_nearby),
                color = MaterialTheme.colorScheme.error,
            )
        PermissionAnswer.Blocked ->
            Text(
                stringResource(R.string.permissions_blocked_nearby),
                color = MaterialTheme.colorScheme.error,
            )
        else -> Unit
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = request) { Text(stringResource(R.string.action_allow)) }
        if (state.permissions == PermissionAnswer.Blocked) {
            OutlinedButton(onClick = viewModel::openSettings) { Text(stringResource(R.string.open_settings)) }
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
        Text(stringResource(R.string.i_understand))
    }
    Button(onClick = viewModel::acceptConsent, enabled = state.consentChecked) { Text(stringResource(R.string.continue_action)) }
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
