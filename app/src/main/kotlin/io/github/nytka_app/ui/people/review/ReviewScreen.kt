package io.github.nytka_app.ui.people.review

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.ReviewContextLine
import io.github.nytka_app.core.api.ReviewItem
import io.github.nytka_app.ui.DecisionBadge
import io.github.nytka_app.ui.DecisionCard
import io.github.nytka_app.ui.ListFailure
import io.github.nytka_app.ui.ListState
import io.github.nytka_app.ui.ListStateKind
import io.github.nytka_app.ui.people.AcceptAllButton
import io.github.nytka_app.ui.people.SuggestionWording
import io.github.nytka_app.ui.people.addedAllText
import io.github.nytka_app.ui.people.cards.ClipState
import io.github.nytka_app.ui.people.cards.clipKey
import io.github.nytka_app.ui.people.lastSeenText
import io.github.nytka_app.ui.people.question
import io.github.nytka_app.ui.unreachable

/** The People screen's inbox icon: hidden while the server has no review list or nothing waits. */
@Composable
fun ReviewInboxAction(
    onClick: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The inbox is answered on its own screen: read the count again when the list is back in front.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    if (!state.available || state.count == 0) return
    IconButton(onClick = onClick) {
        BadgedBox(badge = { DecisionBadge(state.count) }) {
            Icon(Icons.Filled.Email, contentDescription = stringResource(R.string.review_inbox_content_description))
        }
    }
}

