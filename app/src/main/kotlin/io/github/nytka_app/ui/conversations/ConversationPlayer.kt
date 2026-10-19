package io.github.nytka_app.ui.conversations

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import io.github.nytka_app.R

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
    PlayerDock(
        playback,
        speaker = heard?.let { paragraphs.getOrNull(it) }?.let { speakerName(it) },
        turns = turns,
        follow = follow,
        onFollow = onFollow,
        onToggle = viewModel::togglePlay,
        onSeek = viewModel::seekTo,
        onSkip = viewModel::skip,
        onSpeed = viewModel::cycleSpeed,
    )
}

/** What the player calls the speaker now: "You" for the wearer, else the name or the transcription's label. */
@Composable
internal fun speakerName(paragraph: Paragraph): String? =
    if (paragraph.isUser == true) stringResource(R.string.speaker_you) else paragraph.speaker

/** A voice with no person behind it: the wearer's lines and named people are not. */
internal val Paragraph.unknownVoice: Boolean get() = isUser != true && personId == null && speaker != null
