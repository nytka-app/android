package io.github.nytka_app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nytka_app.R
import io.github.nytka_app.capture.CaptureState
import io.github.nytka_app.pendant.PendantConnection
import io.github.nytka_app.ui.conversations.TranscriptionLag

/** The capture strip with the pendant, server and queue lines under it; the card rows below use the same surface. */
@Composable
fun StatusCard(
    state: StatusUiState,
    onMute: (Boolean) -> Unit,
    transcriptionLag: TranscriptionLag? = null,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CaptureStatusIndicator(
                CaptureState.of(state.capture, state.muted),
                style = CaptureStatusStyle.Strip,
            )
            (state.capture.connection as? PendantConnection.Refused)?.let { Problem(it.reason) }
            Line(
                stringResource(
                    R.string.pendant_battery_format,
                    state.capture.battery?.let { "$it%" } ?: stringResource(R.string.unknown),
                ),
            )
            Line(stringResource(R.string.server_line_format, state.serverLine))
            transcriptionLag?.let {
                Problem(
                    pluralStringResource(R.plurals.server_behind_format, it.chunks.toInt(), it.chunks, it.since),
                    stringResource(R.string.server_behind_safe),
                )
            }
            LocalNetworkHint(state.serverUnreachable)
            Line(stringResource(R.string.queued_chunks_format, state.queuedChunks))
            FilledTonalButton(onClick = { onMute(!state.muted) }) {
                Text(stringResource(if (state.muted) R.string.unmute else R.string.mute))
            }
        }
    }
}

@Composable
private fun Line(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** What went wrong, with the triangle and, when known, what is safe: a problem is never shown by colour alone. */
@Composable
private fun Problem(
    what: String,
    safe: String? = null,
) {
    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Column {
            Text(what, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            safe?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
