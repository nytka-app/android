package io.github.nytka_app.ui.firstrun

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import io.github.nytka_app.R
import io.github.nytka_app.core.settings.FirstRunStep
import io.github.nytka_app.ui.ErrorLine
import io.github.nytka_app.ui.LocalNetworkHint
import io.github.nytka_app.ui.LocalNetworkPrompt
import io.github.nytka_app.ui.NytkaSwitch
import io.github.nytka_app.ui.NytkaTextField
import io.github.nytka_app.ui.PairButton
import io.github.nytka_app.ui.PermissionAnswer
import io.github.nytka_app.ui.device.PendantSignalsScreen
import io.github.nytka_app.ui.rememberPermissionRequest

@Composable
fun FirstRunScreen(viewModel: FirstRunViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.loaded) return
    when (state.step) {
        FirstRunStep.Server -> ServerStep(state, viewModel)
        FirstRunStep.Permissions -> PermissionsStep(state, viewModel)
        FirstRunStep.Consent -> ConsentStep(state, viewModel)
        FirstRunStep.Pairing -> PairingStep(state, viewModel)
    }
}

@Composable
private fun ServerStep(
    state: FirstRunUiState,
    viewModel: FirstRunViewModel,
) {
    var revealed by remember { mutableStateOf(false) }
    SetupStep(
        step = FirstRunStep.Server.ordinal,
        title = stringResource(R.string.setup_server_title),
        body = stringResource(R.string.server_step_description),
        primary = {
            Button(onClick = viewModel::testConnection, enabled = !state.testing) {
                Text(if (state.testing) stringResource(R.string.testing) else stringResource(R.string.setup_connect))
            }
        },
    ) {
        ScanQrButton(onScanned = viewModel::qrScanned)
        NytkaTextField(
            value = state.url,
            onValueChange = { viewModel.edit(it, state.token, state.privateNetwork) },
            label = stringResource(R.string.server_url_hint),
            mono = true,
            error = state.serverError,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                ),
            modifier = Modifier.fillMaxWidth(),
        )
        NytkaTextField(
            value = state.token,
            onValueChange = { viewModel.edit(state.url, it, state.privateNetwork) },
            label = stringResource(R.string.token_hint),
            mono = true,
            isError = state.serverError != null,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Password,
                ),
            visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { revealed = !revealed }) {
                    Text(stringResource(if (revealed) R.string.token_hide else R.string.token_show))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NytkaSwitch(
                checked = state.privateNetwork,
                onCheckedChange = { viewModel.edit(state.url, state.token, it) },
            )
            Text(stringResource(R.string.private_network_label))
        }
        if (state.privateNetwork) ErrorLine(stringResource(R.string.private_network_warning))
        LocalNetworkPrompt(state.askLocalNetwork, viewModel::localNetworkAnswered)
        LocalNetworkHint(state.serverUnreachable, onAllowed = viewModel::testConnection)
    }
}

/**
 * Scans the QR code a server's setup page shows and passes its contents to [onScanned]. Android's camera prompt is
 * requested only here, when the user taps the button, with [R.string.scan_qr_code_description] as the rationale.
 */
@Composable
private fun ScanQrButton(onScanned: (String) -> Unit) {
    var denied by remember { mutableStateOf(false) }
    val scan =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            result.contents?.let(onScanned)
        }
    val prompt = stringResource(R.string.scan_qr_code_prompt)
    val options =
        remember(prompt) {
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setBeepEnabled(false)
                .setPrompt(prompt)
        }
    val request =
        rememberPermissionRequest(listOf(Manifest.permission.CAMERA)) { answer ->
            denied = answer != PermissionAnswer.Granted
            if (answer == PermissionAnswer.Granted) scan.launch(options)
        }
    Text(
        stringResource(R.string.scan_qr_code_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (denied) ErrorLine(stringResource(R.string.scan_qr_code_denied))
    OutlinedButton(onClick = request) { Text(stringResource(R.string.scan_qr_code)) }
}

@Composable
private fun PermissionsStep(
    state: FirstRunUiState,
    viewModel: FirstRunViewModel,
) {
    val context = LocalContext.current
    // Read again after each answer: Android may have allowed one permission and refused the other.
    var answers by remember { mutableIntStateOf(0) }
    val nearbyGranted =
        remember(answers) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT).all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        }
    val notifyGranted =
        remember(answers) {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }
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
            onAnswer = {
                answers++
                viewModel.permissionsAnswered(it)
            },
        )
    SetupStep(
        step = FirstRunStep.Permissions.ordinal,
        title = stringResource(R.string.setup_permissions_title),
        body = stringResource(R.string.permissions_description),
        primary = { Button(onClick = request) { Text(stringResource(R.string.action_allow)) } },
        secondary = {
            if (state.permissions == PermissionAnswer.Blocked) {
                OutlinedButton(onClick = viewModel::openSettings) { Text(stringResource(R.string.open_settings)) }
            }
        },
    ) {
        PermissionRow(
            name = stringResource(R.string.permission_nearby_name),
            why = stringResource(R.string.permission_nearby_why),
            granted = nearbyGranted,
        )
        PermissionRow(
            name = stringResource(R.string.permission_notifications_name),
            why = stringResource(R.string.permission_notifications_why),
            granted = notifyGranted,
        )
        when (state.permissions) {
            PermissionAnswer.Denied -> ErrorLine(stringResource(R.string.permissions_denied_nearby))
            PermissionAnswer.Blocked -> ErrorLine(stringResource(R.string.permissions_blocked_nearby))
            else -> Unit
        }
    }
}

@Composable
private fun ConsentStep(
    state: FirstRunUiState,
    viewModel: FirstRunViewModel,
) {
    var showSignals by rememberSaveable { mutableStateOf(false) }
    SetupStep(
        step = FirstRunStep.Consent.ordinal,
        title = stringResource(R.string.setup_consent_title),
        body = stringResource(R.string.setup_consent_body),
        primary = {
            Button(onClick = viewModel::acceptConsent, enabled = state.consentChecked) {
                Text(stringResource(R.string.continue_action))
            }
        },
    ) {
        ConsentNote(
            points =
                listOf(
                    stringResource(R.string.consent_point_who),
                    stringResource(R.string.consent_point_stop),
                    CONSENT_TEXT,
                    stringResource(R.string.consent_point_data),
                ),
            acknowledgement = stringResource(R.string.i_understand),
            checked = state.consentChecked,
            onCheckedChange = viewModel::setConsent,
        )
        TextButton(onClick = { showSignals = true }) { Text(stringResource(R.string.signals_title)) }
    }
    if (showSignals) {
        Dialog(
            onDismissRequest = { showSignals = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize()) { PendantSignalsScreen(onBack = { showSignals = false }) }
        }
    }
}

@Composable
private fun PairingStep(
    state: FirstRunUiState,
    viewModel: FirstRunViewModel,
) {
    SetupStep(
        step = FirstRunStep.Pairing.ordinal,
        title = stringResource(R.string.setup_pairing_title),
        body = stringResource(R.string.pairing_step_description),
        primary = {
            PairButton(
                label = stringResource(R.string.setup_pair),
                onPaired = viewModel::paired,
                onError = viewModel::pairingFailed,
            )
        },
        secondary = {
            TextButton(onClick = viewModel::skipPairing) { Text(stringResource(R.string.set_up_later)) }
        },
    ) {
        state.pairError?.let { ErrorLine(it) }
    }
}
