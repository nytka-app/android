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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.capture.PairedPendant
import io.github.nytka_app.core.api.NytkaApi
import io.github.nytka_app.firmware.FirmwareNotice
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
    onOpenVoice: () -> Unit,
    viewModel: DeviceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pairError by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    var backlogDismissed by rememberSaveable { mutableStateOf(false) }
    // A later question is a new one: show it again.
    LaunchedEffect(state.backlogPackets == null) { if (state.backlogPackets == null) backlogDismissed = false }

    val pendantInfo = (status.capture.connection as? PendantConnection.Connected)?.info
    // Also runs again when the switch changes: the checker reads the setting.
    LaunchedEffect(pendantInfo, state.firmwareCheck) { viewModel.pendantSeen(pendantInfo) }

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
                onFirmwareCheck = viewModel::setFirmwareCheck,
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
        state.pendantSettings?.let { card ->
            item { PendantSettingsCard(card, viewModel::commitLed, viewModel::commitGain) }
        }
        item {
            MuteScheduleSection(
                state.muteSchedule,
                viewModel::setMuteSchedule,
                state.muteServer,
                state.timeZoneHint,
                state.timeZoneError,
                viewModel::setServerTimeZone,
            )
        }
        item {
            ConsentChimeSection(
                state.consentChime,
                state.consentChimeMinutes,
                viewModel::setConsentChime,
                viewModel::setConsentChimeMinutes,
            )
        }
        if (state.serverContext == true) {
            item { PhoneContextSection(state.phoneContext, viewModel::setPhoneContext) }
        }
        item { ServerSection(state, status, viewModel::checkServer) }
        item { SettingsSection(state, viewModel::save) }
        item {
            BriefsSection(
                state,
                onSwitch = viewModel::setBriefNotifications,
                onPermission = viewModel::briefPermissionAnswered,
                onOpenSettings = viewModel::openAppSettings,
            )
        }
        item {
            Section(stringResource(R.string.people)) {
                Text(stringResource(R.string.people_description))
            }
        }
        if (state.serverVoice == true) {
            item {
                Section(stringResource(R.string.voice_title)) {
                    Text(stringResource(R.string.voice_entry_summary))
                    OutlinedButton(onClick = onOpenVoice) { Text(stringResource(R.string.voice_entry_button)) }
                }
            }
        }
        item {
            Section(stringResource(R.string.about)) {
                Text(
                    stringResource(R.string.app_version_format, state.version),
                    modifier = Modifier.clickable(onClick = viewModel::tapVersion),
                )
                if (!state.developerMode && state.tapsToDeveloper in 1..3) {
                    Text(
                        pluralStringResource(
                            R.plurals.taps_to_developer_format,
                            state.tapsToDeveloper,
                            state.tapsToDeveloper,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (state.developerMode) {
                    OutlinedButton(
                        onClick = onOpenDeveloper,
                    ) { Text(stringResource(R.string.developer_mode)) }
                }
                Text(
                    stringResource(R.string.about_affiliation),
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
            title = { Text(stringResource(R.string.forget_pendant_title)) },
            text = { Text(stringResource(R.string.forget_pendant_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    viewModel.forgetPendant()
                }) { Text(stringResource(R.string.forget)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmForget = false },
                ) { Text(stringResource(R.string.action_cancel)) }
            },
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
    onFirmwareCheck: (Boolean) -> Unit,
) {
    Section(stringResource(R.string.pendant)) {
        val connection = status.capture.connection
        val info = (connection as? PendantConnection.Connected)?.info
        Text(state.pendantName ?: stringResource(R.string.no_pendant_paired))
        Text(
            when (connection) {
                is PendantConnection.Connected -> stringResource(R.string.connected)
                is PendantConnection.Connecting -> stringResource(R.string.connecting)
                is PendantConnection.Refused -> connection.reason
                PendantConnection.Disconnected -> stringResource(R.string.disconnected)
            },
        )
        status.capture.battery?.let { Text(stringResource(R.string.battery_percent_format, it)) }
        info?.firmware?.let { Text(stringResource(R.string.firmware_format, it)) }
        state.firmwareNotice?.let { FirmwareNoticeText(it) }
        pairError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.pendantAddress == null) {
                PairButton(label = stringResource(R.string.pair_pendant), onPaired = onPaired, onError = onPairError)
            } else {
                FilledTonalButton(
                    onClick = { onMute(!status.muted) },
                ) { Text(stringResource(if (status.muted) R.string.unmute else R.string.mute)) }
                OutlinedButton(onClick = { onForget() }) { Text(stringResource(R.string.forget)) }
            }
        }
        if (state.pendantAddress != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = state.firmwareCheck, onCheckedChange = onFirmwareCheck)
                Text(stringResource(R.string.firmware_check_label))
            }
            Text(
                stringResource(R.string.firmware_check_explanation),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun FirmwareNoticeText(notice: FirmwareNotice) {
    val uri = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(R.string.firmware_available_format, notice.latest, notice.current),
            color = MaterialTheme.colorScheme.primary,
        )
        TextButton(
            onClick = { uri.openUri(notice.instructionsUrl) },
        ) { Text(stringResource(R.string.firmware_how_to_update)) }
    }
}

@Composable
private fun ServerSection(
    state: DeviceUiState,
    status: StatusUiState,
    onCheck: () -> Unit,
) {
    Section(stringResource(R.string.server)) {
        Text(state.serverUrl.ifEmpty { stringResource(R.string.no_server_set) })
        Text(state.serverState)
        state.apiVersion?.let { Text(stringResource(R.string.api_version_format, it)) }
        if (state.apiMismatch) {
            Text(
                stringResource(R.string.api_mismatch_format, state.apiVersion ?: 0, NytkaApi.API_VERSION),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(stringResource(R.string.queued_chunks_format, status.queuedChunks))
        Text(status.serverLine)
        LocalNetworkHint(state.serverUnreachable || status.serverUnreachable, onAllowed = onCheck)
        OutlinedButton(onClick = onCheck) { Text(stringResource(R.string.check_again)) }
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

    Section(stringResource(R.string.settings)) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(R.string.server_url_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = {
                Text(stringResource(if (state.tokenSet) R.string.token_keep_hint else R.string.token_hint))
            },
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
            Text(stringResource(R.string.private_network_label))
        }
        state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        FilledTonalButton(onClick = {
            onSave(url, token, privateNetwork)
            token = ""
        }) { Text(stringResource(R.string.action_save)) }
    }

    if (confirmPrivate) {
        AlertDialog(
            onDismissRequest = { confirmPrivate = false },
            title = { Text(stringResource(R.string.allow_plain_http_title)) },
            text = {
                Text(stringResource(R.string.allow_plain_http_message))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmPrivate = false
                    privateNetwork = true
                }) { Text(stringResource(R.string.action_allow)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmPrivate = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
internal fun Section(
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
