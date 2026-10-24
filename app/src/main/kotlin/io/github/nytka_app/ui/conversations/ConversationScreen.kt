package io.github.nytka_app.ui.conversations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.core.api.NameSuggestion
import io.github.nytka_app.core.api.Person
import io.github.nytka_app.ui.NytkaTextField
import io.github.nytka_app.ui.names.NameChips
import io.github.nytka_app.ui.names.NameCompletions
import io.github.nytka_app.ui.names.filled
import io.github.nytka_app.ui.names.knownPeople
import io.github.nytka_app.ui.names.nameSuggestions
import io.github.nytka_app.ui.people.RoleNoticeEffect
import io.github.nytka_app.ui.people.UndoNoticeEffect
import io.github.nytka_app.ui.people.names
import io.github.nytka_app.ui.tags.TagNoticeEffect
import io.github.nytka_app.ui.tags.tagChipsItem
import io.github.nytka_app.ui.tags.tagProposalsItem
import io.github.nytka_app.ui.theme.NytkaTheme
import io.github.nytka_app.ui.theme.ReadingColumn
import io.github.nytka_app.ui.today.RelatedViewModel
import io.github.nytka_app.ui.today.earlierItem
import io.github.nytka_app.ui.today.rememberEarlier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    developerMode: Boolean,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenTag: (String) -> Unit,
    viewModel: ConversationViewModel = hiltViewModel(),
    /** Opens another conversation at a moment; with it the screen shows the "Earlier" strip. */
    onOpenMoment: ((conversationId: String, atMs: Long?) -> Unit)? = null,
    related: RelatedViewModel = hiltViewModel(key = "related-conversation"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val earlier = rememberEarlier(related, onOpenMoment != null)
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf<Pair<String, String>?>(null) }
    var memoryDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var follow by rememberSaveable { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val snackbar = rememberNoteHost(state.note, viewModel::noteShown)
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }
    val skip = rememberSuggestionSkip(state.banner, snackbar)
    SuggestionNoticeEffect(state.suggestionNotice, snackbar, viewModel::suggestionNoticeShown)
    AcceptAllNoticeEffect(state.acceptAllNotice, snackbar, viewModel::acceptAllNoticeShown)
    RoleNoticeEffect(state.roleNotice, snackbar, viewModel::roleNoticeShown)
    TagNoticeEffect(state.tagNotice, snackbar, viewModel::tagNoticeShown)
    SpeechNoticeEffect(state.speechNotice, snackbar, viewModel::speechNoticeShown)
    UndoNoticeEffect(state.undoNotice, snackbar, viewModel::undoNoticeShown)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pause() }
    val playback = state.playback
    val heard = playback.heardParagraph()
    FollowTranscript(listState, heard, follow && playback?.playing == true, state.paragraphs.size, state.raw != null)
    ScrollToMoment(listState, state.focus, state.paragraphs.size, state.raw != null, viewModel::focusShown)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
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
                        OtherVoicesItems(state.speechAccess, onMark = viewModel::markOthers) { menuOpen = false }
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
        bottomBar = {
            ConversationPlayerBar(playback, state.paragraphs, heard, follow, { follow = !follow }, viewModel)
        },
    ) { padding ->
        ReadingColumn {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text(state.title, style = NytkaTheme.typography.readingTitle) }
                item {
                    Text(
                        stringResource(R.string.time_range_and_length_format, state.timeRange, state.length),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                tagChipsItem(
                    state.tags,
                    state.tagAccess,
                    viewModel::addTag,
                    viewModel::removeTag,
                    onTagClick = onOpenTag,
                )
                state.chip?.let { chip -> item { AiChip(chip) } }
                state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
                suggestion(
                    skip.shown,
                    viewModel::acceptSuggestion,
                    viewModel::rejectSuggestion,
                    onSkip = skip.onSkip,
                    onAcceptAll = viewModel::acceptAllByName,
                )
                tagProposalsItem(state.tagProposals, viewModel::acceptTagProposal, viewModel::rejectTagProposal)
                state.summary?.let { summary ->
                    item {
                        InfoCard(stringResource(R.string.summary_card_title)) {
                            Text(summary, style = NytkaTheme.typography.readingLead)
                        }
                    }
                }
                earlierItem(earlier, onOpenMoment)
                if (state.tasks.isNotEmpty()) {
                    item {
                        InfoCard(stringResource(R.string.tasks_card_title)) {
                            // Done tasks sink below the open ones and dim; a strikethrough would be harder to read.
                            state.tasks.sortedBy { it.done }.forEach { task ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(task.done, onCheckedChange = { viewModel.setTaskDone(task.id, it) })
                                    Text(
                                        task.text,
                                        color =
                                            if (task.done) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                Color.Unspecified
                                            },
                                    )
                                }
                            }
                        }
                    }
                }
                items(state.looseBookmarks) { mark -> BookmarkLine(mark, viewModel::setBookmarkNote) }
                transcript(
                    state.paragraphs,
                    AutoNamedMarks(state.autoNamed, viewModel::undoAutoName),
                    playingIndex = heard,
                    canPlay = state.playback != null,
                    onName = { naming = it },
                    onOpenPerson = onOpenPerson,
                    onSave = viewModel::setBookmarkNote,
                    onPlay = viewModel::playFrom,
                    onMark = viewModel::markSegment,
                    onKind = viewModel::markKind.takeIf { state.speechAccess },
                    onMemory = { memoryDraft = lineMemoryDraft(it) },
                )
                state.raw?.let { raw ->
                    item { Text(raw, style = NytkaTheme.typography.code) }
                }
            }
        }
    }

    LineMemoryHost(memoryDraft, snackbar) { memoryDraft = null }

    naming?.let { (voiceId, label) ->
        NameVoiceDialog(
            initial = label.takeUnless { it.startsWith("SPEAKER_") }.orEmpty(),
            guesses = viewModel.nameGuesses(voiceId),
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

/** The paragraphs; the time of one plays from there when [canPlay], and the one at [playingIndex] is marked. */
private fun LazyListScope.transcript(
    paragraphs: List<Paragraph>,
    autoNamed: AutoNamedMarks,
    playingIndex: Int?,
    canPlay: Boolean,
    onName: (Pair<String, String>) -> Unit,
    onOpenPerson: (String) -> Unit,
    onSave: (String, String) -> Unit,
    onPlay: (Int) -> Unit,
    onMark: (Long, Boolean?) -> Unit,
    onKind: ((Long, String?) -> Unit)?,
    onMemory: (Paragraph) -> Unit,
) {
    val voices = paragraphs.voiceNumbers()
    itemsIndexed(paragraphs) { i, paragraph ->
        ParagraphItem(
            paragraph,
            autoNamed,
            voiceNumber = voices.of(paragraph.speaker.orEmpty()),
            playing = i == playingIndex,
            onName,
            onOpenPerson,
            onSave,
            onMark,
            onKind,
            onPlay = if (canPlay) ({ onPlay(i) }) else null,
            onMemory = onMemory,
        )
    }
}

@Composable
private fun InfoCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/**
 * The speaker above a run, told apart by shape and not by hue. The wearer is a primary tag; a named person is a
 * mark and a name that opens their page; a voice nobody has named is a tertiary tag, "Voice N", and "Who is this?"
 * opens the dialog that names it.
 */
@Composable
private fun SpeakerLabel(
    paragraph: Paragraph,
    autoNamed: AutoNamedMarks,
    voiceNumber: Int,
    onName: (Pair<String, String>) -> Unit,
    onOpenPerson: (String) -> Unit,
) {
    val voiceId = paragraph.voiceId
    val personId = paragraph.personId
    val label = paragraph.speaker.orEmpty()
    Row(
        Modifier.padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            paragraph.isUser == true ->
                SpeakerTag(
                    stringResource(R.string.speaker_you),
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                )

            paragraph.unknownVoice -> {
                SpeakerTag(
                    stringResource(R.string.speaker_voice_format, voiceNumber),
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer,
                )
                if (voiceId != null) {
                    Text(
                        stringResource(R.string.who_is_this_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.clickable { onName(voiceId to label) },
                    )
                }
            }

            else ->
                Row(
                    Modifier.then(
                        if (personId != null) {
                            Modifier.clickable(onClickLabel = stringResource(R.string.open_person)) {
                                onOpenPerson(personId)
                            }
                        } else {
                            Modifier
                        },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SpeakerMark(paragraph.speakerMark)
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
        }
        if (paragraph.isUser != true) autoNamed.of(paragraph)?.let { AutoNamedMarker(it, autoNamed.onUndo) }
        paragraph.sourceNote?.let {
            val note =
                when (it) {
                    "marked" -> stringResource(R.string.segment_source_marked)
                    "voice" -> stringResource(R.string.segment_source_voice)
                    "provider" -> stringResource(R.string.segment_source_provider)
                    else -> it
                }
            Text(
                "· $note",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The auto-applied names of this conversation and how to take one back. */
private class AutoNamedMarks(
    val named: List<NameSuggestion>,
    val onUndo: (NameSuggestion) -> Unit,
) {
    fun of(paragraph: Paragraph) = named.firstOrNull { it.names(paragraph.voiceId, paragraph.personId) }
}

/** "named for you" beside a speaker the server named by itself, with a one-tap Undo. */
@Composable
private fun AutoNamedMarker(
    suggestion: NameSuggestion,
    onUndo: (NameSuggestion) -> Unit,
) {
    Text(
        "· " + stringResource(R.string.auto_named_marker),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val undoLabel = stringResource(R.string.auto_named_undo_format, suggestion.name)
    Text(
        stringResource(R.string.action_undo),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .clickable(role = Role.Button) { onUndo(suggestion) }
                .semantics { contentDescription = undoLabel },
    )
}

@Composable
private fun SpeakerTag(
    text: String,
    container: Color,
    content: Color,
) {
    Surface(shape = MaterialTheme.shapes.extraSmall, color = container) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ParagraphItem(
    paragraph: Paragraph,
    autoNamed: AutoNamedMarks,
    voiceNumber: Int,
    playing: Boolean,
    onName: (Pair<String, String>) -> Unit,
    onOpenPerson: (String) -> Unit,
    onSave: (String, String) -> Unit,
    onMark: (Long, Boolean?) -> Unit,
    onKind: ((Long, String?) -> Unit)?,
    onPlay: (() -> Unit)?,
    onMemory: (Paragraph) -> Unit,
) {
    var markMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Column(
        if (playing) {
            Modifier.background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small).padding(8.dp)
        } else {
            Modifier
        },
    ) {
        if (paragraph.showSpeaker) {
            SpeakerLabel(paragraph, autoNamed, voiceNumber, onName = onName, onOpenPerson = onOpenPerson)
        }
        Row {
            Column(
                Modifier
                    .width(56.dp)
                    .then(if (onPlay != null) Modifier.clickable(onClick = onPlay) else Modifier),
            ) {
                Text(
                    paragraph.time,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (onPlay != null) MaterialTheme.colorScheme.primary else Color.Unspecified,
                )
                if (playing) {
                    PlayPauseGlyph(
                        playing = true,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        size = 12.dp,
                        modifier = Modifier.padding(top = 4.dp),
                        description = stringResource(R.string.audio_playing_here),
                    )
                }
            }
            Column {
                paragraph.speech?.let { SpeechChip(it, Modifier.padding(bottom = 2.dp)) }
                Box {
                    Text(
                        paragraph.text,
                        style = NytkaTheme.typography.readingBody,
                        color =
                            if (paragraph.speech?.takeUnless { it.guess }?.tag == SpeechTag.Media) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                Color.Unspecified
                            },
                        modifier =
                            Modifier.combinedClickable(onClick = {}, onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                markMenu = true
                            }),
                    )
                    DropdownMenu(expanded = markMenu, onDismissRequest = { markMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.line_save_memory)) }, onClick = {
                            markMenu = false
                            onMemory(paragraph)
                        })
                        HorizontalDivider()
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
                        if (onKind != null) {
                            HorizontalDivider()
                            listOf(
                                R.string.speech_mark_person to KIND_PERSON,
                                R.string.speech_mark_media to KIND_MEDIA,
                                R.string.speech_mark_call to KIND_CALL,
                                R.string.speech_mark_clear to null,
                            ).forEach { (label, kind) ->
                                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                                    markMenu = false
                                    onKind(paragraph.segmentId, kind)
                                })
                            }
                        }
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
            .heightIn(min = 48.dp)
            .clickable { editing = true }
            .padding(start = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Filled.Star,
            contentDescription = stringResource(R.string.bookmark_content_description),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            mark.note ?: stringResource(R.string.add_a_note),
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
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(filled(initial)) }
    val people = knownPeople()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bookmark_note_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it.copy(text = it.text.take(ConversationViewModel.MAX_BOOKMARK_NOTE)) },
                    label = { Text(stringResource(R.string.note_label)) },
                    supportingText = { Text(stringResource(R.string.bookmark_note_hint)) },
                )
                NameCompletions(field, onValueChange = { field = it }, people = people)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(field.text) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Names one voice; every line of it, past and future, shows the name. Below the field sit the server's [guesses] for
 * this voice and known [people] that fit what is typed; a known name joins the voice to that person.
 */
@Composable
internal fun NameVoiceDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    guesses: List<String> = emptyList(),
    people: List<Person> = knownPeople(),
) {
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(filled(initial)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.who_is_this_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NytkaTextField(
                    value = field,
                    onValueChange = { field = it.copy(text = it.text.take(MAX_NAME)) },
                    label = stringResource(R.string.name),
                    supporting = stringResource(R.string.speaker_name_hint),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                NameChips(nameSuggestions(people, field.text, guesses), onPick = { field = filled(it) })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(field.text) },
                enabled = field.text.isNotBlank(),
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
        title = { Text(stringResource(R.string.action_rename)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(MAX_TITLE) },
                label = { Text(stringResource(R.string.title_label)) },
                supportingText = { Text(stringResource(R.string.title_hint)) },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val MAX_TITLE = 120

private const val KIND_PERSON = "person"
private const val KIND_MEDIA = "media"
private const val KIND_CALL = "call"
