package io.github.nytka_app.ui.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R

/** The server allows 32 characters; this leaves room for a `#` and spaces it turns into `-`. */
private const val MAX_INPUT = 64

/**
 * A text field and, below it, the tags in use that start with what is typed, most used first, without those [applied].
 * Picking one or **Done** calls [onAdd]; nothing is sent before that. The server normalizes the name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagSheet(
    applied: List<String>,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: TagSheetViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.setQuery("") }
    val suggestions = state.tags.filter { it.name !in applied }
    val submit = { if (state.query.isNotBlank()) onAdd(state.query) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.tag_sheet_title), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.setQuery(it.take(MAX_INPUT)) },
                label = { Text(stringResource(R.string.tag_field_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            if (suggestions.isNotEmpty()) {
                Text(
                    stringResource(R.string.tags_in_use),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp),
                )
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(suggestions, key = { it.name }) { tag ->
                        ListItem(
                            headlineContent = { Text(tag.name) },
                            trailingContent = { Text(tag.uses.toString()) },
                            modifier = Modifier.clickable { onAdd(tag.name) },
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { submit() }, enabled = state.query.isNotBlank()) {
                    Text(stringResource(R.string.action_done))
                }
            }
        }
    }
}
