package io.github.nytka_app.ui.conversations

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.stringResource
import io.github.nytka_app.R
import io.github.nytka_app.ui.names.VoiceNumbers
import kotlinx.coroutines.flow.first

/** The paragraph being heard; null before play starts and without a position map. */
internal fun Playback?.heardParagraph(): Int? =
    this?.takeIf { it.playing || it.positionMs > 0 }?.let { playingParagraph(paragraphStartsMs, positionMs) }

/** While [follow] holds, scrolls the heard paragraph into view when it is out of it. */
@Composable
internal fun FollowTranscript(
    listState: LazyListState,
    heard: Int?,
    follow: Boolean,
    paragraphCount: Int,
    hasRaw: Boolean,
) {
    // The paragraphs are the last items, before the raw transcription when it is shown.
    val first by remember {
        derivedStateOf { listState.layoutInfo.totalItemsCount - paragraphCount - if (hasRaw) 1 else 0 }
    }
    LaunchedEffect(heard, follow) {
        val at = heard ?: return@LaunchedEffect
        if (!follow) return@LaunchedEffect
        val index = first + at
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.animateScrollToItem(index)
    }
}

/** Scrolls the paragraph a link to a moment asked for ([focus]) to the top, once, and reports it shown. */
@Composable
internal fun ScrollToMoment(
    listState: LazyListState,
    focus: Int?,
    paragraphCount: Int,
    hasRaw: Boolean,
    onShown: () -> Unit,
) {
    LaunchedEffect(focus, paragraphCount) {
        val at = focus ?: return@LaunchedEffect
        if (paragraphCount == 0) return@LaunchedEffect
        // The list lays its items out after this first runs: wait until the paragraphs are among them.
        val total = snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it >= paragraphCount }
        val first = total - paragraphCount - if (hasRaw) 1 else 0
        listState.scrollToItem((first + at).coerceAtLeast(0))
        onShown()
    }
}

/** The docked player, when there is audio: who is heard now, the speaker ticks and the controls of [viewModel]. */
@Composable
internal fun ConversationPlayerBar(
    playback: Playback?,
    paragraphs: List<Paragraph>,
    heard: Int?,
    follow: Boolean,
    onFollow: () -> Unit,
    viewModel: ConversationViewModel,
) {
    playback ?: return
    val turns =
        remember(paragraphs, playback.paragraphStartsMs, playback.durationMs) {
            turnPositions(paragraphs, playback.paragraphStartsMs, playback.durationMs)
        }
    val voices = remember(paragraphs) { paragraphs.voiceNumbers() }
    PlayerDock(
        playback,
        speaker = heard?.let { paragraphs.getOrNull(it) }?.let { speakerName(it, voices) },
        turns = turns,
        follow = follow,
        onFollow = onFollow,
        onToggle = viewModel::togglePlay,
        onSeek = viewModel::seekTo,
        onSkip = viewModel::skip,
        onSpeed = viewModel::cycleSpeed,
    )
}

/** What the player calls the speaker now, as the transcript does: "You", the person's name, or "Voice n". */
@Composable
internal fun speakerName(
    paragraph: Paragraph,
    voices: VoiceNumbers,
): String? =
    when {
        paragraph.isUser == true -> stringResource(R.string.speaker_you)
        paragraph.unknownVoice -> stringResource(R.string.speaker_voice_format, voices.of(paragraph.speaker.orEmpty()))
        else -> paragraph.speaker
    }

/** The numbers of the voices nobody has named in [this]. */
internal fun List<Paragraph>.voiceNumbers(): VoiceNumbers =
    VoiceNumbers(
        filter {
            it.unknownVoice
        }.mapNotNull { it.speaker },
    )

/** A voice with no person behind it: the wearer's lines and named people are not. */
internal val Paragraph.unknownVoice: Boolean get() = isUser != true && personId == null && speaker != null
