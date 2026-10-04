package io.github.nytka_app.ui.voice

import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.core.api.EnrollMode
import io.github.nytka_app.core.api.EnrollRefusal
import io.github.nytka_app.core.api.VoiceStatus
import io.github.nytka_app.core.chunks.EnrollmentRecording
import io.github.nytka_app.ui.conversations.Formatting
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/** Voice enrollment, reached from the Device tab when the server lists `voice` under `features`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen(
    onBack: () -> Unit,
    viewModel: VoiceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    // A reading never outlives the screen: in the background the pendant's audio goes to the queue again.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) viewModel.cancel()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.voice_title)) },
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
            item { StatusCard(state, viewModel::refresh) }
            if (state.status?.modelAvailable != false && state.error == null && !state.loading) {
                if (state.phase == VoicePhase.Recording) {
                    item { RecordingCard(state, viewModel::send, viewModel::cancel) }
                } else {
                    item { StartCard(state, viewModel) }
                }
            }
        }
    }

    if (state.confirmForget) {
        AlertDialog(
            onDismissRequest = viewModel::dismissForget,
            title = { Text(stringResource(R.string.voice_forget_title)) },
            text = { Text(stringResource(R.string.voice_forget_text)) },
            confirmButton = {
                TextButton(onClick = viewModel::forget) { Text(stringResource(R.string.voice_forget_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissForget) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun StatusCard(
    state: VoiceUiState,
    onRetry: () -> Unit,
) = Card {
    val status = state.status
    when {
        state.error != null -> {
            Text(state.error, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }

        status == null -> Text(stringResource(R.string.voice_loading))
        !status.modelAvailable -> Text(stringResource(R.string.voice_no_model))
        !status.enrolled -> Text(stringResource(R.string.voice_not_enrolled))
        else -> EnrolledStatus(status)
    }
}

@Composable
private fun EnrolledStatus(status: VoiceStatus) {
    status.enrolledAt?.let { Text(stringResource(R.string.voice_enrolled_at, day(it))) }
    Text(stringResource(R.string.voice_samples, status.enrolledSamples))
    if (status.learnedSegments > 0) {
        Text(pluralStringResource(R.plurals.voice_learned, status.learnedSegments, status.learnedSegments))
    }
    status.updatedAt?.takeIf { it != status.enrolledAt }?.let {
        Text(stringResource(R.string.voice_updated_at, day(it)), style = MaterialTheme.typography.bodySmall)
    }
}

private fun day(text: String): String =
    Formatting.parse(text)?.let {
        DateTimeFormatter
            .ofLocalizedDateTime(
                FormatStyle.MEDIUM,
                FormatStyle.SHORT,
            ).format(it.atZone(ZoneId.systemDefault()))
    } ?: text

@Composable
private fun StartCard(
    state: VoiceUiState,
    viewModel: VoiceViewModel,
) = Card {
    val idle = state.phase == VoicePhase.Idle
    val enrolled = state.status?.enrolled == true
    Text(stringResource(R.string.voice_languages), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PromptLanguage.entries.forEach { language ->
            FilterChip(
                selected = language in state.languages,
                onClick = { viewModel.toggleLanguage(language) },
                label = { Text(stringResource(language.label)) },
                enabled = idle,
            )
        }
    }
    Text(stringResource(R.string.voice_how), style = MaterialTheme.typography.bodySmall)
    if (!state.pendantReady) {
        Text(
            stringResource(R.string.voice_pendant_silent),
            color = MaterialTheme.colorScheme.error,
        )
    }
    val canStart = idle && state.languages.isNotEmpty()
    if (state.phase == VoicePhase.Sending) Text(stringResource(R.string.voice_sending))
    // The result stays until the next action: a refusal says what to do differently.
    state.notice?.let { notice ->
        Text(
            noticeText(LocalContext.current, notice),
            color = if (notice.isProblem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
    if (enrolled) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.start(EnrollMode.Replace) }, enabled = canStart) {
                Text(stringResource(R.string.voice_reenroll))
            }
            OutlinedButton(onClick = { viewModel.start(EnrollMode.Add) }, enabled = canStart) {
                Text(stringResource(R.string.voice_add_more))
            }
        }
        Text(stringResource(R.string.voice_add_more_hint), style = MaterialTheme.typography.bodySmall)
        if ((state.status?.learnedSegments ?: 0) > 0) {
            TextButton(onClick = viewModel::reset, enabled = idle) { Text(stringResource(R.string.voice_reset)) }
        }
        TextButton(onClick = viewModel::askForget, enabled = idle) { Text(stringResource(R.string.voice_forget)) }
    } else {
        Button(onClick = { viewModel.start(EnrollMode.Replace) }, enabled = canStart) {
            Text(stringResource(R.string.voice_enroll))
        }
    }
}

@Composable
private fun RecordingCard(
    state: VoiceUiState,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) = Card {
    Text(stringResource(R.string.voice_read_aloud), style = MaterialTheme.typography.titleMedium)
    PromptLanguage.entries.filter { it in state.languages }.forEach { language ->
        Text(stringResource(language.label), style = MaterialTheme.typography.labelLarge)
        stringArrayResource(language.prompts).forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
    }
    Text(stringResource(R.string.voice_level), style = MaterialTheme.typography.labelMedium)
    LinearProgressIndicator(progress = { state.level }, modifier = Modifier.fillMaxWidth())
    Text(stringResource(R.string.voice_elapsed, state.seconds.roundToInt(), EnrollmentRecording.MAX_SECONDS))
    LinearProgressIndicator(
        progress = { (state.seconds / EnrollmentRecording.MAX_SECONDS).toFloat() },
        modifier = Modifier.fillMaxWidth(),
    )
    when {
        state.full -> Text(stringResource(R.string.voice_full))
        !state.pendantReady ->
            Text(
                stringResource(R.string.voice_pendant_silent),
                color = MaterialTheme.colorScheme.error,
            )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onSend, enabled = state.seconds > 0) { Text(stringResource(R.string.voice_send)) }
        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
    }
}

@get:StringRes
private val PromptLanguage.label: Int
    get() =
        when (this) {
            PromptLanguage.Ukrainian -> R.string.voice_language_uk
            PromptLanguage.Russian -> R.string.voice_language_ru
            PromptLanguage.English -> R.string.voice_language_en
        }

@get:ArrayRes
private val PromptLanguage.prompts: Int
    get() =
        when (this) {
            PromptLanguage.Ukrainian -> R.array.voice_prompts_uk
            PromptLanguage.Russian -> R.array.voice_prompts_ru
            PromptLanguage.English -> R.array.voice_prompts_en
        }

private val VoiceNotice.isProblem: Boolean
    get() = this !is VoiceNotice.Enrolled && this !is VoiceNotice.Forgotten && this !is VoiceNotice.Reset

private fun noticeText(
    context: Context,
    notice: VoiceNotice,
): String =
    when (notice) {
        is VoiceNotice.Enrolled ->
            context.getString(
                R.string.voice_done,
                seconds(context, notice.speechSeconds),
                context.resources.getQuantityString(R.plurals.voice_sample_count, notice.samples, notice.samples),
            )

        is VoiceNotice.Refused -> refusalText(context, notice)
        VoiceNotice.TooLong -> context.getString(R.string.voice_too_long)
        VoiceNotice.Unreadable -> context.getString(R.string.voice_unreadable)
        VoiceNotice.OtherModel -> context.getString(R.string.voice_other_model)
        VoiceNotice.NoModel -> context.getString(R.string.voice_no_model)
        VoiceNotice.NotRecording -> context.getString(R.string.voice_not_recording)
        VoiceNotice.NothingRecorded -> context.getString(R.string.voice_nothing_recorded)
        VoiceNotice.Forgotten -> context.getString(R.string.voice_forgotten)
        VoiceNotice.Reset -> context.getString(R.string.voice_reset_done)
        is VoiceNotice.Failed -> notice.message
    }

private fun refusalText(
    context: Context,
    notice: VoiceNotice.Refused,
): String =
    when (notice.refusal) {
        EnrollRefusal.TooLittleSpeech ->
            context.getString(
                R.string.voice_too_little_speech,
                seconds(context, notice.speechSeconds),
            )
        EnrollRefusal.TooFewSamples -> context.getString(R.string.voice_too_few_samples)
        EnrollRefusal.SamplesDisagree -> context.getString(R.string.voice_samples_disagree)
        null -> context.getString(R.string.voice_refused)
    }

private fun seconds(
    context: Context,
    value: Double,
): String = value.roundToInt().let { context.resources.getQuantityString(R.plurals.voice_speech_seconds, it, it) }
