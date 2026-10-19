package io.github.nytka_app.ui

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.nytka_app.R
import io.github.nytka_app.capture.CaptureState
import io.github.nytka_app.ui.theme.NytkaPreviewTheme
import io.github.nytka_app.ui.theme.NytkaTheme

/**
 * The only recording indicator the phone shows for Nytka (the app never uses the microphone, so Android shows no
 * privacy dot). Each state has its own glyph and words; only Recording is green. TalkBack reads the sentence from a
 * polite live region when the state changes.
 */
@Composable
fun CaptureStatusIndicator(
    state: CaptureState,
    modifier: Modifier = Modifier,
    style: CaptureStatusStyle = CaptureStatusStyle.Pill,
    detail: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val word = stringResource(state.wordRes)
    val sentence = listOfNotNull(stringResource(state.sentenceRes), detail).joinToString(". ")
    val color by animateColorAsState(state.color(), tween(STATE_CHANGE_MS, easing = EaseOutCubic), "capture colour")
    val openLabel = stringResource(R.string.capture_open_device)
    val semantics =
        Modifier.clearAndSetSemantics {
            contentDescription = sentence
            liveRegion = LiveRegionMode.Polite
            if (onClick != null) {
                role = Role.Button
                onClick(openLabel) {
                    onClick()
                    true
                }
            }
        }
    val shape = if (style == CaptureStatusStyle.Pill) CircleShape else MaterialTheme.shapes.large
    val content: @Composable () -> Unit = {
        Row(
            Modifier
                .heightIn(min = if (style == CaptureStatusStyle.Pill) 40.dp else 56.dp)
                .padding(horizontal = if (style == CaptureStatusStyle.Pill) 12.dp else 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CaptureGlyph(state, color, Modifier.size(if (style == CaptureStatusStyle.Pill) 16.dp else 20.dp))
            Column {
                Text(
                    word,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (state == CaptureState.Recording) color else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (style == CaptureStatusStyle.Strip && detail != null) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    val container = MaterialTheme.colorScheme.surfaceContainer
    val outer = if (style == CaptureStatusStyle.Strip) modifier.fillMaxWidth() else modifier
    if (onClick != null) {
        // A pill is 40 dp tall; the minimum touch target keeps it at 48 dp to tap.
        Surface(
            onClick = onClick,
            modifier = outer.then(semantics),
            shape = shape,
            color = container,
            content = content,
        )
    } else {
        Surface(modifier = outer.then(semantics), shape = shape, color = container, content = content)
    }
}

/**
 * The line under the top bar: solid with a travelling stitch while recording, dashed while muted, dotted while
 * waiting, none when off. Its height stays, so the screen never jumps.
 */
@Composable
fun CaptureThread(
    state: CaptureState,
    modifier: Modifier = Modifier,
) {
    val color = state.color()
    val stitch by travellingStitch(running = state == CaptureState.Recording)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(THREAD_HEIGHT),
    ) {
        val y = size.height / 2
        val thin = 2.dp.toPx()
        when (state) {
            CaptureState.Recording -> {
                drawLine(color, Offset(0f, y), Offset(size.width, y), thin)
                val period = STITCH_PERIOD.toPx()
                val length = STITCH_LENGTH.toPx()
                var x = -period + stitch * period
                while (x < size.width) {
                    drawLine(color, Offset(x, y), Offset(x + length, y), size.height, StrokeCap.Round)
                    x += period
                }
            }
            CaptureState.Muted ->
                drawLine(
                    color,
                    Offset(0f, y),
                    Offset(size.width, y),
                    thin,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                )
            CaptureState.Waiting ->
                drawLine(
                    color,
                    Offset(0f, y),
                    Offset(size.width, y),
                    thin,
                    StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, 6.dp.toPx())),
                )
            CaptureState.Off -> Unit
        }
    }
}

/** Disc, two bars, a broken ring that turns once in 4 s, or a ring with a slash: Canvas, never a symbol font. */
@Composable
fun CaptureGlyph(
    state: CaptureState,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val turn by ringTurn(running = state == CaptureState.Waiting)
    Canvas(modifier) {
        val unit = size.minDimension / GLYPH_BOX
        when (state) {
            CaptureState.Recording -> drawCircle(color, radius = 7 * unit)
            CaptureState.Muted -> bars(color, unit)
            CaptureState.Waiting ->
                rotate(turn * FULL_TURN) {
                    repeat(ARCS) { i ->
                        drawArc(
                            color,
                            startAngle = ARC_START + i * QUARTER,
                            sweepAngle = ARC_SWEEP,
                            useCenter = false,
                            topLeft = Offset(center.x - 7.5f * unit, center.y - 7.5f * unit),
                            size = Size(15 * unit, 15 * unit),
                            style = Stroke(2.5f * unit, cap = StrokeCap.Round),
                        )
                    }
                }
            CaptureState.Off -> {
                drawCircle(color, radius = 7.5f * unit, style = Stroke(2 * unit))
                drawLine(
                    color,
                    Offset(5.5f * unit, 5.5f * unit),
                    Offset(18.5f * unit, 18.5f * unit),
                    2 * unit,
                    StrokeCap.Round,
                )
            }
        }
    }
}

private fun DrawScope.bars(
    color: Color,
    unit: Float,
) {
    listOf(7f, 13f).forEach { left ->
        drawRoundRect(
            color,
            topLeft = Offset(left * unit, 6 * unit),
            size = Size(4 * unit, 12 * unit),
            cornerRadius = CornerRadius(1.5f * unit),
        )
    }
}

@Composable
private fun CaptureState.color(): Color =
    when (this) {
        CaptureState.Recording -> NytkaTheme.colors.capture
        CaptureState.Muted -> MaterialTheme.colorScheme.onSurface
        CaptureState.Waiting, CaptureState.Off -> MaterialTheme.colorScheme.onSurfaceVariant
    }

private val CaptureState.sentenceRes: Int
    get() =
        when (this) {
            CaptureState.Recording -> R.string.capture_sentence_recording
            CaptureState.Muted -> R.string.capture_sentence_muted
            CaptureState.Waiting -> R.string.capture_sentence_waiting
            CaptureState.Off -> R.string.capture_sentence_off
        }

/** "Remove animations" sets the animator scale to 0: the thread and ring then stand still. */
@Composable
private fun animationsOn(): Boolean {
    val context = LocalContext.current
    val inPreview = LocalInspectionMode.current
    return remember(context) {
        !inPreview &&
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

@Composable
private fun travellingStitch(running: Boolean): State<Float> = endless(running, STITCH_MS)

@Composable
private fun ringTurn(running: Boolean): State<Float> = endless(running, RING_TURN_MS)

@Composable
private fun endless(
    running: Boolean,
    periodMs: Int,
): State<Float> {
    if (!running || !animationsOn()) return remember { mutableFloatStateOf(0f) }
    return rememberInfiniteTransition("capture motion").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart),
        label = "capture motion",
    )
}

private const val STATE_CHANGE_MS = 200
private const val STITCH_MS = 1_600
private const val RING_TURN_MS = 4_000
private const val GLYPH_BOX = 24f
private const val FULL_TURN = 360f
private const val QUARTER = 90f
private const val ARCS = 4
private const val ARC_START = 15f
private const val ARC_SWEEP = 60f
private val THREAD_HEIGHT: Dp = 4.dp
private val STITCH_PERIOD: Dp = 48.dp
private val STITCH_LENGTH: Dp = 12.dp

@PreviewLightDark
@Composable
private fun CaptureStatusPreview() {
    NytkaPreviewTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CaptureState.entries.forEach { state ->
                    CaptureStatusIndicator(state, onClick = {})
                    CaptureStatusIndicator(state, style = CaptureStatusStyle.Strip, detail = "Pendant 67% · 0 queued")
                    CaptureThread(state)
                }
            }
        }
    }
}
