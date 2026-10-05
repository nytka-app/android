package io.github.nytka_app.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nytka_app.R

/** The Device tab's card for what the pendant stored while the phone was away. */
@Composable
fun PendantStorageCard(
    card: StorageCard,
    onSyncNow: () -> Unit,
    onStop: () -> Unit,
    onAnswerBacklog: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.pendant_storage), style = MaterialTheme.typography.titleMedium)
            Text(card.state)
            card.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
            card.details.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            when (card.action) {
                StorageAction.None -> Unit
                StorageAction.SyncNow ->
                    FilledTonalButton(onClick = onSyncNow) { Text(stringResource(R.string.sync_now)) }
                StorageAction.Stop -> OutlinedButton(onClick = onStop) { Text(stringResource(R.string.action_stop)) }
                StorageAction.AnswerBacklog ->
                    FilledTonalButton(
                        onClick = onAnswerBacklog,
                    ) { Text(stringResource(R.string.import_or_discard)) }
            }
        }
    }
}

/**
 * The first-sync question: the pendant holds a lot ([packets]) that this phone never read. Import is the default; a
 * dismissed dialog leaves the question open, and the card's button asks again.
 */
@Composable
fun BacklogDialog(
    packets: Long,
    dismissed: Boolean,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
    onDiscard: () -> Unit,
) {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val duration = packetsToDuration(packets)
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.discard_audio_title)) },
            text = {
                Text(stringResource(R.string.discard_audio_message_format, duration))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onDiscard()
                }) { Text(stringResource(R.string.action_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.action_back)) }
            },
        )
    } else if (!dismissed) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.import_audio_title)) },
            text = {
                Text(stringResource(R.string.import_audio_message_format, duration))
            },
            confirmButton = { TextButton(onClick = onImport) { Text(stringResource(R.string.action_import)) } },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = true }) {
                    Text(stringResource(R.string.action_discard_ellipsis))
                }
            },
        )
    }
}
