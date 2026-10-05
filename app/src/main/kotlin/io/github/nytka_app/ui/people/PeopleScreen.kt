package io.github.nytka_app.ui.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.core.api.Person
import io.github.nytka_app.ui.conversations.MAX_NAME
import io.github.nytka_app.ui.conversations.NameVoiceDialog
import io.github.nytka_app.ui.people.cards.CardsEffects
import io.github.nytka_app.ui.people.cards.CardsViewModel
import io.github.nytka_app.ui.people.cards.cardsSection
import io.github.nytka_app.ui.people.review.ReviewInboxAction

/**
 * The People tab's list: the people Nytka knows and the voices it heard but nobody named. [onOpenPerson] opens
 * the person page; without it, or on a server that has none, a row opens the actions dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
    onOpenPerson: ((String) -> Unit)? = null,
    onOpenReview: () -> Unit = {},
    viewModel: PeopleViewModel = hiltViewModel(),
    cardsViewModel: CardsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cards by cardsViewModel.state.collectAsStateWithLifecycle()
    val clip by cardsViewModel.clip.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.resumed() }
    LaunchedEffect(state.note) {
        state.note?.let {
            snackbar.showSnackbar(noticeText(context, it))
            viewModel.noteShown()
        }
    }
    CardsEffects(cardsViewModel, snackbar)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.people_title)) },
                actions = { ReviewInboxAction(onOpenReview) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = {
                viewModel.refresh()
                cardsViewModel.refresh()
            },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                state.error?.let { error ->
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Text(noticeText(context, error), color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.action_retry)) }
                        }
                    }
                }
                if (state.error == null) {
                    cardsSection(cards.cards, clip, cards.busy, state.people, cardsViewModel, viewModel::refresh)
                    item { SectionTitle(stringResource(R.string.people_section)) }
                    if (state.people.isEmpty() && !state.loading) {
                        item { Empty(stringResource(R.string.no_people_yet)) }
                    }
                    items(state.people, key = { "p" + it.id }) { person ->
                        ListItem(
                            headlineContent = { Text(person.name) },
                            supportingContent = { Text(personLine(person, state.hasSummaries)) },
                            modifier = Modifier.clickable { viewModel.tap(person, onOpenPerson) },
                        )
                    }
                    item { SectionTitle(stringResource(R.string.unnamed_voices_section)) }
                    if (state.voices.isEmpty() && !state.loading) {
                        item { Empty(stringResource(R.string.no_unnamed_voices)) }
                    }
                    items(state.voices, key = { "v" + it.speakerId }) { voice ->
                        ListItem(
                            headlineContent = { Text(voiceTitle(voice)) },
                            supportingContent = { Text(voiceLine(voice)) },
                            modifier = Modifier.clickable { viewModel.openVoice(voice) },
                        )
                    }
                }
            }
        }
    }

    when (val dialog = state.dialog) {
        null -> Unit
        is PeopleDialog.Actions -> ActionsDialog(dialog.person, viewModel)
        is PeopleDialog.Rename ->
            RenameDialog(dialog, onDismiss = viewModel::dismissDialog, onSave = { viewModel.rename(dialog.person, it) })

        is PeopleDialog.Merge ->
            MergeDialog(
                dialog.person,
                state.people.filter { it.id != dialog.person.id },
                onDismiss = viewModel::dismissDialog,
                onPick = { viewModel.merge(dialog.person, it) },
            )

        is PeopleDialog.Delete -> DeleteDialog(dialog.person, viewModel)
        is PeopleDialog.NameVoice ->
            NameVoiceDialog(
                initial =
                    dialog.voice.label
                        ?.takeUnless { it.startsWith("SPEAKER_") }
                        .orEmpty(),
                onDismiss = viewModel::dismissDialog,
                onSave = { viewModel.nameVoice(dialog.voice, it) },
            )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun Empty(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun ActionsDialog(
    person: Person,
    viewModel: PeopleViewModel,
) {
    AlertDialog(
        onDismissRequest = viewModel::dismissDialog,
        title = { Text(person.name) },
        text = {
            Column {
                TextButton(onClick = { viewModel.startRename(person) }) { Text(stringResource(R.string.action_rename)) }
                TextButton(onClick = { viewModel.startMerge(person) }) {
                    Text(stringResource(R.string.people_merge_action))
                }
                TextButton(onClick = { viewModel.startDelete(person) }) { Text(stringResource(R.string.action_delete)) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = viewModel::dismissDialog,
            ) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun RenameDialog(
    dialog: PeopleDialog.Rename,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(dialog.person.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_rename)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(MAX_NAME) },
                label = { Text(stringResource(R.string.people_name_label)) },
                isError = dialog.error != null,
                supportingText = dialog.error?.let { error -> { Text(noticeText(LocalContext.current, error)) } },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun MergeDialog(
    person: Person,
    others: List<Person>,
    onDismiss: () -> Unit,
    onPick: (Person) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.people_merge_title_format, person.name)) },
        text = {
            if (others.isEmpty()) {
                Text(stringResource(R.string.people_merge_nobody))
            } else {
                LazyColumn {
                    items(others, key = { it.id }) { other ->
                        ListItem(
                            headlineContent = { Text(other.name) },
                            supportingContent = { Text(linesText(other.segments)) },
                            modifier = Modifier.clickable { onPick(other) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun DeleteDialog(
    person: Person,
    viewModel: PeopleViewModel,
) {
    AlertDialog(
        onDismissRequest = viewModel::dismissDialog,
        title = { Text(stringResource(R.string.people_delete_title_format, person.name)) },
        text = { Text(stringResource(R.string.people_delete_text)) },
        confirmButton = {
            Column {
                TextButton(onClick = { viewModel.forget(person) }) {
                    Text(stringResource(R.string.people_delete_and_forget))
                }
                TextButton(onClick = { viewModel.delete(person) }) {
                    Text(stringResource(R.string.people_delete_keep_model))
                }
                TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
