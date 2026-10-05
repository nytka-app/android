package io.github.nytka_app.ui.conversations

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.ui.tags.TagNoticeEffect
import io.github.nytka_app.ui.tags.tagChipsItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    developerMode: Boolean,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenTag: (String) -> Unit,
    viewModel: ConversationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf<Pair<String, String>?>(null) }
    val snackbar = rememberNoteHost(state.note, viewModel::noteShown)
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }
    SuggestionNoticeEffect(state.suggestionNotice, snackbar, viewModel::suggestionNoticeShown)
    TagNoticeEffect(state.tagNotice, snackbar, viewModel::tagNoticeShown)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pause() }
    // A summary in the making shows up on its own, while the screen is in front.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner, state.chip) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.keepFresh() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { menuOpen = true },
                    ) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more)) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = {
                            menuOpen = false
                            renaming = true
                        })
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.regenerate_summary)) },
                            enabled = !state.open,
                            onClick = {
                                menuOpen = false
                                viewModel.regenerate()
                            },
                        )
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = {
                            menuOpen = false
                            confirmDelete = true
                        })
                        if (developerMode) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.raw_transcription)) }, onClick = {
                                menuOpen = false
                                viewModel.loadRaw()
                            })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.time_range_and_length_format, state.timeRange, state.length),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            tagChipsItem(state.tags, state.tagAccess, viewModel::addTag, viewModel::removeTag, onTagClick = onOpenTag)
            state.playback?.let { playback ->
                item { PlayBar(playback, onToggle = viewModel::togglePlay, onSeek = viewModel::seekTo) }
            }
            state.chip?.let { chip -> item { AiChip(chip) } }
            state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            suggestion(state.banner, viewModel::acceptSuggestion, viewModel::rejectSuggestion)
            state.summary?.let { summary ->
                item { InfoCard(stringResource(R.string.summary_card_title)) { Text(summary) } }
            }
            if (state.tasks.isNotEmpty()) {
                item {
                    InfoCard(stringResource(R.string.tasks_card_title)) {
                        state.tasks.forEach { task ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(task.done, onCheckedChange = { viewModel.setTaskDone(task.id, it) })
                                Text(
                                    task.text,
                                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                                )
                            }
                        }
                    }
                }
            }
            items(state.looseBookmarks) { mark -> BookmarkLine(mark, viewModel::setBookmarkNote) }
            transcript(
                state.paragraphs,
                canPlay = state.playback != null,
                onName = { naming = it },
                onOpenPerson = onOpenPerson,
                onSave = viewModel::setBookmarkNote,
                onPlay = viewModel::playFrom,
                onMark = viewModel::markSegment,
            )
            state.raw?.let { raw ->
                item { Text(raw, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }

    naming?.let { (voiceId, label) ->
        NameVoiceDialog(
            initial = label.takeUnless { it.startsWith("SPEAKER_") }.orEmpty(),
            onDismiss = { naming = null },
            onSave = {
                naming = null
                viewModel.nameVoice(voiceId, it)
            },
        )
    }

    if (renaming) {
        RenameDialog(
            initial = state.currentTitle.orEmpty(),
            onDismiss = { renaming = false },
            onSave = {
                renaming = false
                viewModel.rename(it)
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_conversation_title)) },
            text = { Text(stringResource(R.string.delete_conversation_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDelete = false },
                ) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** The paragraphs; the time of one plays from there when [canPlay]. */
private fun LazyListScope.transcript(
    paragraphs: List<Paragraph>,
    canPlay: Boolean,
    onName: (Pair<String, String>) -> Unit,
    onOpenPerson: (String) -> Unit,
    onSave: (String, String) -> Unit,
    onPlay: (Int) -> Unit,
    onMark: (Long, Boolean?) -> Unit,
) = itemsIndexed(paragraphs) { i, paragraph ->
    ParagraphItem(paragraph, onName, onOpenPerson, onSave, onMark, onPlay = if (canPlay) ({ onPlay(i) }) else null)
}

/** Play or pause, the position and length, and a slider. A drag seeks when it ends, not on every move. */
@Composable
private fun PlayBar(
    playback: Playback,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: playback.positionMs.toFloat()
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onToggle) { Text(if (playback.playing) "Pause" else "Play") }
                Slider(
                    value = shown.coerceIn(0f, playback.durationMs.toFloat()),
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { onSeek(it.toLong()) }
                        dragging = null
                    },
                    valueRange = 0f..playback.durationMs.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                if (playback.failed) {
                    "The audio could not be played."
                } else {
                    "${Formatting.position(shown.toLong())} / ${Formatting.position(playback.durationMs)}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (playback.failed) MaterialTheme.colorScheme.error else Color.Unspecified,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    content: @Composable () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/**
 * The speaker above a run. A named person opens their page; a voice that can be named opens the dialog with its id
 * and label.
 */
@Composable
private fun SpeakerLabel(
    paragraph: Paragraph,
    onName: (Pair<String, String>) -> Unit,
    onOpenPerson: (String) -> Unit,
) {
    val voiceId = paragraph.voiceId
    val personId = paragraph.personId
    val label = paragraph.speaker.orEmpty()
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = speakerColor(paragraph.speakerColor),
            modifier =
                when {
                    personId != null ->
                        Modifier.clickable(
                            onClickLabel = stringResource(R.string.open_person),
                        ) { onOpenPerson(personId) }

                    voiceId != null -> Modifier.clickable { onName(voiceId to label) }
                    else -> Modifier
                },
        )
        paragraph.sourceNote?.let {
            val note =
                when (it) {
                    "marked" -> stringResource(R.string.segment_source_marked)
                    "voice" -> stringResource(R.string.segment_source_voice)
                    "provider" -> stringResource(R.string.segment_source_provider)
                    else -> it
                }
            Text(
                " · $note",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ParagraphItem(
    paragraph: Paragraph,
    onName: (Pair<String, String>) -> Unit,
    onOpenPerson: (String) -> Unit,
    onSave: (String, String) -> Unit,
    onMark: (Long, Boolean?) -> Unit,
    onPlay: (() -> Unit)?,
) {
    var markMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Column {
        if (paragraph.showSpeaker) {
            SpeakerLabel(paragraph, onName = onName, onOpenPerson = onOpenPerson)
        }
        Row {
            Text(
                paragraph.time,
                style = MaterialTheme.typography.labelMedium,
                color = if (onPlay != null) MaterialTheme.colorScheme.primary else Color.Unspecified,
                modifier =
                    Modifier
                        .width(56.dp)
                        .then(if (onPlay != null) Modifier.clickable(onClick = onPlay) else Modifier),
            )
            Box {
                Text(
                    paragraph.text,
                    modifier =
                        Modifier.combinedClickable(onClick = {}, onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            markMenu = true
                        }),
                )
                DropdownMenu(expanded = markMenu, onDismissRequest = { markMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.segment_mark_me)) }, onClick = {
                        markMenu = false
                        onMark(paragraph.segmentId, true)
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.segment_mark_not_me)) }, onClick = {
                        markMenu = false
                        onMark(paragraph.segmentId, false)
                    })
                    if (paragraph.marked) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.segment_mark_clear)) }, onClick = {
                            markMenu = false
                            onMark(paragraph.segmentId, null)
                        })
                    }
                }
            }
        }
        paragraph.bookmarks.forEach { mark -> BookmarkLine(mark, onSave) }
    }
}

