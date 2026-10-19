package io.github.nytka_app.ui.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nytka_app.R
import io.github.nytka_app.core.api.Person
import io.github.nytka_app.core.api.UnnamedVoice
import io.github.nytka_app.core.api.VoiceCard
import io.github.nytka_app.ui.Monogram
import io.github.nytka_app.ui.tags.TagRow

/** The people a voice match asks about ("Is this Olena?"): a decision waits on each. */
fun peopleWithOpenMatch(cards: List<VoiceCard>): Set<String> =
    cards.filter { it.kind == VoiceCard.KIND_MATCH }.mapNotNullTo(HashSet()) { it.personId }

/** People with an open suggestion first, each group keeping the order it came in. */
fun openFirst(
    people: List<Person>,
    needing: Set<String>,
): List<Person> = if (needing.isEmpty()) people else people.sortedBy { it.id !in needing }

/** A person in the list: monogram, name, when they were heard, and a note when an answer waits. */
@Composable
fun PersonRow(
    person: Person,
    meta: String,
    needsAnswer: Boolean,
    onClick: () -> Unit,
) {
    Column {
        ListItem(
            leadingContent = { Monogram(person.name) },
            headlineContent = { Text(person.name) },
            supportingContent = {
                Column {
                    NameNotKnown(person.named)
                    Text(meta)
                    TagRow(person.tags, Modifier.padding(top = 4.dp))
                }
            },
            trailingContent = { RowEnd(needsAnswer) },
            modifier = Modifier.heightIn(min = 56.dp).clickable(onClick = onClick),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** A voice nobody named yet: a "?" disc on the tertiary container. */
@Composable
fun UnnamedVoiceRow(
    voice: UnnamedVoice,
    onClick: () -> Unit,
) {
    Column {
        ListItem(
            leadingContent = { Monogram("?", unknown = true) },
            headlineContent = { Text(voiceTitle(voice)) },
            supportingContent = { Text(voiceLine(voice)) },
            trailingContent = { RowEnd(needsAnswer = false) },
            modifier = Modifier.heightIn(min = 56.dp).clickable(onClick = onClick),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun RowEnd(needsAnswer: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (needsAnswer) {
            Text(
                stringResource(R.string.people_needs_answer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
