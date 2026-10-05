package io.github.nytka_app.ui.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nytka_app.R

/** A row of a list shows this many tags, then "+N". */
private const val MAX_ROW_TAGS = 3

/** The tags of a list row: at most three small labels and "+N" for the rest. Not tappable; the row opens the item. */
@Composable
fun TagRow(
    tags: List<String>,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tags.take(MAX_ROW_TAGS).forEach { TagLabel(it) }
        val more = tags.size - MAX_ROW_TAGS
        if (more > 0) TagLabel(stringResource(R.string.tags_more_format, more))
    }
}

@Composable
private fun TagLabel(text: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * Every tag of a conversation or person, with an ✕ on each and a **+ Tag** chip when [access] allows changes. Shows
 * nothing without the `tags` feature, and nothing at all when a read token meets no tags. [onTagClick] null leaves the
 * chips without a tap; A chip is not a button then.
 */
@Composable
fun TagChips(
    tags: List<String>,
    access: TagAccess,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    onTagClick: ((String) -> Unit)? = null,
) {
    val editable = access == TagAccess.Edit
    if (access == TagAccess.None || (tags.isEmpty() && !editable)) return
    var adding by rememberSaveable { mutableStateOf(false) }
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tags.forEach { TagChip(it, editable, onTagClick, onRemove) }
        if (editable) {
            AssistChip(onClick = { adding = true }, label = { Text(stringResource(R.string.tags_add)) })
        }
    }
    if (adding) {
        TagSheet(
            applied = tags,
            onAdd = {
                adding = false
                onAdd(it)
            },
            onDismiss = { adding = false },
        )
    }
}

/** [TagChips] as one list item, when the server has tags. */
fun LazyListScope.tagChipsItem(
    tags: List<String>,
    access: TagAccess,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    onTagClick: ((String) -> Unit)? = null,
) {
    if (access == TagAccess.None) return
    item(key = "tags") { TagChips(tags, access, onAdd, onRemove, modifier, onTagClick) }
}

@Composable
private fun TagChip(
    name: String,
    editable: Boolean,
    onClick: ((String) -> Unit)?,
    onRemove: (String) -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                style = MaterialTheme.typography.labelLarge,
                modifier =
                    (if (onClick != null) Modifier.clickable { onClick(name) } else Modifier)
                        .padding(start = 12.dp, end = if (editable) 0.dp else 12.dp, top = 8.dp, bottom = 8.dp),
            )
            if (editable) {
                IconButton(onClick = { onRemove(name) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.tag_remove_format, name),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
