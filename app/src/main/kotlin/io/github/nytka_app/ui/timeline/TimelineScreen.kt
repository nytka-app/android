package io.github.nytka_app.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.core.api.LifeItem
import io.github.nytka_app.core.api.Salience
import io.github.nytka_app.core.api.Scene
import io.github.nytka_app.ui.ListFailure
import io.github.nytka_app.ui.ListState
import io.github.nytka_app.ui.ListStateKind
import io.github.nytka_app.ui.conversations.Formatting
import io.github.nytka_app.ui.theme.ReadingColumn
import io.github.nytka_app.ui.today.CorrectionSnackbar
import io.github.nytka_app.ui.today.EarlierStrip
import io.github.nytka_app.ui.today.EvidenceSheet
import io.github.nytka_app.ui.today.ItemActions
import io.github.nytka_app.ui.today.LifeItemCard
import io.github.nytka_app.ui.today.LineActions
import io.github.nytka_app.ui.today.TopicChips
import java.time.LocalDate
import java.time.ZoneId

/**
 * The Timeline: one day as scenes, oldest first, each with when, who and where, two lines of summary and its items.
 * A scene opens its conversation (audio and transcript); an item opens where it comes from. Short fragments fold into
 * one line; a private scene shows only that it is private until tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    onOpenMoment: (conversationId: String, atMs: Long?) -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenConversations: () -> Unit,
    onOpenTopic: (String) -> Unit = {},
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var open by remember { mutableStateOf<LifeItem?>(null) }
    CorrectionSnackbar(state.notice, snackbar, onUndo = viewModel::undo, onShown = viewModel::noticeShown)
    val actions =
        ItemActions(onOpen = { open = it }, onNotImportant = viewModel::notImportant, onOpenPerson = onOpenPerson)
    val zone = viewModel.zone

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            ReadingColumn {
                Column {
                    DayBar(
                        Formatting.dayTitle(state.date, state.today),
                        canGoForward = state.canGoForward,
                        onPrevious = viewModel::previousDay,
                        onNext = viewModel::nextDay,
                        onToday = viewModel::showToday.takeIf { state.date != state.today },
                    )
                    PullToRefreshBox(isRefreshing = false, onRefresh = viewModel::refresh) {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
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
                            if (state.loading && state.rows.isEmpty()) {
                                item(key = "loading") {
                                    ListState(ListStateKind.Loading, stringResource(R.string.list_loading))
                                }
                            }
                            if (state.nothingHeard) {
                                item(key = "empty") {
                                    ListState(
                                        ListStateKind.Empty,
                                        stringResource(R.string.timeline_empty_title),
                                        body = stringResource(R.string.timeline_empty_body),
                                    )
                                }
                            }
                            items(state.rows, key = ::keyOf) { row ->
                                when (row) {
                                    is TimelineRow.Of -> SceneItem(row.scene, zone, actions, onOpenMoment, onOpenTopic)
                                    is TimelineRow.Fragments -> Fragments(row.scenes, zone, onOpenMoment)
                                }
                            }
                            item(key = "all") {
                                TextButton(onClick = onOpenConversations, modifier = Modifier.padding(8.dp)) {
                                    Text(stringResource(R.string.timeline_all_conversations))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    open?.let { item ->
        EvidenceSheet(
            item,
            zone,
            onDismiss = { open = null },
            onOpenMoment = { id, at ->
                open = null
                onOpenMoment(id, at)
            },
            onNotImportant = {
                open = null
                viewModel.notImportant(it)
            },
            lines =
                LineActions(
                    onNotMe = {
                        open = null
                        viewModel.notMe(it)
                    },
                    onOtherPerson = { line, person ->
                        open = null
                        viewModel.otherPerson(line, person)
                    },
                ),
            onOpenTopic = {
                open = null
                onOpenTopic(it)
            },
        )
    }
}

private fun keyOf(row: TimelineRow): String =
    when (row) {
        is TimelineRow.Of -> row.scene.id
        is TimelineRow.Fragments -> "fragments-${row.scenes.first().id}"
    }

@Composable
private fun DayBar(
    title: String,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: (() -> Unit)?,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.timeline_previous),
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        onToday?.let { TextButton(onClick = it) { Text(stringResource(R.string.timeline_back_to_today)) } }
        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.timeline_next),
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun SceneItem(
    scene: Scene,
    zone: ZoneId,
    actions: ItemActions,
    onOpenMoment: (String, Long?) -> Unit,
    onOpenTopic: (String) -> Unit,
) {
    var revealed by rememberSaveable(scene.id) { mutableStateOf(scene.sensitivity == null) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { if (revealed) onOpenMoment(scene.conversationId, null) else revealed = true }
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                listOfNotNull(timeRange(scene, zone), scene.place).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            if (!revealed) {
                Text(
                    stringResource(R.string.timeline_private_format, scene.sensitivity.orEmpty()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            scene.title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            if (scene.people.isNotEmpty()) {
                Text(
                    scene.people.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            scene.summary?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (revealed) {
            scene.items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VitalMark(item.salience == Salience.VITAL)
                    LifeItemCard(item, zone, actions, Modifier.weight(1f), dim = item.salience == Salience.NOISE)
                }
            }
            TopicChips(scene.topics, onOpenTopic, Modifier.padding(horizontal = 16.dp))
            EarlierStrip(
                scene.related,
                zone,
                LocalDate.now(zone),
                onOpenMoment,
                Modifier.padding(top = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            )
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

/** A dot before a vital item, a gap before the rest, so the column of items stays aligned. */
@Composable
private fun VitalMark(vital: Boolean) {
    val description = stringResource(R.string.timeline_vital)
    Box(Modifier.padding(start = 16.dp).size(8.dp)) {
        if (vital) {
            Spacer(
                Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .semantics { contentDescription = description },
            )
        }
    }
}

@Composable
private fun Fragments(
    scenes: List<Scene>,
    zone: ZoneId,
    onOpenMoment: (String, Long?) -> Unit,
) {
    var expanded by rememberSaveable(scenes.first().id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            pluralStringResource(R.plurals.timeline_fragments, scenes.size, scenes.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
        )
        if (expanded) {
            scenes.forEach { scene ->
                Text(
                    listOfNotNull(timeRange(scene, zone), scene.title).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onOpenMoment(scene.conversationId, null) }
                            .padding(horizontal = 32.dp, vertical = 14.dp),
                )
            }
        }
        HorizontalDivider()
    }
}

private fun timeRange(
    scene: Scene,
    zone: ZoneId,
): String? {
    val start = Formatting.parse(scene.startedAt) ?: return null
    val end = Formatting.parse(scene.endedAt) ?: return Formatting.clock(start, zone)
    return Formatting.timeRange(start, end, zone)
}
