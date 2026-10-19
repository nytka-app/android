package io.github.nytka_app.ui.memories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nytka_app.R
import io.github.nytka_app.ui.theme.NytkaTheme

/**
 * A lasting fact and where it came from: [text] in the reading type, [source] as a link to the conversation
 * when [onOpenSource] is given. Forgetting is the trash icon; the screen then offers Undo with
 * [UndoForgetEffect]; the icons' descriptions name the fact.
 */
@Composable
fun MemoryItem(
    text: String,
    source: String?,
    onOpenSource: (() -> Unit)?,
    onEdit: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                Text(text, style = NytkaTheme.typography.readingBody)
                if (source != null) SourceLink(source, onOpenSource)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.memory_edit_named_format, text))
            }
            IconButton(onClick = onForget) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.memory_forget_named_format, text),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SourceLink(
    source: String,
    onOpen: (() -> Unit)?,
) {
    val link = onOpen != null
    Text(
        source,
        style = MaterialTheme.typography.bodySmall,
        color = if (link) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        textDecoration = if (link) TextDecoration.Underline else null,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .heightIn(min = 48.dp)
                .padding(end = 8.dp)
                .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
                .padding(vertical = 14.dp),
    )
}

/**
 * Shows "forgotten, Undo" while [pendingKey] is set. [onUndo] when the action is tapped, [onCommit] when the
 * snackbar goes away or this leaves the screen, so the server is asked only once nobody can take it back.
 */
@Composable
fun UndoForgetEffect(
    pendingKey: String?,
    message: String,
    snackbar: SnackbarHostState,
    onUndo: (String) -> Unit,
    onCommit: (String) -> Unit,
) {
    val undo = rememberUpdatedState(onUndo)
    val commit = rememberUpdatedState(onCommit)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(pendingKey) {
        val key = pendingKey ?: return@LaunchedEffect
        var undone = false
        try {
            val result =
                snackbar.showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Long)
            undone = result == SnackbarResult.ActionPerformed
        } finally {
            if (undone) undo.value(key) else commit.value(key)
        }
    }
}