/** The review inbox: one row per thing waiting for an answer, newest first. A row opens its conversation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clip by viewModel.clip.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stopClip() }
    DisposableEffect(Unit) { onDispose { viewModel.stopClip() } }
    LaunchedEffect(state.note) {
        state.note?.let {
            snackbar.showSnackbar(reviewNoticeText(context, it))
            viewModel.noteShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review_title)) },
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
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.error?.let { error ->
                    item(key = "error") {
                        ListFailure(
                            reviewNoticeText(context, error),
                            offline = (error as? ReviewNotice.Failed)?.kind?.unreachable == true,
                            onRetry = viewModel::refresh,
                        )
                    }
                }
                if (state.error == null && state.items.isEmpty()) {
                    item(key = "state") {
                        if (state.loading) {
                            ListState(ListStateKind.Loading, stringResource(R.string.list_loading))
                        } else {
                            ListState(
                                ListStateKind.Empty,
                                stringResource(R.string.review_empty),
                                body = stringResource(R.string.review_empty_body),
                            )
                        }
                    }
                }
                items(state.items, key = { it.kind + "/" + it.id }) { item ->
                    ReviewRowView(
                        item,
                        state.tagPeople[item.id],
                        state.roles,
                        state.speech,
                        if (state.acceptAllAvailable) state.sameNames[item.id] ?: 1 else 1,
                        !state.busy,
                        ClipView(state.clips, clip, state.expiredClips, viewModel::toggleClip),
                        onOpenConversation,
                        onOpenPerson,
                        viewModel::accept,
                        viewModel::reject,
                        viewModel::acceptAll,
                    )
                }
            }
        }
    }
}

private const val SPEECH_LINES = 4

/** What a card needs of the inbox's clips: [on] is `review-clips` on the server. */
private class ClipView(
    val on: Boolean,
    val state: ClipState,
    val expired: Set<String>,
    val onToggle: (ReviewItem) -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewRowView(
    item: ReviewItem,
    personName: String?,
    roles: Boolean,
    speech: Boolean,
    sameName: Int,
    acceptAllEnabled: Boolean,
    clips: ClipView,
    onOpenConversation: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onAccept: (ReviewItem) -> Unit,
    onReject: (ReviewItem) -> Unit,
    onAcceptAll: (ReviewItem) -> Unit,
) {
    val row = ReviewRow.of(item, roles, sameName, speech) ?: return
    val question = reviewQuestion(row, personName)
    // A person's tag opens the person; every other row its conversation.
    val opens: (() -> Unit)? =
        when {
            row is ReviewRow.Tag && row.personId != null -> ({ onOpenPerson(row.personId) })
            item.conversationId.isNotEmpty() -> ({ onOpenConversation(item.conversationId) })
            else -> null
        }
    val details =
        listOfNotNull(
            (row as? ReviewRow.VoiceMatch)?.percent?.let { stringResource(R.string.review_similarity_format, it) },
            item.conversationTitle?.takeIf { it.isNotBlank() },
            lastSeenText(item.at.ifEmpty { null }),
        ).joinToString(" · ")
    DecisionCard(question, Modifier.padding(horizontal = 16.dp)) {
        Column(
            Modifier.clickable(enabled = opens != null, role = Role.Button) { opens?.invoke() },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (details.isNotEmpty()) {
                Text(
                    details,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ReviewText(row, item.text, if (clips.on) item.context else emptyList())
        }
        ReviewClipRow(item, clips)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Button(onClick = { onAccept(item) }) { Text(stringResource(R.string.review_yes)) }
            OutlinedButton(onClick = { onReject(item) }) {
                Text(
                    stringResource(if (row is ReviewRow.VoiceMatch) R.string.review_not_them else R.string.review_no),
                )
            }
            if (row is ReviewRow.NameSuggestion && row.named && row.sameName > 1) {
                row.name?.let { AcceptAllButton(row.sameName, it, acceptAllEnabled, { onAcceptAll(item) }) }
            }
        }
    }
}

/** The play button, or the words for audio past retention. A tag rests on no line, so it has no audio to miss. */
@Composable
private fun ReviewClipRow(
    item: ReviewItem,
    clips: ClipView,
) {
    if (!clips.on || item.kind == ReviewItem.KIND_TAG) return
    val key = clipKey(item.kind, item.id)
    if (item.clip != null && key !in clips.expired) {
        ClipButton(clips.state.key == key && clips.state.playing) { clips.onToggle(item) }
    } else {
        Text(
            stringResource(R.string.review_clip_expired),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ClipButton(
    playing: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalButton(onClick = onClick) {
        Icon(
            if (playing) Icons.Filled.Close else Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(if (playing) R.string.cards_stop_clip else R.string.cards_play_clip))
    }
}

/** The lines around the evidence, oldest first; the evidence lines stand out. */
@Composable
private fun ContextLines(lines: List<ReviewContextLine>) {
    val colors = MaterialTheme.colorScheme
    lines.zip(ContextSpeaker.of(lines)).forEach { (line, who) ->
        val speaker =
            when (who) {
                ContextSpeaker.You -> stringResource(R.string.speaker_you)
                is ContextSpeaker.Person -> who.name
                is ContextSpeaker.Voice -> stringResource(R.string.speaker_voice_format, who.number)
                ContextSpeaker.Someone -> stringResource(R.string.review_unknown_speaker)
            }
        Text(
            stringResource(R.string.review_context_line_format, speaker, line.text),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (line.evidence) FontWeight.SemiBold else null,
            color = if (line.evidence) colors.primary else colors.onSurfaceVariant,
            maxLines = if (line.evidence) 4 else 2,
        )
    }
}

/**
 * The lines around the evidence where the server sends them; else a stretch of speech shows its lines, any other row
 * the text of its line.
 */
@Composable
private fun ReviewText(
    row: ReviewRow,
    text: String,
    context: List<ReviewContextLine>,
) {
    if (context.isNotEmpty()) {
        ContextLines(context)
    } else if (row is ReviewRow.Speech) {
        row.lines.take(SPEECH_LINES).forEach {
            Text(stringResource(R.string.source_quote, it), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        }
    } else if (text.isNotBlank() && row !is ReviewRow.Tag) {
        Text(stringResource(R.string.source_quote, text), style = MaterialTheme.typography.bodyMedium, maxLines = 4)
    } else if (text.isNotBlank()) {
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 4)
    }
}

@Composable
private fun reviewQuestion(
    row: ReviewRow,
    personName: String?,
): String {
    val someone = stringResource(R.string.review_someone)
    return when (row) {
        is ReviewRow.NameSuggestion -> SuggestionWording.of(row.name ?: someone, row.role, row.named).question()
        is ReviewRow.VoiceMatch -> stringResource(R.string.review_name_question_format, row.name ?: someone)
        is ReviewRow.Label ->
            stringResource(if (row.isUser) R.string.review_label_yours else R.string.review_label_other)

        is ReviewRow.Speech ->
            stringResource(if (row.call) R.string.review_speech_call else R.string.review_speech_media)

        is ReviewRow.Tag ->
            if (row.personId == null) {
                stringResource(R.string.review_tag_conversation_format, row.tag)
            } else {
                stringResource(
                    R.string.review_tag_person_format,
                    personName ?: stringResource(R.string.review_this_person),
                    row.tag,
                )
            }
    }
}

internal fun reviewNoticeText(
    context: Context,
    notice: ReviewNotice,
): String =
    when (notice) {
        ReviewNotice.AlreadyAnswered -> context.getString(R.string.review_already_answered)
        ReviewNotice.TagLimit -> context.getString(R.string.tag_too_many)
        is ReviewNotice.MergedInto -> context.getString(R.string.role_merged_format, notice.name)
        is ReviewNotice.AddedAll -> addedAllText(context, notice.name, notice.accepted, notice.skipped)
        ReviewNotice.AcceptAllDisagree -> context.getString(R.string.accept_all_disagree)
        ReviewNotice.ClipFailed -> context.getString(R.string.cards_clip_failed)
        is ReviewNotice.Failed ->
            when {
                notice.kind == FailureKind.NotFound && notice.item -> context.getString(R.string.item_no_longer_exists)
                notice.kind == FailureKind.NotFound || notice.kind == FailureKind.Unsupported ->
                    context.getString(R.string.server_needs_update)

                notice.kind == FailureKind.Forbidden -> context.getString(R.string.app_needs_admin_token)
                else -> notice.message
            }
    }
