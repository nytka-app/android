package io.github.nytka_app.ui.memories

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.ui.ListFailure
import io.github.nytka_app.ui.ListState
import io.github.nytka_app.ui.ListStateKind
import io.github.nytka_app.ui.theme.ReadingColumn

/**
 * The Memories tab: what the server has learned about you, newest first and grouped by how long ago. A
 * memory's source opens that conversation through [onOpenConversation]; the pencil edits, the trash forgets
 * (with Undo), the button adds.
 */
@Composable
fun MemoriesTab(
    onOpenConversation: (String) -> Unit,
    viewModel: MemoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    UndoForgetEffect(
        state.forgotten,
        stringResource(R.string.memory_forgotten),
        snackbar,
        onUndo = viewModel::undoForget,
        onCommit = viewModel::commitForget,
    )

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::startAdd) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_memory))
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            ReadingColumn {
                PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                    ) {
                        state.error?.let { error ->
                            item(key = "error") { ListFailure(error, state.offline, onRetry = viewModel::refresh) }
                        }
                        if (state.loading && state.rows.isEmpty() && state.error == null) {
                            item(key = "loading") {
                                ListState(ListStateKind.Loading, stringResource(R.string.list_loading))
                            }
                        }
                        itemsIndexed(state.rows, key = { _, row -> row.id }) { index, row ->
                            if (index == 0 || state.rows[index - 1].group != row.group) GroupTitle(row.group)
                            MemoryItem(
                                row.text,
                                row.source,
                                onOpenSource = row.conversationId?.let { id -> { onOpenConversation(id) } },
                                onEdit = { viewModel.startEdit(row.id) },
                                onForget = { viewModel.forget(row.id) },
                            )
                        }
                        if (state.rows.isNotEmpty() && !state.endReached) {
                            item(key = "more") { LaunchedEffect(state.rows.size) { viewModel.loadMore() } }
                        }
                        if (state.rows.isEmpty() && !state.loading && state.error == null) {
                            item {
                                ListState(
                                    ListStateKind.Empty,
                                    stringResource(R.string.no_memories_yet),
                                    body = stringResource(R.string.no_memories_body),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    state.editor?.let { editor ->
        MemoryDialog(editor, onSave = viewModel::save, onDismiss = viewModel::dismissEditor)
    }
}

@Composable
private fun GroupTitle(group: MemoryGroup) {
    Text(
        stringResource(
            when (group) {
                MemoryGroup.Today -> R.string.memories_group_today
                MemoryGroup.ThisWeek -> R.string.memories_group_week
                MemoryGroup.ThisMonth -> R.string.memories_group_month
                MemoryGroup.Earlier -> R.string.memories_group_earlier
            },
        ),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
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
        title = {
            Text(stringResource(if (editor.target == null) R.string.add_memory else R.string.edit_memory_title))
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(MemoriesViewModel.MAX_LENGTH) },
                label = { Text(stringResource(R.string.memory_label)) },
                supportingText = { Text(editor.error ?: "${text.length} / ${MemoriesViewModel.MAX_LENGTH}") },
                shape = MaterialTheme.shapes.small,
                isError = editor.error != null,
                minLines = 2,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text) },
                enabled = text.isNotBlank() && !editor.saving,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
