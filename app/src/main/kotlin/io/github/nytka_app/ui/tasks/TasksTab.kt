package io.github.nytka_app.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.ui.ListFailure
import io.github.nytka_app.ui.ListState
import io.github.nytka_app.ui.ListStateKind
import io.github.nytka_app.ui.NytkaTextField
import io.github.nytka_app.ui.names.NameCompletions
import io.github.nytka_app.ui.names.filled
import io.github.nytka_app.ui.names.knownPeople
import java.time.LocalDate

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
    var dating by remember { mutableStateOf<TaskRow?>(null) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            if (state.canCreate) {
                FloatingActionButton(onClick = { adding = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_task))
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)) {
                state.error?.let { error ->
                    item(key = "error") { ListFailure(error, state.offline, onRetry = viewModel::refresh) }
                }
                if (state.loading && state.open.isEmpty() && state.error == null) {
                    item(key = "loading") { ListState(ListStateKind.Loading, stringResource(R.string.list_loading)) }
                }
                items(state.open, key = { "open-${it.id}" }) { task ->
                    TaskItem(
                        task,
                        state.canCreate,
                        onOpenConversation,
                        viewModel::setDone,
                        { editing = task },
                        { dating = task },
                        { deleting = task },
                    )
                }
                if (state.open.isNotEmpty() && !state.openEndReached) {
                    item(key = "more-open") { LaunchedEffect(state.open.size) { viewModel.loadMoreOpen() } }
                }
                if (state.open.isEmpty() && !state.loading && state.error == null) {
                    item {
                        ListState(
                            ListStateKind.Empty,
                            stringResource(R.string.tasks_empty_title),
                            body = stringResource(R.string.tasks_empty_body),
                        )
                    }
                }
                completedSection(state, viewModel, onOpenConversation, { editing = it }, { dating = it }, {
                    deleting =
                        it
                })
            }
        }
    }

    AddAndDueDialogs(
        adding,
        dating,
        onAdded = { text, due ->
            adding = false
            viewModel.create(text, due)
        },
        onDated = { task, due ->
            dating = null
            viewModel.setDue(task.id, due)
        },
        onDismiss = {
            adding = false
            dating = null
        },
    )

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

/** The dialog that adds a task and the picker that sets one's due date. */
@Composable
private fun AddAndDueDialogs(
    adding: Boolean,
    dating: TaskRow?,
    onAdded: (String, LocalDate?) -> Unit,
    onDated: (TaskRow, LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    if (adding) AddTaskDialog(onDismiss, onAdded)
    dating?.let { task -> DuePicker(task.due, onDismiss, onPick = { onDated(task, it) }) }
}

/** The collapsed "Done" header and, when opened, the done tasks, 30 at a time. */
private fun LazyListScope.completedSection(
    state: TasksUiState,
    viewModel: TasksViewModel,
    onOpenConversation: (String) -> Unit,
    onEdit: (TaskRow) -> Unit,
    onDue: (TaskRow) -> Unit,
    onDelete: (TaskRow) -> Unit,
) {
    if (state.error == null || state.completedShown) {
        item(key = "done-header") {
            TextButton(onClick = viewModel::toggleCompleted, Modifier.padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.action_done))
                Icon(
                    if (state.completedShown) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription =
                        if (state.completedShown) {
                            stringResource(
                                R.string.hide_done_tasks,
                            )
                        } else {
                            stringResource(R.string.show_done_tasks)
                        },
                )
            }
        }
    }
    if (!state.completedShown) return
    items(state.completed, key = { "done-${it.id}" }) { task ->
        TaskItem(
            task,
            state.canCreate,
            onOpenConversation,
            viewModel::setDone,
            { onEdit(task) },
            { onDue(task) },
            { onDelete(task) },
        )
    }
    if (state.completed.isNotEmpty() && !state.completedEndReached) {
        item(key = "more-done") {
            TextButton(onClick = viewModel::loadMoreCompleted, Modifier.padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.action_show_more))
            }
        }
    }
    if (state.completed.isEmpty()) {
        item(key = "no-done") {
            Text(
                stringResource(R.string.nothing_done_yet),
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeleteDialog(
    text: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_task_title)) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = onDelete,
                colors =
                    ButtonDefaults.buttonColors(
                        MaterialTheme.colorScheme.error,
                        MaterialTheme.colorScheme.onError,
                    ),
            ) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun TaskItem(
    task: TaskRow,
    canSetDue: Boolean,
    onOpenConversation: (String) -> Unit,
    onDone: (String, Boolean) -> Unit,
    onEdit: () -> Unit,
    onDue: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        leadingContent = { Checkbox(task.done, onCheckedChange = { onDone(task.id, it) }) },
        headlineContent = {
            Text(task.text, color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified)
        },
        supportingContent = {
            Column {
                if (task.source.isNotEmpty()) Text(task.source)
                task.dueText?.let {
                    Text(it, color = if (task.overdue) MaterialTheme.colorScheme.error else Color.Unspecified)
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    menuOpen = true
                }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more)) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = {
                        menuOpen = false
                        onEdit()
                    })
                    if (canSetDue) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.task_due_edit)) }, onClick = {
                            menuOpen = false
                            onDue()
                        })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = {
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
                    task.conversationId?.let { id -> Modifier.clickable { onOpenConversation(id) } } ?: Modifier,
                ),
    )
}

@Composable
private fun EditDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(filled(initial)) }
    val people = knownPeople()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_task_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NytkaTextField(
                    value = field,
                    onValueChange = { field = it.copy(text = it.text.take(MAX_TEXT)) },
                    label = stringResource(R.string.task_label),
                )
                NameCompletions(field, onValueChange = { field = it }, people = people)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(field.text) },
                enabled = field.text.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val MAX_TEXT = 200
