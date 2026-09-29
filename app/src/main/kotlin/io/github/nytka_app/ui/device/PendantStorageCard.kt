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
import androidx.compose.ui.unit.dp

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
            Text("Pendant storage", style = MaterialTheme.typography.titleMedium)
            Text(card.state)
            card.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
            card.details.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            when (card.action) {
                StorageAction.None -> Unit
                StorageAction.SyncNow -> FilledTonalButton(onClick = onSyncNow) { Text("Sync now") }
                StorageAction.Stop -> OutlinedButton(onClick = onStop) { Text("Stop") }
                StorageAction.AnswerBacklog ->
                    FilledTonalButton(
                        onClick = onAnswerBacklog,
                    ) { Text("Import or discard…") }
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
            title = { Text("Discard the stored audio?") },
            text = {
                Text(
                    "About $duration of audio is freed on the pendant without being read. It cannot be recovered.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onDiscard()
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Back") } },
        )
    } else if (!dismissed) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Import the stored audio?") },
            text = {
                Text(
                    "The pendant holds about $duration of audio it recorded while the phone was away. Importing " +
                        "reads it all and uploads it to your server, which takes a while.",
                )
            },
            confirmButton = { TextButton(onClick = onImport) { Text("Import") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = true }) { Text("Discard…") } },
        )
    }
}
