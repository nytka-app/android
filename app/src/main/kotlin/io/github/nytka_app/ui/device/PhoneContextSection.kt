package io.github.nytka_app.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nytka_app.R

/** The Device tab's switch for phone context: times of speaker playback and calls, never what played. */
@Composable
internal fun PhoneContextSection(
    on: Boolean,
    onSwitch: (Boolean) -> Unit,
) {
    Section(stringResource(R.string.phone_context_title)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = on, onCheckedChange = onSwitch)
            Text(stringResource(R.string.phone_context_label))
        }
        Text(stringResource(R.string.phone_context_explanation), style = MaterialTheme.typography.bodySmall)
    }
}
