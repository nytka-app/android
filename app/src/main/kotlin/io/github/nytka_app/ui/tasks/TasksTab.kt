package io.github.nytka_app.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The Tasks tab: open tasks newest first, done ones below, collapsed. A task opens its conversation through
 * [onOpenConversation].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksTab(
    onOpenConversation: (String) -> Unit,
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TaskRow?>(null) }
    var deleting by remember { mutableStateOf<TaskRow?>(null) }

    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            state.error?.let { error ->
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = viewModel::refresh) { Text("Retry") }
                    }
                }
            }
            items(state.open, key = { "open-${it.id}" }) { task ->
                TaskItem(task, onOpenConversation, viewModel::setDone, { editing = task }, { deleting = task })
            }
            if (state.open.isNotEmpty() && !state.openEndReached) {
                item(key = "more-open") { LaunchedEffect(state.open.size) { viewModel.loadMoreOpen() } }
            }
            if (state.open.isEmpty() && !state.loading && state.error == null) {
                item {
                    Text(
                        "No open tasks. Tasks the language model finds in your conversations show up here.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            completedSection(state, viewModel, onOpenConversation, { editing = it }, { deleting = it })
        }
    }

    editing?.let { task ->
        EditDialog(
            task.text,
            onDismiss = { editing = null },
            onSave = {
                editing = null
                viewModel.edit(task.id, it)
            },
        )
    }
    deleting?.let { task ->
        DeleteDialog(
            task.text,
            onDismiss = { deleting = null },
            onDelete = {
                deleting = null
                viewModel.delete(task.id)
            },
        )
    }
}

/** The collapsed "Done" header and, when opened, the done tasks, 30 at a time. */
private fun LazyListScope.completedSection(
    state: TasksUiState,
    viewModel: TasksViewModel,
    onOpenConversation: (String) -> Unit,
    onEdit: (TaskRow) -> Unit,
    onDelete: (TaskRow) -> Unit,
) {
    if (state.error == null || state.completedShown) {
        item(key = "done-header") {
            TextButton(onClick = viewModel::toggleCompleted, Modifier.padding(horizontal = 8.dp)) {
                Text("Done")
                Icon(
                    if (state.completedShown) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (state.completedShown) "Hide done tasks" else "Show done tasks",
                )
            }
        }
    }
    if (!state.completedShown) return
    items(state.completed, key = { "done-${it.id}" }) { task ->
        TaskItem(task, onOpenConversation, viewModel::setDone, { onEdit(task) }, { onDelete(task) })
    }
    if (state.completed.isNotEmpty() && !state.completedEndReached) {
        item(key = "more-done") {
            TextButton(onClick = viewModel::loadMoreCompleted, Modifier.padding(horizontal = 8.dp)) {
                Text("Show more")
            }
        }
    }
    if (state.completed.isEmpty()) item(key = "no-done") { Text("Nothing done yet.", Modifier.padding(16.dp)) }
}

@Composable
private fun DeleteDialog(
    text: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this task?") },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TaskItem(
    task: TaskRow,
    onOpenConversation: (String) -> Unit,
    onDone: (String, Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        leadingContent = { Checkbox(task.done, onCheckedChange = { onDone(task.id, it) }) },
        headlineContent = {
            Text(task.text, textDecoration = if (task.done) TextDecoration.LineThrough else null)
        },
        supportingContent = { if (task.source.isNotEmpty()) Text(task.source) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
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
        modifier = Modifier.fillMaxWidth().clickable { onOpenConversation(task.conversationId) },
    )
}

@Composable
private fun EditDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit task") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(MAX_TEXT) },
                label = { Text("Task") },
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private const val MAX_TEXT = 200
