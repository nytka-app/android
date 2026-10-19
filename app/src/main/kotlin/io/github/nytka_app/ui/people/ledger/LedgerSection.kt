package io.github.nytka_app.ui.people.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R

/**
 * What you owe this person and what they owe you, each with its open promises and, on a tap, its done ones. Not drawn
 * on a server without `ledger`. A promise with a conversation opens it through [onOpenConversation].
 */
@Composable
fun LedgerSection(
    onOpenConversation: (String) -> Unit,
    viewModel: LedgerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.available) return
    Column {
        Text(
            stringResource(R.string.person_ledger_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        state.error?.let { Note(it) }
        LedgerSide(stringResource(R.string.person_ledger_by_me), state.owedByMe, onOpenConversation)
        LedgerSide(stringResource(R.string.person_ledger_to_me), state.owedToMe, onOpenConversation)
    }
}

@Composable
private fun LedgerSide(
    title: String,
    side: LedgerSideState,
    onOpenConversation: (String) -> Unit,
) {
    var doneShown by rememberSaveable(title) { mutableStateOf(false) }
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    if (side.open.isEmpty()) Note(stringResource(R.string.person_ledger_none))
    side.open.forEach { LedgerRow(it, onOpenConversation) }
    if (side.done.isNotEmpty()) {
        TextButton(onClick = { doneShown = !doneShown }, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                if (doneShown) {
                    stringResource(R.string.person_ledger_hide_done)
                } else {
                    stringResource(R.string.person_ledger_done_format, side.done.size)
                },
            )
        }
        if (doneShown) side.done.forEach { LedgerRow(it, onOpenConversation, done = true) }
    }
}

@Composable
private fun LedgerRow(
    entry: LedgerEntry,
    onOpenConversation: (String) -> Unit,
    done: Boolean = false,
) {
    ListItem(
        headlineContent = {
            Text(
                entry.text,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = if (entry.source.isNotEmpty()) ({ Text(entry.source) }) else null,
        modifier = entry.conversationId?.let { id -> Modifier.clickable { onOpenConversation(id) } } ?: Modifier,
    )
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
}
