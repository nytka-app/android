package io.github.nytka_app.ui.today

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.core.api.DayPart
import io.github.nytka_app.core.api.LifeItem
import io.github.nytka_app.ui.DecisionBadge
import io.github.nytka_app.ui.ListFailure
import io.github.nytka_app.ui.ListState
import io.github.nytka_app.ui.ListStateKind
import io.github.nytka_app.ui.theme.ReadingColumn

/**
 * Today: what matters from today, at most seven vital items under Morning, Day and Evening, each opening where it
 * comes from. Below them, "More" leads to Tasks, Memories, People and Ask.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onOpenMoment: (conversationId: String, atMs: Long?) -> Unit,
    onOpenPerson: (String) -> Unit,
    links: TodayLinks,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var open by remember { mutableStateOf<LifeItem?>(null) }
    CorrectionSnackbar(state.notice, snackbar, onUndo = viewModel::undo, onShown = viewModel::noticeShown)
    val actions =
        ItemActions(
            onOpen = { open = it },
            onNotImportant = viewModel::notImportant,
            onOpenPerson = onOpenPerson,
        )

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            ReadingColumn {
                PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
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
                        if (state.loading && state.empty) {
                            item(
                                key = "loading",
                            ) { ListState(ListStateKind.Loading, stringResource(R.string.list_loading)) }
                        }
                        if (state.nothingYet) {
                            item(key = "empty") {
                                ListState(
                                    ListStateKind.Empty,
                                    stringResource(R.string.today_empty_title),
                                    body = stringResource(R.string.today_empty_body),
                                )
                            }
                        }
                        state.sections.forEach { section ->
                            item(key = "part-${section.part}") { PartTitle(section.part) }
                            items(section.items, key = { "${it.kind}-${it.id}" }) { item ->
                                LifeItemCard(item, viewModel.zone, actions)
                            }
                        }
                        item(key = "more") { More(links) }
                    }
                }
            }
        }
    }

    open?.let { item ->
        EvidenceSheet(
            item,
            viewModel.zone,
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
        )
    }
}

@Composable
private fun PartTitle(part: String) {
    Text(
        stringResource(
            when (part) {
                DayPart.MORNING -> R.string.today_part_morning
                DayPart.EVENING -> R.string.today_part_evening
                else -> R.string.today_part_day
            },
        ),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun More(links: TodayLinks) {
    Text(
        stringResource(R.string.today_more),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp).semantics { heading() },
    )
    HorizontalDivider()
    MoreRow(R.drawable.ic_tab_tasks, R.string.tab_tasks, links.onOpenTasks)
    MoreRow(R.drawable.ic_tab_memories, R.string.tab_memories, links.onOpenMemories)
    MoreRow(R.drawable.ic_tab_people, R.string.tab_people, links.onOpenPeople, links.peopleBadge)
    MoreRow(R.drawable.ic_tab_ask, R.string.tab_ask, links.onOpenAsk)
}

@Composable
private fun MoreRow(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    onClick: () -> Unit,
    badge: Int = 0,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (badge > 0) {
            val needsYou = pluralStringResource(R.plurals.needs_you_format, badge, badge)
            DecisionBadge(badge, Modifier.semantics { contentDescription = needsYou })
        }
    }
}
