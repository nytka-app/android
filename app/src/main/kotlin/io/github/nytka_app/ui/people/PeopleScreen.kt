package io.github.nytka_app.ui.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.core.api.Person
import io.github.nytka_app.core.api.UnnamedVoice
import io.github.nytka_app.ui.conversations.Formatting
import io.github.nytka_app.ui.conversations.MAX_NAME
import io.github.nytka_app.ui.conversations.NameVoiceDialog
import java.time.LocalDate
import java.time.ZoneId

/** The people Nytka knows, and the voices it heard but nobody named. Reached from the Device tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
    onBack: () -> Unit,
    viewModel: PeopleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.note) {
        state.note?.let {
            snackbar.showSnackbar(it)
            viewModel.noteShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.people_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
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
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                state.error?.let { error ->
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Text(error, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = viewModel::refresh) { Text("Retry") }
                        }
                    }
                }
                if (state.error == null) {
                    item { SectionTitle(stringResource(R.string.people_section)) }
                    if (state.people.isEmpty() && !state.loading) {
                        item { Empty(stringResource(R.string.no_people_yet)) }
                    }
                    items(state.people, key = { "p" + it.id }) { person ->
                        ListItem(
                            headlineContent = { Text(person.name) },
                            supportingContent = { Text(lines(person.segments)) },
                            modifier = Modifier.clickable { viewModel.openPerson(person) },
                        )
                    }
                    item { SectionTitle(stringResource(R.string.unnamed_voices_section)) }
                    if (state.voices.isEmpty() &&
                        !state.loading
                    ) {
                        item { Empty(stringResource(R.string.no_unnamed_voices)) }
                    }
                    items(state.voices, key = { "v" + it.speakerId }) { voice ->
                        ListItem(
                            headlineContent = { Text(voiceTitle(voice)) },
                            supportingContent = { Text("${lines(voice.segments)} · last heard ${lastHeard(voice)}") },
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

private fun lines(count: Int) = if (count == 1) "1 line" else "$count lines"

private fun voiceTitle(voice: UnnamedVoice) = voice.label?.takeIf { it.isNotBlank() } ?: "Unknown voice"

private fun lastHeard(voice: UnnamedVoice): String {
    val instant = Formatting.parse(voice.lastSeenAt) ?: return "unknown"
    val zone = ZoneId.systemDefault()
    val day = Formatting.dayTitle(instant.atZone(zone).toLocalDate(), LocalDate.now(zone))
    return "$day, ${Formatting.clock(instant, zone)}"
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
                TextButton(onClick = { viewModel.startRename(person) }) { Text("Rename") }
                TextButton(onClick = { viewModel.startMerge(person) }) { Text("Merge into another person") }
                TextButton(onClick = { viewModel.startDelete(person) }) { Text("Delete") }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::dismissDialog) { Text("Close") } },
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
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(MAX_NAME) },
                label = { Text("Name") },
                isError = dialog.error != null,
                supportingText = dialog.error?.let { error -> { Text(error) } },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
        title = { Text("Merge ${person.name} into") },
        text = {
            if (others.isEmpty()) {
                Text("There is nobody else to merge into.")
            } else {
                LazyColumn {
                    items(others, key = { it.id }) { other ->
                        ListItem(
                            headlineContent = { Text(other.name) },
                            supportingContent = { Text(lines(other.segments)) },
                            modifier = Modifier.clickable { onPick(other) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeleteDialog(
    person: Person,
    viewModel: PeopleViewModel,
) {
    AlertDialog(
        onDismissRequest = viewModel::dismissDialog,
        title = { Text("Delete ${person.name}?") },
        text = {
            Text(
                "Their lines stay, but show an unknown voice again. Deleting the voice model as well removes " +
                    "the stored voiceprint from the transcription service, so the voice is no longer recognized. " +
                    "This cannot be undone.",
            )
        },
        confirmButton = {
            Column {
                TextButton(onClick = { viewModel.forget(person) }) { Text("Delete and remove voice model") }
                TextButton(onClick = { viewModel.delete(person) }) { Text("Delete, keep voice model") }
                TextButton(onClick = viewModel::dismissDialog) { Text("Cancel") }
            }
        },
    )
}
