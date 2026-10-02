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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.util.Locale

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
                title = { Text("Developer mode") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
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
                Section("Bluetooth") {
                    Text("Packets: ${"%.1f".format(Locale.ROOT, state.packetsPerSecond)} per second")
                    Text("Lost: ${"%.2f".format(Locale.ROOT, state.lossPercent)}% (under 1% is healthy)")
                    Text("Frames dropped: ${state.droppedFrames}")
                    Text("Frames this session: ${state.framesThisSession}")
                }
            }
            item {
                Section("Queue and upload") {
                    Text("Queue: ${"%.1f".format(Locale.ROOT, state.queueMegabytes)} MB")
                    Text("Sealed chunks: ${state.sealedChunks}, frames not yet sealed: ${state.unsealedFrames}")
                    Text("Chunks dropped at the cap: ${state.droppedChunks}")
                    Text("Uploaded this run: ${state.uploadedChunks}")
                    Text("Last upload: ${state.lastUpload ?: "none"}")
                }
            }
            state.storage?.let { details -> item { StorageSectionCard(details) } }
            item {
                Section("Server") {
                    Text(state.serverStatus)
                    OutlinedButton(onClick = viewModel::refreshServerStatus) { Text("Check status") }
                    ServerRow("Server settings", onOpenServerSettings)
                    ServerRow("Access tokens", onOpenTokens)
                    ServerRow("Webhooks", onOpenWebhooks)
                }
            }
            item {
                Section("Testing") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Switch(checked = state.fakePendant, onCheckedChange = viewModel::setFakePendant)
                        Text("Fake pendant (replays a bundled recording)")
                    }
                    FilledTonalButton(onClick = viewModel::recordFixture) { Text("Save the next 60 seconds of frames") }
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
                    }) { Text("Copy debug report") }
                }
            }
            item {
                Section("Diagnostics") {
                    Text("${state.diagnosticsSamples} samples kept for 7 days, one every 10 seconds while capture runs")
                    FilledTonalButton(onClick = viewModel::exportDiagnostics) { Text("Export diagnostics") }
                    state.diagnosticsExport?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Switch(checked = state.diagnosticsUpload, onCheckedChange = viewModel::setDiagnosticsUpload)
                        Text("Send diagnostics to my server")
                    }
                    state.diagnosticsNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            item { AlertSection(state, viewModel::setThresholds) }
            item { OutlinedButton(onClick = viewModel::turnOff) { Text("Turn off developer mode") } }
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

    Section("Alert thresholds") {
        NumberField("Pendant disconnected, minutes", disconnected) { disconnected = it }
        NumberField("Server unreachable, minutes", unreachable) { unreachable = it }
        NumberField("Battery, percent", battery) { battery = it }
        FilledTonalButton(onClick = {
            onSave(
                disconnected.toIntOrNull() ?: state.disconnectedMinutes,
                unreachable.toIntOrNull() ?: state.unreachableMinutes,
                battery.toIntOrNull() ?: state.batteryPercent,
            )
        }) { Text("Save thresholds") }
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
