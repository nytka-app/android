package io.github.nytka_app.ui.conversations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.nytka_app.R
import io.github.nytka_app.ui.theme.ReadingMaxWidth

/** Which paragraph is being heard: the last that starts at or before [positionMs]; null without a position map. */
internal fun playingParagraph(
    startsMs: List<Long>,
    positionMs: Long,
): Int? = startsMs.indexOfLast { it <= positionMs }.takeIf { it >= 0 }

/** Where the speaker changes along the track, as fractions of [durationMs]. */
internal fun turnPositions(
    paragraphs: List<Paragraph>,
    startsMs: List<Long>,
    durationMs: Long,
): List<Float> {
    if (durationMs <= 0) return emptyList()
    return (1 until minOf(paragraphs.size, startsMs.size))
        .filter { paragraphs[it].speaker != null && paragraphs[it].speaker != paragraphs[it - 1].speaker }
        .map { (startsMs[it].toFloat() / durationMs).coerceIn(0f, 1f) }
        .distinct()
}

/**
 * The player docked at the bottom of the conversation: a plain track with a tick where the speaker changes, the time
 * and who talks now, then follow, back 10 s, play, forward 10 s and speed. When the audio fails it says so in one line
 * and stays.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerDock(
    playback: Playback,
    speaker: String?,
    turns: List<Float>,
    follow: Boolean,
    onFollow: () -> Unit,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onSpeed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = (dragging ?: playback.positionMs.toFloat()).coerceIn(0f, playback.durationMs.toFloat())
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val activeColor = MaterialTheme.colorScheme.primary
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(Modifier.navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = ReadingMaxWidth).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Slider(
                    value = shown,
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { onSeek(it.toLong()) }
                        dragging = null
                    },
                    valueRange = 0f..playback.durationMs.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(activeTrackColor = activeColor, thumbColor = activeColor),
                    track = { state ->
                        val span = state.valueRange.endInclusive - state.valueRange.start
                        val fraction = if (span > 0f) (state.value - state.valueRange.start) / span else 0f
                        Canvas(Modifier.fillMaxWidth().height(TRACK_HEIGHT)) {
                            val line = 4.dp.toPx()
                            val top = (size.height - line) / 2
                            val corner = CornerRadius(line / 2)
                            drawRoundRect(trackColor, Offset(0f, top), Size(size.width, line), corner)
                            drawRoundRect(activeColor, Offset(0f, top), Size(size.width * fraction, line), corner)
                            val tick = 2.dp.toPx()
                            turns.forEach { at ->
                                drawRect(
                                    tickColor,
                                    Offset(size.width * at - tick / 2, size.height * 0.2f),
                                    Size(tick, size.height * 0.6f),
                                )
                            }
                        }
                    },
                )
                if (playback.failed) {
                    Text(
                        stringResource(R.string.audio_unreachable),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${Formatting.position(shown.toLong())} / ${Formatting.position(playback.durationMs)}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        speaker?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FilterChip(
                        selected = follow,
                        onClick = onFollow,
                        label = { Text(stringResource(R.string.audio_follow)) },
                    )
                    val back = stringResource(R.string.audio_back_10)
                    TextButton(
                        onClick = { onSkip(-SKIP_MS) },
                        modifier = Modifier.semantics { contentDescription = back },
                    ) {
                        Text(stringResource(R.string.audio_back_10_label))
                    }
                    FilledIconButton(
                        onClick = onToggle,
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        colors =
                            IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                    ) {
                        PlayPauseGlyph(
                            playing = playback.playing,
                            color = MaterialTheme.colorScheme.onPrimary,
                            size = 24.dp,
                            description = stringResource(if (playback.playing) R.string.pause else R.string.play),
                        )
                    }
                    val forward = stringResource(R.string.audio_forward_10)
                    TextButton(
                        onClick = { onSkip(SKIP_MS) },
                        modifier =
                            Modifier.semantics {
                                contentDescription =
                                    forward
                            },
                    ) {
                        Text(stringResource(R.string.audio_forward_10_label))
                    }
                    TextButton(
                        onClick = onSpeed,
                    ) { Text(stringResource(R.string.audio_speed_format, speedText(playback.speed))) }
                }
            }
        }
    }
}

private fun speedText(speed: Float): String = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()

/** A play triangle or a pause pair, drawn so the app needs no icon set for it. */
@Composable
fun PlayPauseGlyph(
    playing: Boolean,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Canvas(
        modifier
            .size(size)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) {
        if (playing) {
            val bar = this.size.width * 0.28f
            val height = this.size.height * 0.8f
            val top = (this.size.height - height) / 2
            drawRoundRect(color, Offset(this.size.width * 0.12f, top), Size(bar, height), CornerRadius(bar * 0.2f))
            drawRoundRect(color, Offset(this.size.width * 0.6f, top), Size(bar, height), CornerRadius(bar * 0.2f))
        } else {
            drawPath(
                Path().apply {
                    moveTo(this@Canvas.size.width * 0.2f, this@Canvas.size.height * 0.1f)
                    lineTo(this@Canvas.size.width * 0.9f, this@Canvas.size.height * 0.5f)
                    lineTo(this@Canvas.size.width * 0.2f, this@Canvas.size.height * 0.9f)
                    close()
                },
                color,
            )
        }
    }
}

private val TRACK_HEIGHT = 20.dp
private const val SKIP_MS = 10_000L
