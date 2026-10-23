package io.github.nytka_app.ui.device

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import io.github.nytka_app.ui.BatteryMeter
import io.github.nytka_app.ui.LocalNetworkHint
import io.github.nytka_app.ui.LocalNetworkPrompt
import io.github.nytka_app.ui.PairButton
import io.github.nytka_app.ui.SettingsBlock
import io.github.nytka_app.ui.SettingsCard
import io.github.nytka_app.ui.SettingsRow
import io.github.nytka_app.ui.SettingsToggleRow
import io.github.nytka_app.ui.StatusUiState
import io.github.nytka_app.ui.theme.NytkaTheme

@Composable
fun DeviceScreen(
    status: StatusUiState,
    onMute: (Boolean) -> Unit,
    onOpenDeveloper: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenPendant: () -> Unit,
    onOpenSignals: () -> Unit,
    onOpenAboutRecording: () -> Unit,
    onOpenPhoneTranscription: () -> Unit,
    viewModel: DeviceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pairError by rememberSaveable { mutableStateOf<String?>(null) }
    var backlogDismissed by rememberSaveable { mutableStateOf(false) }
    var showFontLicenses by rememberSaveable { mutableStateOf(false) }
    // A later question is a new one: show it again.
    LaunchedEffect(state.backlogPackets == null) { if (state.backlogPackets == null) backlogDismissed = false }

    val pendantInfo = (status.capture.connection as? PendantConnection.Connected)?.info
    // Also runs again when the switch changes: the checker reads the setting.
    LaunchedEffect(pendantInfo, state.firmwareCheck) {
        viewModel.pendantSeen(pendantInfo, status.capture.capabilities)
    }

    LocalNetworkPrompt(state.askLocalNetwork, viewModel::localNetworkAnswered)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsCard(stringResource(R.string.pendant)) {
                PendantBlock(
                    state,
                    status,
                    pairError,
                    onPaired = viewModel::paired,
                    onPairError = { pairError = it },
                    onMute = onMute,
                    onFirmwareCheck = viewModel::setFirmwareCheck,
                )
                if (state.pendantAddress != null) {
                    SettingsRow(
                        stringResource(R.string.pendant_details),
                        supporting = state.pendantName,
                        onClick = onOpenPendant,
                    )
                }
                SettingsRow(stringResource(R.string.signals_title), onClick = onOpenSignals)
                state.storage?.let { card ->
                    HorizontalDivider()
                    PendantStorageCard(
                        card,
                        onSyncNow = viewModel::syncNow,
                        onStop = viewModel::stopSync,
                        onAnswerBacklog = { backlogDismissed = false },
                    )
                }
                state.pendantSettings?.let { card ->
                    HorizontalDivider()
                    PendantSettingsCard(card, viewModel::commitLed, viewModel::commitGain)
                }
            }
        }
        item {
            SettingsCard(stringResource(R.string.on_this_phone)) {
                MuteScheduleSection(
                    state.muteSchedule,
                    viewModel::setMuteSchedule,
                    state.muteServer,
                    state.timeZoneHint,
                    state.timeZoneError,
                    viewModel::setServerTimeZone,
                )
                HorizontalDivider()
                ConsentChimeSection(
                    state.consentChime,
                    state.consentChimeMinutes,
                    viewModel::setConsentChime,
                    viewModel::setConsentChimeMinutes,
                )
                if (state.serverContext == true) {
                    HorizontalDivider()
                    PhoneContextSection(
                        state,
                        onSwitch = viewModel::setPhoneContext,
                        onActivity = viewModel::setActivityContext,
                        onActivityPermission = viewModel::activityPermissionAnswered,
                        onNowPlaying = viewModel::setNowPlayingContext,
                        onOpenSettings = viewModel::openAppSettings,
                    )
                    HorizontalDivider()
                    AppearanceSection(state.wallpaperColors, viewModel::setWallpaperColors)
                }
                if (state.serverPhoneMic == true) {
                    HorizontalDivider()
                    PhoneMicSection(
                        state,
                        onSwitch = viewModel::setPhoneMic,
                        onPermission = viewModel::phoneMicPermissionAnswered,
                        onOpenSettings = viewModel::openAppSettings,
                    )
                }
                HorizontalDivider()
                BriefsSection(
                    state,
                    onSwitch = viewModel::setBriefNotifications,
                    onPermission = viewModel::briefPermissionAnswered,
                    onOpenSettings = viewModel::openAppSettings,
                )
                HorizontalDivider()
                LocationSection(
                    state,
                    onSwitch = viewModel::setLocationTags,
                    onPermission = viewModel::locationPermissionAnswered,
                    onOpenSettings = viewModel::openAppSettings,
                    onAddPlace = viewModel::addSavedPlace,
                    onRemovePlace = viewModel::removeSavedPlace,
                )
                HorizontalDivider()
                SettingsRow(
                    stringResource(R.string.phone_transcription_title),
                    supporting = stringResource(R.string.phone_transcription_entry_summary),
                    onClick = onOpenPhoneTranscription,
                )
            }
        }
        item {
            SettingsCard(stringResource(R.string.your_server)) {
                ServerSection(state, status, viewModel::checkServer)
                HorizontalDivider()
                SettingsSection(state, viewModel::save)
                HorizontalDivider()
                OutputLanguageSection(
                    state.outputLanguage,
                    state.outputLanguageLocked,
                    state.outputLanguageError,
                    viewModel::setOutputLanguage,
                )
                SettingsRow(
                    stringResource(R.string.people),
                    supporting = stringResource(R.string.people_description),
                )
                if (state.serverVoice == true) {
                    SettingsRow(
                        stringResource(R.string.voice_title),
                        supporting = stringResource(R.string.voice_entry_summary),
                        onClick = onOpenVoice,
                    )
                }
            }
        }
        if (state.developerMode) {
            item {
                SettingsCard(stringResource(R.string.developer)) {
                    SettingsRow(stringResource(R.string.developer_mode), onClick = onOpenDeveloper)
                }
            }
        }
        item {
            SettingsCard(stringResource(R.string.about)) {
                SettingsRow(
                    stringResource(R.string.app_version_format, state.version),
                    Modifier.clickable(onClick = viewModel::tapVersion),
                    supporting =
                        if (!state.developerMode && state.tapsToDeveloper in 1..3) {
                            pluralStringResource(
                                R.plurals.taps_to_developer_format,
                                state.tapsToDeveloper,
                                state.tapsToDeveloper,
                            )
                        } else {
                            null
                        },
                )
                SettingsRow(stringResource(R.string.about_recording), onClick = onOpenAboutRecording)
                SettingsRow(stringResource(R.string.font_licenses), onClick = { showFontLicenses = true })
                Text(
                    stringResource(R.string.about_affiliation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

    if (showFontLicenses) FontLicensesDialog(onDismiss = { showFontLicenses = false })
}

@Composable
private fun PendantBlock(
    state: DeviceUiState,
    status: StatusUiState,
    pairError: String?,
    onPaired: (PairedPendant) -> Unit,
    onPairError: (String) -> Unit,
    onMute: (Boolean) -> Unit,
    onFirmwareCheck: (Boolean) -> Unit,
) {
    SettingsBlock(null) {
        val connection = status.capture.connection
        val info = (connection as? PendantConnection.Connected)?.info
        SettingsRow(
            state.pendantName ?: stringResource(R.string.no_pendant_paired),
            supporting =
                when (connection) {
                    is PendantConnection.Connected -> stringResource(R.string.connected)
                    is PendantConnection.Connecting -> stringResource(R.string.connecting)
                    is PendantConnection.Refused -> connection.reason
                    PendantConnection.Disconnected -> stringResource(R.string.disconnected)
                },
        )
        status.capture.battery?.let { BatteryMeter(it) }
        info?.firmware?.let { SettingsRow(stringResource(R.string.firmware_format, it)) }
        state.firmwareNotice?.let { FirmwareNoticeText(it) }
        pairError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.pendantAddress == null) {
            PairButton(label = stringResource(R.string.pair_pendant), onPaired = onPaired, onError = onPairError)
        } else {
            FilledTonalButton(
                onClick = { onMute(!status.muted) },
            ) { Text(stringResource(if (status.muted) R.string.unmute else R.string.mute)) }
            if (status.capture.capabilities?.audioSwitch == false) {
                Text(
                    stringResource(R.string.mute_still_hears),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SettingsToggleRow(
                stringResource(R.string.firmware_check_label),
                checked = state.firmwareCheck,
                onCheckedChange = onFirmwareCheck,
                supporting = stringResource(R.string.firmware_check_explanation),
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
    SettingsBlock(stringResource(R.string.server)) {
        if (state.serverUrl.isEmpty()) {
            Text(stringResource(R.string.no_server_set))
        } else {
            Text(state.serverUrl, style = NytkaTheme.typography.code)
        }
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

    SettingsBlock(stringResource(R.string.settings)) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(R.string.server_url_label)) },
            singleLine = true,
            textStyle = NytkaTheme.typography.code,
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
        SettingsToggleRow(
            stringResource(R.string.private_network_label),
            checked = privateNetwork,
            onCheckedChange = { if (it) confirmPrivate = true else privateNetwork = false },
        )
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
