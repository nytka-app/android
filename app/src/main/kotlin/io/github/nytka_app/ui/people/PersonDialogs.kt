package io.github.nytka_app.ui.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.nytka_app.R
import io.github.nytka_app.core.api.Person

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
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(PersonViewModel.MAX_FACT) },
                label = { Text(stringResource(R.string.person_fact_label)) },
                isError = error != null,
                supportingText = error?.let { e -> { Text(personNoticeText(LocalContext.current, e)) } },
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
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(PersonViewModel.MAX_NAME) },
                label = { Text(stringResource(R.string.people_name_label)) },
                isError = error != null,
                supportingText = error?.let { e -> { Text(personNoticeText(LocalContext.current, e)) } },
                singleLine = true,
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
                CircularProgressIndicator()
            } else if (others.isEmpty()) {
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
