package io.github.nytka_app.ui.developer

import android.content.ClipData
import android.content.ClipDescription.EXTRA_IS_SENSITIVE
import android.os.PersistableBundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.ui.SettingsBlock
import io.github.nytka_app.ui.SettingsCard
import io.github.nytka_app.ui.SettingsRow
import io.github.nytka_app.ui.SettingsToggleRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(
    onBack: () -> Unit,
    onOpenServerSettings: () -> Unit,
    onOpenTokens: () -> Unit,
    onOpenWebhooks: () -> Unit,
    viewModel: DeveloperViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.developer_mode_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsCard(stringResource(R.string.pendant)) {
                    SettingsBlock(stringResource(R.string.bluetooth)) {
                        Text(stringResource(R.string.packets_per_second_format, state.packetsPerSecond))
                        Text(stringResource(R.string.lost_percent_format, state.lossPercent))
                        Text(stringResource(R.string.frames_dropped, state.droppedFrames))
                        Text(stringResource(R.string.frames_this_session, state.framesThisSession))
                    }
                    state.storage?.let { details ->
                        HorizontalDivider()
                        StorageSectionCard(details)
                    }
                }
            }
            item {
                SettingsCard(stringResource(R.string.on_this_phone)) {
                    SettingsBlock(stringResource(R.string.queue_and_upload)) {
                        Text(stringResource(R.string.queue_mb_format, state.queueMegabytes))
                        Text(
                            stringResource(
                                R.string.sealed_chunks_and_unsealed_format,
                                state.sealedChunks,
                                state.unsealedFrames,
                            ),
                        )
                        Text(stringResource(R.string.chunks_dropped_at_cap, state.droppedChunks))
                        Text(stringResource(R.string.uploaded_this_run, state.uploadedChunks))
                        Text(state.lastUpload ?: stringResource(R.string.last_upload_none))
                    }
                    HorizontalDivider()
                    AlertSection(state, viewModel::setThresholds)
                }
            }
            item {
                SettingsCard(stringResource(R.string.your_server)) {
                    SettingsBlock(stringResource(R.string.server)) {
                        Text(state.serverStatus)
                        OutlinedButton(
                            onClick = viewModel::refreshServerStatus,
                        ) { Text(stringResource(R.string.check_status)) }
                        SettingsRow(stringResource(R.string.server_settings), onClick = onOpenServerSettings)
                        SettingsRow(stringResource(R.string.access_tokens), onClick = onOpenTokens)
                        SettingsRow(stringResource(R.string.webhooks), onClick = onOpenWebhooks)
                    }
                }
            }
            item {
                SettingsCard(stringResource(R.string.developer)) {
                    SettingsBlock(stringResource(R.string.testing_section)) {
                        SettingsToggleRow(
                            stringResource(R.string.fake_pendant_label),
                            checked = state.fakePendant,
                            onCheckedChange = viewModel::setFakePendant,
                        )
                        SettingsToggleRow(
                            stringResource(R.string.high_priority_connection_label),
                            checked = state.highPriorityConnection,
                            onCheckedChange = viewModel::setHighPriorityConnection,
                        )
                        FilledTonalButton(
                            onClick = viewModel::recordFixture,
                        ) { Text(stringResource(R.string.save_next_60_seconds)) }
                        state.fixture?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        FilledTonalButton(onClick = {
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(
                                        ClipData.newPlainText("Nytka debug report", viewModel.report()).apply {
                                            // Holds the server URL: keep it out of clipboard previews.
                                            description.extras =
                                                PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
                                        },
                                    ),
                                )
                            }
                        }) { Text(stringResource(R.string.copy_debug_report)) }
                    }
                    HorizontalDivider()
                    SettingsBlock(stringResource(R.string.diagnostics)) {
                        Text(stringResource(R.string.diagnostics_samples_format, state.diagnosticsSamples))
                        FilledTonalButton(
                            onClick = viewModel::exportDiagnostics,
                        ) { Text(stringResource(R.string.export_diagnostics)) }
                        state.diagnosticsExport?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        SettingsToggleRow(
                            stringResource(R.string.send_diagnostics_to_server),
                            checked = state.diagnosticsUpload,
                            onCheckedChange = viewModel::setDiagnosticsUpload,
                        )
                        state.diagnosticsNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    OutlinedButton(
                        onClick = viewModel::turnOff,
                    ) { Text(stringResource(R.string.turn_off_developer_mode)) }
                }
            }
        }
    }
}

@Composable
private fun AlertSection(
    state: DeveloperUiState,
    onSave: (Int, Int, Int) -> Unit,
) {
    var disconnected by rememberSaveable(
        state.disconnectedMinutes,
    ) { mutableStateOf(state.disconnectedMinutes.toString()) }
    var unreachable by rememberSaveable(
        state.unreachableMinutes,
    ) { mutableStateOf(state.unreachableMinutes.toString()) }
    var battery by rememberSaveable(state.batteryPercent) { mutableStateOf(state.batteryPercent.toString()) }

    SettingsBlock(stringResource(R.string.alert_thresholds)) {
        NumberField(stringResource(R.string.pendant_disconnected_minutes), disconnected) { disconnected = it }
        NumberField(stringResource(R.string.server_unreachable_minutes), unreachable) { unreachable = it }
        NumberField(stringResource(R.string.battery_percent_label), battery) { battery = it }
        FilledTonalButton(onClick = {
            onSave(
                disconnected.toIntOrNull() ?: state.disconnectedMinutes,
                unreachable.toIntOrNull() ?: state.unreachableMinutes,
                battery.toIntOrNull() ?: state.batteryPercent,
            )
        }) { Text(stringResource(R.string.save_thresholds)) }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter(Char::isDigit)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(280.dp),
    )
}
