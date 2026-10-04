package io.github.nytka_app.ui.developer

import android.content.ClipData
import android.content.ClipDescription.EXTRA_IS_SENSITIVE
import android.os.PersistableBundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
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
                Section(stringResource(R.string.bluetooth)) {
                    Text(stringResource(R.string.packets_per_second_format, state.packetsPerSecond))
                    Text(stringResource(R.string.lost_percent_format, state.lossPercent))
                    Text(stringResource(R.string.frames_dropped, state.droppedFrames))
                    Text(stringResource(R.string.frames_this_session, state.framesThisSession))
                }
            }
            item {
                Section(stringResource(R.string.queue_and_upload)) {
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
            }
            state.storage?.let { details -> item { StorageSectionCard(details) } }
            item {
                Section(stringResource(R.string.server)) {
                    Text(state.serverStatus)
                    OutlinedButton(
                        onClick = viewModel::refreshServerStatus,
                    ) { Text(stringResource(R.string.check_status)) }
                    ServerRow(stringResource(R.string.server_settings), onOpenServerSettings)
                    ServerRow(stringResource(R.string.access_tokens), onOpenTokens)
                    ServerRow(stringResource(R.string.webhooks), onOpenWebhooks)
                }
            }
            item {
                Section(stringResource(R.string.testing_section)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Switch(checked = state.fakePendant, onCheckedChange = viewModel::setFakePendant)
                        Text(stringResource(R.string.fake_pendant_label))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Switch(
                            checked = state.highPriorityConnection,
                            onCheckedChange = viewModel::setHighPriorityConnection,
                        )
                        Text(stringResource(R.string.high_priority_connection_label))
                    }
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
            }
            item {
                Section(stringResource(R.string.diagnostics)) {
                    Text(stringResource(R.string.diagnostics_samples_format, state.diagnosticsSamples))
                    FilledTonalButton(
                        onClick = viewModel::exportDiagnostics,
                    ) { Text(stringResource(R.string.export_diagnostics)) }
                    state.diagnosticsExport?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Switch(checked = state.diagnosticsUpload, onCheckedChange = viewModel::setDiagnosticsUpload)
                        Text(stringResource(R.string.send_diagnostics_to_server))
                    }
                    state.diagnosticsNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            item { AlertSection(state, viewModel::setThresholds) }
            item {
                OutlinedButton(onClick = viewModel::turnOff) { Text(stringResource(R.string.turn_off_developer_mode)) }
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

    Section(stringResource(R.string.alert_thresholds)) {
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

@Composable
private fun ServerRow(
    title: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
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
