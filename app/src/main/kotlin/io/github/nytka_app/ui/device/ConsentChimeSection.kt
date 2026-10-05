package io.github.nytka_app.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nytka_app.R

internal const val CHIME_MINUTES_MIN = 5
internal const val CHIME_MINUTES_MAX = 60

/** The Device tab's switch for the consent chime and its interval; the sound never uses the microphone. */
@Composable
internal fun ConsentChimeSection(
    on: Boolean,
    minutes: Int,
    onSwitch: (Boolean) -> Unit,
    onMinutes: (Int) -> Unit,
) {
    var draft by remember(minutes) { mutableFloatStateOf(minutes.toFloat()) }
    Section(stringResource(R.string.consent_chime_title)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = on, onCheckedChange = onSwitch)
            Text(stringResource(R.string.consent_chime_label))
        }
        if (on) {
            Text(stringResource(R.string.consent_chime_interval_format, draft.toInt()))
            Slider(
                value = draft,
                onValueChange = { draft = it },
                onValueChangeFinished = { onMinutes(draft.toInt()) },
                valueRange = CHIME_MINUTES_MIN.toFloat()..CHIME_MINUTES_MAX.toFloat(),
                steps = CHIME_MINUTES_MAX - CHIME_MINUTES_MIN - 1,
            )
        }
        Text(stringResource(R.string.consent_chime_explanation), style = MaterialTheme.typography.bodySmall)
    }
}