/** A bookmark beside its paragraph: a star and the note, or a prompt to add one. Tapping edits the note. */
@Composable
private fun BookmarkLine(
    mark: BookmarkMark,
    onSave: (String, String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        BookmarkNoteDialog(
            initial = mark.note.orEmpty(),
            onDismiss = { editing = false },
            onSave = {
                editing = false
                onSave(mark.id, it)
            },
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { editing = true }
            .padding(start = 56.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Filled.Star,
            contentDescription = "Bookmark",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            mark.note ?: "Add a note",
            style = MaterialTheme.typography.bodySmall,
            color = if (mark.note == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
        )
    }
}

/** Sets the note of a bookmark; an empty one clears it. */
@Composable
private fun BookmarkNoteDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bookmark note") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(ConversationViewModel.MAX_BOOKMARK_NOTE) },
                label = { Text("Note") },
                supportingText = { Text("Leave it empty to remove the note.") },
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Names one voice; every line of it, past and future, shows the name. */
@Composable
internal fun NameVoiceDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Who is this?") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(MAX_NAME) },
                label = { Text("Name") },
                supportingText = { Text("The same name on another voice joins them into one person.") },
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

internal const val MAX_NAME = 80

/** An empty title restores the generated one. */
@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(MAX_TITLE) },
                label = { Text("Title") },
                supportingText = { Text("Leave it empty to use the generated title.") },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private const val MAX_TITLE = 120

/** Six colors that read on a light and on a dark surface; a seventh speaker starts over. */
@Composable
internal fun speakerColor(index: Int): Color {
    val palette = if (isSystemInDarkTheme()) DARK_SPEAKERS else LIGHT_SPEAKERS
    return palette[index % palette.size]
}

private val LIGHT_SPEAKERS =
    listOf(0xFF1565C0, 0xFF2E7D32, 0xFFC62828, 0xFF6A1B9A, 0xFFEF6C00, 0xFF00838F).map { Color(it) }
private val DARK_SPEAKERS =
    listOf(0xFF90CAF9, 0xFFA5D6A7, 0xFFEF9A9A, 0xFFCE93D8, 0xFFFFCC80, 0xFF80DEEA).map { Color(it) }
