package io.github.nytka_app.ui.voice

import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
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
import io.github.nytka_app.ui.ErrorLine
import io.github.nytka_app.ui.conversations.Formatting
import io.github.nytka_app.ui.theme.NytkaTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.ceil
import kotlin.math.roundToInt

/** The shortest reading the screen lets you send; the server needs 20 s of speech. */
private const val MIN_SECONDS = 30

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
private fun VoiceCard(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun StatusCard(
    state: VoiceUiState,
    onRetry: () -> Unit,
) = VoiceCard {
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
) = VoiceCard {
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
) = VoiceCard {
    val context = LocalContext.current
    val sentences =
        PromptLanguage.entries
            .filter { it in state.languages }
            .flatMap { language ->
                context.resources.getStringArray(language.prompts).map { language to it }
            }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val remaining = ceil(MIN_SECONDS - state.seconds).toInt()
    val enough = remaining <= 0
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(if (state.pendantReady) R.drawable.ic_capture_recording else R.drawable.ic_capture_waiting),
            contentDescription = null,
            tint = if (state.pendantReady) NytkaTheme.colors.capture else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            stringResource(R.string.voice_listening),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    sentences.getOrNull(index % sentences.size.coerceAtLeast(1))?.let { (language, sentence) ->
        Text(stringResource(language.label), style = MaterialTheme.typography.labelLarge)
        Text(sentence, style = NytkaTheme.typography.readingTitle)
        if (sentences.size > 1) {
            TextButton(onClick = { index = (index + 1) % sentences.size }) {
                Text(stringResource(R.string.voice_next_sentence))
            }
        }
    }
    Text(
        "%d:%02d".format(state.seconds.toInt() / 60, state.seconds.toInt() % 60),
        style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
    )
    EnrollmentTrack(state.seconds)
    Text(
        if (enough) {
            stringResource(R.string.voice_enough)
        } else {
            pluralStringResource(R.plurals.voice_need_more, remaining, remaining)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    when {
        state.full -> Text(stringResource(R.string.voice_full))
        !state.pendantReady -> ErrorLine(stringResource(R.string.voice_pendant_silent))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onSend, enabled = enough) { Text(stringResource(R.string.voice_send)) }
        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
    }
}

/** An 8dp track filling toward the 120 s goal, with a 2dp marker at the 30 s minimum. */
@Composable
private fun EnrollmentTrack(seconds: Double) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val fill = MaterialTheme.colorScheme.primary
    val marker = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxWidth().height(12.dp)) {
        val bar = 8.dp.toPx()
        val top = (size.height - bar) / 2
        val round = CornerRadius(bar / 2)
        drawRoundRect(track, Offset(0f, top), Size(size.width, bar), round)
        val done = (seconds / EnrollmentRecording.MAX_SECONDS).toFloat().coerceIn(0f, 1f)
        if (done > 0f) drawRoundRect(fill, Offset(0f, top), Size(size.width * done, bar), round)
        val x = size.width * MIN_SECONDS / EnrollmentRecording.MAX_SECONDS
        drawRect(marker, Offset(x - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
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
