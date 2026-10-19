package io.github.nytka_app.ui.device

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.nytka_app.R
import io.github.nytka_app.ui.SettingsBlock
import io.github.nytka_app.ui.SettingsToggleRow

/** The Device tab's switch for phone context: times of speaker playback and calls, never what played. */
@Composable
internal fun PhoneContextSection(
    on: Boolean,
    onSwitch: (Boolean) -> Unit,
) {
    SettingsBlock(stringResource(R.string.phone_context_title)) {
        SettingsToggleRow(stringResource(R.string.phone_context_label), checked = on, onCheckedChange = onSwitch)
        Text(stringResource(R.string.phone_context_explanation), style = MaterialTheme.typography.bodySmall)
    }
}
