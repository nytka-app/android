package io.github.nytka_app.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.core.api.Evidence
import io.github.nytka_app.core.api.LifeItem
import io.github.nytka_app.core.api.Person
import io.github.nytka_app.ui.Monogram
import io.github.nytka_app.ui.conversations.Formatting
import io.github.nytka_app.ui.names.knownPeople
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** What an item's screen can do with it: open its evidence, correct it, or open what it points to. */
class ItemActions(
    val onOpen: (LifeItem) -> Unit,
    val onNotImportant: (LifeItem) -> Unit,
    val onOpenPerson: (String) -> Unit,
)

/**
 * One item: its kind, its text, and when it was said and with whom. A tap opens where it comes from ([EvidenceSheet]);
 * ⋮ has "Not important".
 */
@Composable
fun LifeItemCard(
    item: LifeItem,
    zone: ZoneId,
    actions: ItemActions,
    modifier: Modifier = Modifier,
    dim: Boolean = false,
) {
    var menu by remember { mutableStateOf(false) }
    val color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { actions.onOpen(item) }
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                kindTitle(item),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(item.text, style = MaterialTheme.typography.bodyLarge, color = color)
            meta(item, zone)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.item_actions_format, item.text),
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.item_where_from)) }, onClick = {
                    menu = false
                    actions.onOpen(item)
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.item_not_important)) }, onClick = {
                    menu = false
                    actions.onNotImportant(item)
                })
                item.personId?.let { id ->
                    DropdownMenuItem(text = { Text(item.personName.orEmpty()) }, onClick = {
                        menu = false
                        actions.onOpenPerson(id)
                    })
                }
            }
        }
    }
}

@Composable
private fun kindLabel(kind: String): String =
    stringResource(
        when (kind) {
            LifeItem.KIND_TASK -> R.string.item_kind_task
            LifeItem.KIND_MEMORY -> R.string.item_kind_memory
            else -> R.string.item_kind_note
        },
    )

/** "Task", or "Task · You marked this" for a moment the wearer marked with a tap on the pendant. */
@Composable
private fun kindTitle(item: LifeItem): String =
    if (item.markedAt == null) {
        kindLabel(item.kind)
    } else {
        stringResource(R.string.item_kind_marked_format, kindLabel(item.kind))
    }

/** "09:14 · Ann · Due 2026-10-24": what is known of when and with whom. */
@Composable
private fun meta(
    item: LifeItem,
    zone: ZoneId,
): String? {
    val due = item.due?.let { stringResource(R.string.item_due_format, it.take(DATE_LENGTH)) }
    return listOfNotNull(item.at?.let(Formatting::parse)?.let { Formatting.clock(it, zone) }, item.personName, due)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")
}

/** The corrections a line of evidence takes. */
class LineActions(
    val onNotMe: (Evidence) -> Unit,
    val onOtherPerson: (Evidence, String) -> Unit,
)

