package io.github.nytka_app.ui.device

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.nytka_app.R
import io.github.nytka_app.ui.SettingsBlock
import io.github.nytka_app.ui.SettingsToggleRow

/** "Match wallpaper colours", off by default: recording, decisions and failures keep their colours either way. */
@Composable
internal fun AppearanceSection(
    on: Boolean,
    onSwitch: (Boolean) -> Unit,
) {
    SettingsBlock(stringResource(R.string.appearance)) {
        SettingsToggleRow(stringResource(R.string.wallpaper_colors_label), checked = on, onCheckedChange = onSwitch)
        Text(stringResource(R.string.wallpaper_colors_explanation), style = MaterialTheme.typography.bodySmall)
    }
}
