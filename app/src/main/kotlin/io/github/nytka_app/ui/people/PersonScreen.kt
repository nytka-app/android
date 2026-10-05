package io.github.nytka_app.ui.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.core.api.PersonFact

/**
 * One person: header, your note, facts, open tasks and recent conversations. A pushed screen, so it has a back
 * arrow. A fact, task or conversation with a conversation opens it through [onOpenConversation].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonScreen(
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    viewModel: PersonViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(state.gone) { if (state.gone) onBack() }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(personNoticeText(context, it))
            viewModel.noticeShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.header?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = { if (state.header != null) PersonMenu(viewModel) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = viewModel::refresh,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                state.error?.let { error ->
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Text(personNoticeText(context, error), color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.action_retry)) }
                        }
                    }
                }
                state.header?.let { header ->
                    item { Header(header) }
                    item { NoteSection(state, viewModel) }
                    factsSection(state, viewModel, onOpenConversation)
                    tasksSection(state, onOpenConversation)
                    conversationsSection(state, onOpenConversation)
                }
            }
        }
    }

    when (val dialog = state.dialog) {
        null -> Unit
        is PersonDialog.AddFact ->
            FactDialog(
                title = R.string.person_fact_add,
                initial = "",
                error = dialog.error,
                onDismiss = viewModel::dismissDialog,
                onSave = viewModel::addFact,
            )

        is PersonDialog.EditFact ->
            FactDialog(
                title = R.string.person_fact_edit,
                initial = dialog.fact.text,
                error = dialog.error,
                onDismiss = viewModel::dismissDialog,
                onSave = { viewModel.editFact(dialog.fact, it) },
            )

        is PersonDialog.DeleteFact ->
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                title = { Text(stringResource(R.string.person_fact_delete_title)) },
                text = { Text(stringResource(R.string.person_fact_delete_text)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.deleteFact(dialog.fact) }) {
                        Text(stringResource(R.string.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(R.string.action_cancel)) }
                },
            )

        is PersonDialog.Rename ->
            RenameDialog(
                initial = state.header?.name.orEmpty(),
                error = dialog.error,
                onDismiss = viewModel::dismissDialog,
                onSave = viewModel::rename,
            )

        PersonDialog.Merge ->
            MergeDialog(
                name = state.header?.name.orEmpty(),
                others = state.others,
                onDismiss = viewModel::dismissDialog,
                onPick = viewModel::merge,
            )

        PersonDialog.Delete ->
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                title = { Text(stringResource(R.string.people_delete_title_format, state.header?.name.orEmpty())) },
                text = { Text(stringResource(R.string.person_delete_text)) },
                confirmButton = {
                    Column {
                        TextButton(onClick = { viewModel.delete(forgetVoice = true) }) {
                            Text(stringResource(R.string.person_delete_forget))
                        }
                        TextButton(onClick = { viewModel.delete(forgetVoice = false) }) {
                            Text(stringResource(R.string.person_delete_only))
                        }
                        TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(R.string.action_cancel)) }
                    }
                },
            )
    }
}

@Composable
private fun PersonMenu(viewModel: PersonViewModel) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_rename)) },
            onClick = {
                open = false
                viewModel.show(PersonDialog.Rename())
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.person_merge_action)) },
            onClick = {
                open = false
                viewModel.startMerge()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete)) },
            onClick = {
                open = false
                viewModel.show(PersonDialog.Delete)
            },
        )
    }
}

@Composable
private fun Header(header: PersonHeader) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            lastSeenText(header.lastSeenAt)?.let { stringResource(R.string.people_last_heard_format, it) }
                ?: stringResource(R.string.people_never_heard),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            if (header.hasVoiceprint) {
                pluralStringResource(R.plurals.person_voice_model, header.voiceprintSamples, header.voiceprintSamples)
            } else {
                stringResource(R.string.person_voice_model_none)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoteSection(
    state: PersonUiState,
    viewModel: PersonViewModel,
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        SectionTitle(stringResource(R.string.person_note_title), padded = false)
        OutlinedTextField(
            value = state.noteDraft,
            onValueChange = viewModel::setNoteDraft,
            label = { Text(stringResource(R.string.person_note_label)) },
            supportingText = {
                Text(
                    stringResource(R.string.person_note_hint) + " " +
                        stringResource(
                            R.string.person_note_counter_format,
                            state.noteDraft.length,
                            PersonViewModel.MAX_NOTE,
                        ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = viewModel::saveNote,
            enabled = state.noteDraft.trim() != state.note,
        ) { Text(stringResource(R.string.action_save)) }
    }
}

private fun LazyListScope.factsSection(
    state: PersonUiState,
    viewModel: PersonViewModel,
    onOpenConversation: (String) -> Unit,
) {
    item {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionTitle(stringResource(R.string.person_facts_title))
            TextButton(
                onClick = { viewModel.show(PersonDialog.AddFact()) },
            ) { Text(stringResource(R.string.person_fact_add)) }
        }
    }
    if (state.facts.isEmpty()) item { Empty(stringResource(R.string.person_no_facts)) }
    items(state.facts, key = { "f" + it.id }) { fact ->
        FactRow(fact, viewModel, onOpenConversation)
    }
    if (state.nextBefore != null) {
        item {
            TextButton(
                onClick = viewModel::loadMoreFacts,
                enabled = !state.loadingMore,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text(stringResource(R.string.action_show_more)) }
        }
    }
}

@Composable
private fun FactRow(
    fact: PersonFact,
    viewModel: PersonViewModel,
    onOpenConversation: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val conversationId = fact.conversationId
    ListItem(
        headlineContent = { Text(fact.text) },
        supportingContent = {
            Text(listOfNotNull(basisText(fact.basis), fact.conversationTitle).joinToString(" · "))
        },
        trailingContent = {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.person_fact_more))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.person_fact_edit)) },
                    onClick = {
                        menu = false
                        viewModel.show(PersonDialog.EditFact(fact))
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    onClick = {
                        menu = false
                        viewModel.show(PersonDialog.DeleteFact(fact))
                    },
                )
            }
        },
        modifier = if (conversationId != null) Modifier.clickable { onOpenConversation(conversationId) } else Modifier,
    )
}

private fun LazyListScope.tasksSection(
    state: PersonUiState,
    onOpenConversation: (String) -> Unit,
) {
    item { SectionTitle(stringResource(R.string.person_tasks_title)) }
    if (state.tasks.isEmpty()) item { Empty(stringResource(R.string.person_no_tasks)) }
    items(state.tasks, key = { "t" + it.id }) { task ->
        ListItem(
            headlineContent = { Text(task.text) },
            supportingContent = task.conversationTitle?.let { title -> { Text(title) } },
            modifier = Modifier.clickable { onOpenConversation(task.conversationId) },
        )
    }
}

private fun LazyListScope.conversationsSection(
    state: PersonUiState,
    onOpenConversation: (String) -> Unit,
) {
    item { SectionTitle(stringResource(R.string.person_conversations_title)) }
    if (state.conversations.isEmpty()) item { Empty(stringResource(R.string.person_no_conversations)) }
    items(state.conversations, key = { "c" + it.id }) { conversation ->
        ListItem(
            headlineContent = {
                Text(
                    conversation.title?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.person_conversation_untitled),
                )
            },
            supportingContent = lastSeenText(conversation.startedAt)?.let { day -> { Text(day) } },
            modifier = Modifier.clickable { onOpenConversation(conversation.id) },
        )
    }
}

@Composable
private fun SectionTitle(
    text: String,
    padded: Boolean = true,
) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = if (padded) 16.dp else 0.dp, vertical = 8.dp),
    )
}

@Composable
private fun Empty(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
}
