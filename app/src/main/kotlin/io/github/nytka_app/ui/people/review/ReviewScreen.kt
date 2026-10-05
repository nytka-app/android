package io.github.nytka_app.ui.people.review

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.ReviewItem
import io.github.nytka_app.ui.people.lastSeenText

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
        BadgedBox(badge = { Badge { Text(if (state.count > 99) "99+" else state.count.toString()) } }) {
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
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
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
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                state.error?.let { error ->
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Text(reviewNoticeText(context, error), color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.action_retry)) }
                        }
                    }
                }
                if (state.error == null && state.items.isEmpty() && !state.loading) {
                    item {
                        Text(
                            stringResource(R.string.review_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                items(state.items, key = { it.kind + "/" + it.id }) { item ->
                    ReviewRowView(
                        item,
                        state.tagPeople[item.id],
                        onOpenConversation,
                        onOpenPerson,
                        viewModel::accept,
                        viewModel::reject,
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ReviewRowView(
    item: ReviewItem,
    personName: String?,
    onOpenConversation: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onAccept: (ReviewItem) -> Unit,
    onReject: (ReviewItem) -> Unit,
) {
    val row = ReviewRow.of(item) ?: return
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
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = opens != null) { opens?.invoke() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(question, style = MaterialTheme.typography.titleMedium)
        if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall)
        if (item.text.isNotBlank()) Text(item.text, style = MaterialTheme.typography.bodyMedium, maxLines = 4)
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onAccept(item) }) { Text(stringResource(R.string.review_yes)) }
            OutlinedButton(onClick = { onReject(item) }) {
                Text(
                    stringResource(if (row is ReviewRow.VoiceMatch) R.string.review_not_them else R.string.review_no),
                )
            }
        }
    }
}

@Composable
private fun reviewQuestion(
    row: ReviewRow,
    personName: String?,
): String {
    val someone = stringResource(R.string.review_someone)
    return when (row) {
        is ReviewRow.NameSuggestion -> stringResource(R.string.review_name_question_format, row.name ?: someone)
        is ReviewRow.VoiceMatch -> stringResource(R.string.review_name_question_format, row.name ?: someone)
        is ReviewRow.Label ->
            stringResource(if (row.isUser) R.string.review_label_yours else R.string.review_label_other)

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
        is ReviewNotice.Failed ->
            when {
                notice.kind == FailureKind.NotFound && notice.item -> context.getString(R.string.item_no_longer_exists)
                notice.kind == FailureKind.NotFound || notice.kind == FailureKind.Unsupported ->
                    context.getString(R.string.server_needs_update)

                notice.kind == FailureKind.Forbidden -> context.getString(R.string.app_needs_admin_token)
                else -> notice.message
            }
    }
