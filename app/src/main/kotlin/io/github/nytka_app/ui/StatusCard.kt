package io.github.nytka_app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nytka_app.R
import io.github.nytka_app.pendant.PendantConnection

@Composable
fun StatusCard(
    state: StatusUiState,
    onMute: (Boolean) -> Unit,
    transcriptionNotice: String? = null,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val headline =
                when {
                    state.muted -> stringResource(R.string.muted)
                    state.capture.recording -> stringResource(R.string.recording)
                    else -> stringResource(R.string.not_recording)
                }
            Text(headline, style = MaterialTheme.typography.titleMedium)
            (state.capture.connection as? PendantConnection.Refused)?.let {
                Text(it.reason, color = MaterialTheme.colorScheme.error)
            }
            Text(
                stringResource(
                    R.string.pendant_battery_format,
                    state.capture.battery?.let { "$it%" } ?: stringResource(R.string.unknown),
                ),
            )
            Text(stringResource(R.string.server_line_format, state.serverLine))
            transcriptionNotice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LocalNetworkHint(state.serverUnreachable)
            Text(stringResource(R.string.queued_chunks_format, state.queuedChunks))
            FilledTonalButton(onClick = { onMute(!state.muted) }) {
                Text(stringResource(if (state.muted) R.string.unmute else R.string.mute))
            }
        }
    }
}
