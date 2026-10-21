package io.github.nytka_app.ui.transcribe

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.core.transcribe.DraftTextRow
import io.github.nytka_app.transcribe.BenchmarkResult
import io.github.nytka_app.transcribe.ModelImport
import io.github.nytka_app.ui.ErrorLine
import io.github.nytka_app.ui.SettingsBlock
import io.github.nytka_app.ui.SettingsCard
import io.github.nytka_app.ui.SettingsNote
import io.github.nytka_app.ui.SettingsRow
import io.github.nytka_app.ui.SettingsToggleRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private const val BATTERY_MIN = 20
private const val BATTERY_MAX = 100
private const val BATTERY_STEP = 10

/** "Transcribe on this phone", reached from the Device tab: the switch, the model, the drafts, the benchmark. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneTranscriptionScreen(
    onBack: () -> Unit,
    viewModel: PhoneTranscriptionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let { viewModel.folderPicked(it.toString()) }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.phone_transcription_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                SettingsCard(stringResource(R.string.phone_transcription_title)) {
                    SwitchBlock(state, viewModel)
                }
            }
            if (state.available) {
                item {
                    SettingsCard(stringResource(R.string.phone_transcription_model)) {
                        ModelBlock(state, onPick = { pick.launch(null) }, onDelete = viewModel::deleteModel)
                    }
                }
                item {
                    SettingsCard(stringResource(R.string.phone_transcription_drafts)) {
                        DraftsBlock(state.drafts, viewModel::deleteDrafts)
                    }
                }
                if (state.developerMode && state.model != null) {
                    item {
                        SettingsCard(stringResource(R.string.phone_transcription_benchmark)) {
                            BenchmarkBlock(state, viewModel::runBenchmark)
                        }
                    }
                }
            }
        }
    }

    state.prompt?.let { ImportDialog(it, viewModel::confirmImport, viewModel::dismissImport) }
}

@Composable
private fun SwitchBlock(
    state: PhoneTranscriptionUiState,
    viewModel: PhoneTranscriptionViewModel,
) {
    if (!state.available) {
        SettingsNote(stringResource(R.string.phone_transcription_unavailable))
        return
    }
    if (state.model == null) {
        SettingsNote(stringResource(R.string.phone_transcription_needs_model))
    } else {
        SettingsToggleRow(
            stringResource(R.string.phone_transcription_label),
            checked = state.on,
            onCheckedChange = viewModel::setOn,
            supporting = stringResource(R.string.phone_transcription_explanation),
        )
    }
    if (state.on) {
        HorizontalDivider()
        SettingsToggleRow(
            stringResource(R.string.phone_transcription_on_battery),
            checked = state.onBattery,
            onCheckedChange = viewModel::setOnBattery,
            supporting =
                if (state.onBattery) {
                    null
                } else {
                    stringResource(R.string.phone_transcription_charging_only)
                },
        )
        if (state.onBattery) BatterySlider(state.batteryPercent, viewModel::setBatteryPercent)
        if (state.waitingSeconds > 0) {
            SettingsNote(stringResource(R.string.phone_transcription_waiting_format, (state.waitingSeconds + 59) / 60))
        }
    }
    state.notice?.let { ErrorLine(noticeText(it)) }
}

@Composable
private fun BatterySlider(
    percent: Int,
    onPercent: (Int) -> Unit,
) {
    var draft by remember(percent) { mutableFloatStateOf(percent.toFloat()) }
    val label = stringResource(R.string.phone_transcription_battery_format, draft.toInt())
    Text(label, style = MaterialTheme.typography.bodyLarge)
    Slider(
        value = draft,
        onValueChange = { draft = it },
        onValueChangeFinished = { onPercent(draft.toInt()) },
        valueRange = BATTERY_MIN.toFloat()..BATTERY_MAX.toFloat(),
        steps = (BATTERY_MAX - BATTERY_MIN) / BATTERY_STEP - 1,
        modifier = Modifier.semantics { contentDescription = label },
    )
}

@Composable
private fun ModelBlock(
    state: PhoneTranscriptionUiState,
    onPick: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    state.model?.let { model ->
        SettingsRow(model.name, supporting = Formatter.formatShortFileSize(context, model.bytes), mono = true)
    }
    val copied = state.importedBytes
    if (copied != null) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        SettingsNote(
            stringResource(R.string.phone_transcription_copying_format, Formatter.formatShortFileSize(context, copied)),
        )
    } else {
        SettingsBlock(null) {
            FilledTonalButton(onClick = onPick) { Text(stringResource(R.string.phone_transcription_pick)) }
            if (state.model != null) {
                OutlinedButton(onClick = onDelete) { Text(stringResource(R.string.phone_transcription_delete_model)) }
            }
        }
    }
    SettingsNote(stringResource(R.string.phone_transcription_model_help))
}

@Composable
private fun DraftsBlock(
    drafts: List<DraftTextRow>,
    onDelete: () -> Unit,
) {
    if (drafts.isEmpty()) {
        SettingsNote(stringResource(R.string.phone_transcription_no_drafts))
        return
    }
    val format = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        drafts.forEach { draft ->
            Column {
                Text(
                    format.format(Instant.ofEpochMilli(draft.startMs)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(draft.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    OutlinedButton(onClick = onDelete) { Text(stringResource(R.string.phone_transcription_delete_drafts)) }
    SettingsNote(stringResource(R.string.phone_transcription_drafts_note))
}

@Composable
private fun BenchmarkBlock(
    state: PhoneTranscriptionUiState,
    onRun: () -> Unit,
) {
    SettingsNote(stringResource(R.string.phone_transcription_benchmark_note))
    if (state.benchmarkRunning) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    } else {
        FilledTonalButton(onClick = onRun) { Text(stringResource(R.string.phone_transcription_benchmark_run)) }
    }
    state.benchmark?.let { BenchmarkLines(it) }
}

@Composable
private fun BenchmarkLines(result: BenchmarkResult) {
    val battery =
        result.batteryPercentPerHour?.let {
            String.format(Locale.ROOT, "%.1f %%/h, %.1f %% per hour of speech", it, result.batteryPercentPerSpeechHour)
        } ?: stringResource(R.string.phone_transcription_benchmark_no_battery)
    val lines =
        listOf(
            "Model" to result.model,
            "Real-time factor" to String.format(Locale.ROOT, "%.3f", result.realTimeFactor),
            "Load" to String.format(Locale.ROOT, "%.1f s", result.loadMs / 1000.0),
            "Audio" to String.format(Locale.ROOT, "%.0f s in %.1f s", result.audioMs / 1000.0, result.workMs / 1000.0),
            "Memory" to "${result.peakRssMb} MB peak, ${result.idleRssMb} MB before",
            "Battery" to battery,
            "Word error rate" to String.format(Locale.ROOT, "%.0f %%", result.wordErrorRate * 100),
        )
    lines.forEach { (label, value) -> SettingsRow(label, value = value, mono = true) }
    SettingsNote(result.text)
}

@Composable
private fun ImportDialog(
    import: ModelImport,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val size = Formatter.formatShortFileSize(context, import.bytes)
    val free = Formatter.formatShortFileSize(context, import.freeBytes)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.phone_transcription_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.phone_transcription_import_text_format, import.name, size, free))
                if (!import.fits) ErrorLine(stringResource(R.string.phone_transcription_no_room))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = import.fits) {
                Text(stringResource(R.string.phone_transcription_import_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun noticeText(notice: TranscriptionNotice): String =
    stringResource(
        when (notice) {
            TranscriptionNotice.NoModelInFolder -> R.string.phone_transcription_no_model_in_folder
            TranscriptionNotice.NoRoom -> R.string.phone_transcription_no_room
            TranscriptionNotice.ImportFailed -> R.string.phone_transcription_import_failed
            TranscriptionNotice.BenchmarkFailed -> R.string.phone_transcription_benchmark_failed
        },
    )
