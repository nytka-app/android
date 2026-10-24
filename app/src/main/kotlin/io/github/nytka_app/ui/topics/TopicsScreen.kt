package io.github.nytka_app.ui.topics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.core.api.LifeItem
import io.github.nytka_app.core.api.Topic
import io.github.nytka_app.core.api.TopicMoment
import io.github.nytka_app.ui.ListFailure
import io.github.nytka_app.ui.ListState
import io.github.nytka_app.ui.ListStateKind
import io.github.nytka_app.ui.Monogram
import io.github.nytka_app.ui.conversations.Formatting
import io.github.nytka_app.ui.theme.ReadingColumn
import java.time.LocalDate
import java.time.ZoneId

/**
 * Topics: a page for each tag and person the server sees across conversations, filtered by kind and by name. A row
 * says how many moments it holds and how many commitments are still open; it opens the topic.
 */
@Composable
fun TopicsScreen(
    onOpenTopic: (String) -> Unit,
    viewModel: TopicsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReadingColumn {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "filters") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        label = { Text(stringResource(R.string.topics_filter)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KindChip(R.string.search_filter_all, state.kind == null) { viewModel.setKind(null) }
                        KindChip(
                            R.string.topics_kind_tags,
                            state.kind == Topic.KIND_TAG,
                        ) { viewModel.setKind(Topic.KIND_TAG) }
                        KindChip(R.string.topics_kind_people, state.kind == Topic.KIND_PERSON) {
                            viewModel.setKind(Topic.KIND_PERSON)
                        }
                    }
                }
            }
            state.error?.let { error ->
                item(key = "error") { ListFailure(error, state.offline, onRetry = viewModel::refresh) }
            }
            if (state.offline) {
                item(key = "offline") {
                    ListState(
                        ListStateKind.Offline,
                        stringResource(R.string.list_offline_title),
                        body = stringResource(R.string.list_offline_body),
                    )
                }
            }
            if (state.loading) {
                item(
                    key = "loading",
                ) { ListState(ListStateKind.Loading, stringResource(R.string.list_loading)) }
            }
            if (state.nothing) {
                item(key = "empty") {
                    ListState(
                        ListStateKind.Empty,
                        stringResource(R.string.topics_empty_title),
                        body = stringResource(R.string.topics_empty_body),
                    )
                }
            }
            items(state.shown, key = { it.id }) { topic -> TopicRow(topic) { onOpenTopic(topic.id) } }
        }
    }
}

@Composable
private fun KindChip(
    label: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(stringResource(label)) })
}

@Composable
private fun TopicRow(
    topic: Topic,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Monogram(if (topic.kind == Topic.KIND_TAG) "#" else topic.name, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(topic.name, style = MaterialTheme.typography.titleMedium)
            topic.summary?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                counts(topic.moments, topic.openCommitments),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider()
}

/** "12 moments · 2 open commitments"; no open commitments says nothing about them. */
@Composable
private fun counts(
    moments: Int,
    open: Int,
): String {
    val parts = listOf(pluralStringResource(R.plurals.topic_moments, moments, moments))
    val commitments =
        if (open >
            0
        ) {
            listOf(pluralStringResource(R.plurals.topic_open_commitments, open, open))
        } else {
            emptyList()
        }
    return (parts + commitments).joinToString(" · ")
}

/**
 * One topic: its summary, the commitments still open in it, then its moments, newest first, each opening its
 * conversation at that line. More moments load as the list reaches its end.
 */
@Composable
fun TopicScreen(
    onOpenMoment: (conversationId: String, atMs: Long?) -> Unit,
    viewModel: TopicViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val zone = viewModel.zone
    val today = LocalDate.now(zone)
    ReadingColumn {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            state.error?.let { error -> item(key = "error") { ListFailure(error, offline = false, onRetry = {}) } }
            if (state.loading) {
                item(
                    key = "loading",
                ) { ListState(ListStateKind.Loading, stringResource(R.string.list_loading)) }
            }
            val page = state.page ?: return@LazyColumn
            item(key = "head") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        page.name,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        counts(page.moments, page.openCommitments),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    page.summary?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                }
            }
            if (page.commitments.isNotEmpty()) {
                item(key = "commitments-title") { SectionTitle(R.string.topic_commitments) }
                items(page.commitments, key = { "c-${it.id}" }) { item -> CommitmentRow(item, onOpenMoment) }
            }
            if (page.timeline.isNotEmpty()) {
                item(key = "moments-title") { SectionTitle(R.string.topic_moments_title) }
                items(page.timeline, key = { "${it.conversationId}-${it.at}" }) { moment ->
                    MomentRow(moment, zone, today, onOpenMoment)
                }
            }
            if (page.nextBefore !=
                null
            ) {
                item(key = "more") { LaunchedEffect(page.timeline.size) { viewModel.loadMore() } }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun CommitmentRow(
    item: LifeItem,
    onOpenMoment: (String, Long?) -> Unit,
) {
    val line = item.evidence.firstOrNull()
    val conversation = line?.conversationId ?: item.conversationId
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(
                if (conversation != null) {
                    Modifier.clickable { onOpenMoment(conversation, line?.at?.let(Formatting::parse)?.toEpochMilli()) }
                } else {
                    Modifier
                },
            ).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(item.text, style = MaterialTheme.typography.bodyLarge)
        item.due?.let {
            Text(
                stringResource(R.string.item_due_format, it.take(DATE_LENGTH)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MomentRow(
    moment: TopicMoment,
    zone: ZoneId,
    today: LocalDate,
    onOpenMoment: (String, Long?) -> Unit,
) {
    val at = Formatting.parse(moment.at)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onOpenMoment(moment.conversationId, at?.toEpochMilli()) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            listOfNotNull(
                at?.let {
                    "${Formatting.dayTitle(
                        it.atZone(zone).toLocalDate(),
                        today,
                    )} · ${Formatting.clock(it, zone)}"
                },
                moment.title,
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        moment.line?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private const val DATE_LENGTH = 10
