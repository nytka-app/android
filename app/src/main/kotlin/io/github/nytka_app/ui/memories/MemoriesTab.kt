package io.github.nytka_app.ui.memories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The Memories tab: what the server has learned about you, newest first. A memory with a source opens that
 * conversation through [onOpenConversation]; the row menu edits and deletes, the button adds.
 */
@Composable
fun MemoriesTab(
    onOpenConversation: (String) -> Unit,
    viewModel: MemoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            ) {
                state.error?.let { error ->
                    item {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = viewModel::refresh) { Text("Retry") }
                    }
                }
                items(state.rows, key = { it.id }) { row ->
                    MemoryItem(
                        row,
                        onOpen = { row.conversationId?.let(onOpenConversation) },
                        onEdit = { viewModel.startEdit(row.id) },
                        onDelete = { deleting = row.id },
                    )
                }
                if (state.rows.isNotEmpty() && !state.endReached) {
                    item(key = "more") { LaunchedEffect(state.rows.size) { viewModel.loadMore() } }
                }
                if (state.rows.isEmpty() && !state.loading && state.error == null) {
                    item { Text(EMPTY) }
                }
            }
        }
        FloatingActionButton(
            onClick = viewModel::startAdd,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "Add a memory") }
    }

    state.editor?.let { editor ->
        MemoryDialog(editor, onSave = viewModel::save, onDismiss = viewModel::dismissEditor)
    }
    deleting?.let { id ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this memory?") },
            text = { Text("The server will not learn it from your conversations again.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(id)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

private const val EMPTY =
    "No memories yet. Memories are lasting facts about you, such as where you work or what you like. " +
        "They come from summarized conversations, or you can add one."

@Composable
private fun MemoryItem(
    row: MemoryRow,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(row.text) },
        supportingContent =
            row.source?.let { source ->
                { Text(source, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Memory options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = {
                        menuOpen = false
                        onEdit()
                    })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = {
                        menuOpen = false
                        onDelete()
                    })
                }
            }
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (row.conversationId !=
                        null
                    ) {
                        Modifier.clickable(onClick = onOpen)
                    } else {
                        Modifier
                    },
                ),
    )
}

@Composable
private fun MemoryDialog(
    editor: MemoryEditor,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(editor.target?.id) { mutableStateOf(editor.target?.text.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editor.target == null) "Add a memory" else "Edit memory") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(MemoriesViewModel.MAX_LENGTH) },
                label = { Text("A fact about you") },
                supportingText = { Text(editor.error ?: "${text.length} / ${MemoriesViewModel.MAX_LENGTH}") },
                isError = editor.error != null,
                minLines = 2,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank() && !editor.saving) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