/**
 * Where [item] comes from: each cited line with who said it, when, the quote and how sure the server is it was the
 * wearer; "Play from here" opens the conversation at that moment. Lines take "Not me" (on the wearer's) and "Someone
 * else said this"; the item takes "Not important".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvidenceSheet(
    item: LifeItem,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onOpenMoment: (conversationId: String, atMs: Long?) -> Unit,
    onNotImportant: (LifeItem) -> Unit,
    lines: LineActions,
    onOpenTopic: (String) -> Unit = {},
    related: RelatedViewModel = hiltViewModel(key = "related-sheet"),
) {
    var picking by remember { mutableStateOf<Evidence?>(null) }
    val earlier by related.moments.collectAsStateWithLifecycle()
    LaunchedEffect(item.kind, item.id) { related.load(item.kind, item.id, item.related) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                kindTitle(item),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(item.text, style = MaterialTheme.typography.titleMedium)
            item.markedAt?.let(Formatting::parse)?.let {
                Text(
                    stringResource(R.string.item_marked_at_format, Formatting.clock(it, zone)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                stringResource(R.string.item_where_from),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            if (item.evidence.isEmpty()) {
                Text(stringResource(R.string.item_no_lines), style = MaterialTheme.typography.bodyMedium)
            }
            item.evidence.forEach { line ->
                EvidenceLine(
                    line,
                    zone,
                    onPlay = { onOpenMoment(line.conversationId, Formatting.parse(line.at)?.toEpochMilli()) },
                    onNotMe = { lines.onNotMe(line) },
                    onOtherPerson = { picking = line },
                )
                HorizontalDivider()
            }
            TopicChips(item.topics, onOpenTopic)
            EarlierStrip(earlier, zone, LocalDate.now(zone), onOpenMoment)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onNotImportant(item) }) { Text(stringResource(R.string.item_not_important)) }
                val conversation = item.conversationId ?: item.evidence.firstOrNull()?.conversationId
                if (conversation != null) {
                    TextButton(onClick = { onOpenMoment(conversation, null) }) {
                        Text(stringResource(R.string.item_open_conversation))
                    }
                }
            }
        }
    }
    picking?.let { line ->
        PersonPicker(
            people = knownPeople(),
            onPick = { id ->
                picking = null
                lines.onOtherPerson(line, id)
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun EvidenceLine(
    line: Evidence,
    zone: ZoneId,
    onPlay: () -> Unit,
    onNotMe: () -> Unit,
    onOtherPerson: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(speakerOf(line), style = MaterialTheme.typography.labelLarge)
                Formatting.parse(line.at)?.let {
                    Text(
                        Formatting.clock(it, zone),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (line.isUser == true) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.evidence_not_me)) }, onClick = {
                            menu = false
                            onNotMe()
                        })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.evidence_other_person)) }, onClick = {
                        menu = false
                        onOtherPerson()
                    })
                }
            }
        }
        Text("“${line.quote}”", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
        TextButton(onClick = onPlay) { Text(stringResource(R.string.evidence_play)) }
    }
}

/** "You · 66% sure it is your voice" for the wearer, else the server's label; "Unknown" reads "Someone". */
@Composable
private fun speakerOf(line: Evidence): String =
    when {
        line.isUser == true ->
            line.wearerConfidence?.let {
                stringResource(
                    R.string.evidence_you_sure_format,
                    (it * PERCENT).roundToInt(),
                )
            }
                ?: stringResource(R.string.speaker_you)

        line.speaker.isNullOrBlank() || line.speaker == UNKNOWN -> stringResource(R.string.evidence_someone)
        else -> line.speaker.orEmpty()
    }

@Composable
private fun PersonPicker(
    people: List<Person>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.evidence_pick_person)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (people.isEmpty()) Text(stringResource(R.string.evidence_no_people))
                people.forEach { person ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onPick(person.id) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Monogram(person.name, size = 32.dp)
                        Text(person.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Says what a correction did; after "Not important" the snackbar's Undo puts the item back. */
@Composable
fun CorrectionSnackbar(
    notice: CorrectionNotice?,
    snackbar: SnackbarHostState,
    onUndo: (LifeItem) -> Unit,
    onShown: () -> Unit,
) {
    val done =
        when (notice?.kind) {
            CorrectionKind.NotImportant -> stringResource(R.string.correction_not_important)
            CorrectionKind.NotMe -> stringResource(R.string.correction_not_me)
            CorrectionKind.OtherPerson -> stringResource(R.string.correction_other_person)
            CorrectionKind.Undone -> stringResource(R.string.correction_undone)
            null -> null
        }
    val undo = stringResource(R.string.action_undo)
    val currentUndo by rememberUpdatedState(onUndo)
    val currentShown by rememberUpdatedState(onShown)
    LaunchedEffect(notice) {
        notice ?: return@LaunchedEffect
        val text = notice.failure ?: done ?: return@LaunchedEffect
        val item = notice.undoable.takeIf { notice.failure == null }
        val result =
            snackbar.showSnackbar(
                text,
                actionLabel = item?.let { undo },
                duration = if (item != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
        // Cleared only now: clearing it first would end this effect before the snackbar shows.
        currentShown()
        if (result == SnackbarResult.ActionPerformed && item != null) currentUndo(item)
    }
}

private const val PERCENT = 100
private const val DATE_LENGTH = 10
private const val UNKNOWN = "Unknown"
