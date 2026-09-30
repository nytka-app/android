package io.github.nytka_app.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nytka_app.capture.PendantSettingsState
import io.github.nytka_app.pendant.LedDim
import io.github.nytka_app.pendant.MicGain

/** The words for the LED slider: the value in percent, and a warning at 0 (the status lights go dark). */
fun ledLabel(percent: Int): String = "$percent %"

fun ledWarning(percent: Int): String? =
    if (LedDim.hidesStatus(percent)) {
        "At 0 the pendant's link and charging lights stay dark, so you cannot see its state."
    } else {
        null
    }

/** The words for the gain slider: the level and its gain in dB, or "muted" at level 0. */
fun gainLabel(level: Int): String =
    MicGain.decibels(level)?.let { "Level $level of ${MicGain.MAX}, ${signed(it)} dB" } ?: "Level 0, muted"

fun gainWarning(level: Int): String? =
    if (MicGain.isMute(level)) "At level 0 the pendant records silence, so Nytka has nothing to transcribe." else null

private fun signed(decibels: Int) = if (decibels > 0) "+$decibels" else decibels.toString()

/** The Device tab's card for the pendant's LED brightness and microphone gain. */
@Composable
fun PendantSettingsCard(
    card: PendantSettingsState,
    onLed: (Int) -> Unit,
    onGain: (Int) -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Pendant settings", style = MaterialTheme.typography.titleMedium)
            card.led?.let {
                SettingSlider(
                    title = "LED brightness",
                    value = it,
                    range = LedDim.MIN..LedDim.MAX,
                    steps = 0,
                    label = ::ledLabel,
                    warning = ::ledWarning,
                    failures = card.failures,
                    onCommit = onLed,
                )
            }
            card.gain?.let {
                SettingSlider(
                    title = "Microphone gain",
                    value = it,
                    range = MicGain.MIN..MicGain.MAX,
                    steps = MicGain.MAX - MicGain.MIN - 1,
                    label = ::gainLabel,
                    warning = ::gainWarning,
                    failures = card.failures,
                    onCommit = onGain,
                )
            }
            card.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text(
                "The pendant keeps these in its own memory; nothing is sent to your server.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** The thumb follows the finger; the pendant is written when it lets go, and a failure puts the thumb back. */
@Composable
private fun SettingSlider(
    title: String,
    value: Int,
    range: IntRange,
    steps: Int,
    label: (Int) -> String,
    warning: (Int) -> String?,
    failures: Int,
    onCommit: (Int) -> Unit,
) {
    var draft by remember(value, failures) { mutableFloatStateOf(value.toFloat()) }
    val shown = draft.toInt()
    Text("$title: ${label(shown)}")
    Slider(
        value = draft,
        onValueChange = { draft = it },
        onValueChangeFinished = { onCommit(draft.toInt()) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = steps,
    )
    warning(shown)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}
