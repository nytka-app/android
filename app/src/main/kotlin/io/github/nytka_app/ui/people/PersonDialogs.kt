package io.github.nytka_app.ui.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.nytka_app.R
import io.github.nytka_app.core.api.Person
import io.github.nytka_app.ui.ErrorLine
import io.github.nytka_app.ui.Monogram
import io.github.nytka_app.ui.NytkaTextField

@Composable
internal fun FactDialog(
    title: Int,
    initial: String,
    error: PersonNotice?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            // A fact can run to several lines, which NytkaTextField (one line) cannot hold.
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(PersonViewModel.MAX_FACT) },
                label = { Text(stringResource(R.string.person_fact_label)) },
                shape = MaterialTheme.shapes.small,
                minLines = 2,
                isError = error != null,
                supportingText = error?.let { e -> { ErrorLine(personNoticeText(LocalContext.current, e)) } },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
internal fun RenameDialog(
    initial: String,
    error: PersonNotice?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    named: Boolean = true,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(renameLabel(named)) },
        text = {
            NytkaTextField(
                value = text,
                onValueChange = { text = it.take(PersonViewModel.MAX_NAME) },
                label = stringResource(R.string.people_name_label),
                error = error?.let { personNoticeText(LocalContext.current, it) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
internal fun MergeDialog(
    name: String,
    others: List<Person>?,
    onDismiss: () -> Unit,
    onPick: (Person) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.people_merge_title_format, name)) },
        text = {
            if (others == null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.list_loading), style = MaterialTheme.typography.bodyMedium)
                }
            } else if (others.isEmpty()) {
                Text(stringResource(R.string.people_merge_nobody))
            } else {
                LazyColumn {
                    items(others, key = { it.id }) { other ->
                        ListItem(
                            headlineContent = { Text(other.name) },
                            supportingContent = { Text(linesText(other.segments)) },
                            leadingContent = { Monogram(other.name) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable(role = Role.Button) { onPick(other) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
