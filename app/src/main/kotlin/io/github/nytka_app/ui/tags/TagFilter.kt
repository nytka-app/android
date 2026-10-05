package io.github.nytka_app.ui.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.core.api.Tag

/** What a list shows when it has no rows. */
enum class ListEmpty {
    /** It has rows, is still loading, or failed. */
    NotEmpty,

    /** The full list has nothing in it. */
    Empty,

    /** The list is filtered by a tag and nothing carries it. */
    EmptyForTag,
}

fun listEmpty(
    isEmpty: Boolean,
    loading: Boolean,
    failed: Boolean,
    tag: String?,
): ListEmpty =
    when {
        !isEmpty || loading || failed -> ListEmpty.NotEmpty
        tag != null -> ListEmpty.EmptyForTag
        else -> ListEmpty.Empty
    }

/** A tag the picker offers and how many items of the list being filtered carry it. */
data class FilterOption(
    val name: String,
    val count: Int,
)

/** The tags that [count] says some item of this list carries, in the server's order (most used first). */
fun List<Tag>.filterOptions(count: (Tag) -> Int): List<FilterOption> =
    mapNotNull { tag -> count(tag).takeIf { it > 0 }?.let { FilterOption(tag.name, it) } }

@Composable
@ReadOnlyComposable
fun tagFilterEmptyText(tag: String): String = stringResource(R.string.tag_filter_empty_format, tag)

/** The **Tag** action of a list's top bar: opens the tags in use; picking one calls [onPick]. */
@Composable
fun TagFilterAction(
    count: (Tag) -> Int,
    onPick: (String) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text(stringResource(R.string.tag_filter_action)) }
    if (open) {
        TagFilterSheet(
            count,
            onPick = {
                open = false
                onPick(it)
            },
            onDismiss = { open = false },
        )
    }
}

/** The tags in use that start with what is typed, with how many items of this list carry each. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagFilterSheet(
    count: (Tag) -> Int,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: TagSheetViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.setQuery("") }
    val options = state.tags.filterOptions(count)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.tag_filter_title), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.setQuery(it.take(MAX_QUERY)) },
                label = { Text(stringResource(R.string.tag_field_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            if (options.isEmpty()) {
                Text(
                    stringResource(R.string.tag_filter_none),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 320.dp).padding(bottom = 16.dp)) {
                    items(options, key = { it.name }) { option ->
                        ListItem(
                            headlineContent = { Text(option.name) },
                            trailingContent = { Text(option.count.toString()) },
                            modifier = Modifier.clickable { onPick(option.name) },
                        )
                    }
                }
            }
        }
    }
}

private const val MAX_QUERY = 64

/** "Tag: work" with an ✕ that clears the filter; nothing while no tag is set. */
fun LazyListScope.tagFilterChipItem(
    tag: String?,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tag == null) return
    item(key = "tag-filter") { TagFilterChip(tag, onClear, modifier) }
}

@Composable
private fun TagFilterChip(
    tag: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.tag_filter_chip_format, tag),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            )
            IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.tag_filter_clear_format, tag),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
