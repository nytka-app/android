package io.github.nytka_app.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nytka_app.R
import io.github.nytka_app.ui.NytkaSwitch

/** "Match wallpaper colours", off by default: recording, decisions and failures keep their colours either way. */
@Composable
internal fun AppearanceSection(
    on: Boolean,
    onSwitch: (Boolean) -> Unit,
) {
    Section(stringResource(R.string.appearance)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NytkaSwitch(checked = on, onCheckedChange = onSwitch)
            Text(stringResource(R.string.wallpaper_colors_label))
        }
        Text(stringResource(R.string.wallpaper_colors_explanation), style = MaterialTheme.typography.bodySmall)
    }
}
