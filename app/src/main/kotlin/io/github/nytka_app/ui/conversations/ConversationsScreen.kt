package io.github.nytka_app.ui.conversations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.nytka_app.R
import io.github.nytka_app.ui.ListFailure
import io.github.nytka_app.ui.ListState
import io.github.nytka_app.ui.ListStateKind
import io.github.nytka_app.ui.ScreenEnter
import io.github.nytka_app.ui.ScreenExit
import io.github.nytka_app.ui.StatusCard
import io.github.nytka_app.ui.StatusUiState
import io.github.nytka_app.ui.search.SEARCH_ENABLED
import io.github.nytka_app.ui.search.SearchScreen
import io.github.nytka_app.ui.tags.ListEmpty
import io.github.nytka_app.ui.tags.TagFilterAction
import io.github.nytka_app.ui.tags.TagRow
import io.github.nytka_app.ui.tags.tagFilterChipItem
import io.github.nytka_app.ui.tags.tagFilterEmptyText
import kotlinx.coroutines.flow.Flow

/**
 * The Conversations tab: the list, a conversation opened from it, and search. A conversation that another
 * tab asks for arrives in [openRequests] and opens over the list, not over what this tab showed last.
 */
@Composable
fun ConversationsTab(
    status: StatusUiState,
    onMute: (Boolean) -> Unit,
    openRequests: Flow<String>,
    onOpenPerson: (String) -> Unit,
    /** A tag the list opens filtered by, once; null shows every conversation. */
    initialTag: String? = null,
) {
    val navController = rememberNavController()
    LaunchedEffect(navController, openRequests) {
        openRequests.collect { id -> navController.navigate("conversation/$id") { popUpTo("list") } }
    }
    LaunchedEffect(navController, initialTag) {
        initialTag?.let { navController.getBackStackEntry("list").savedStateHandle[TAG_REQUEST] = it }
    }
    NavHost(
        navController,
        startDestination = "list",
        enterTransition = { ScreenEnter },
        exitTransition = { ScreenExit },
        popEnterTransition = { ScreenEnter },
        popExitTransition = { ScreenExit },
    ) {
        composable("list") { entry ->
            // A conversation deleted on its own screen comes back as this entry's result.
            val deleted by entry.savedStateHandle.getStateFlow<String?>(DELETED, null).collectAsStateWithLifecycle()
            // So does a tag tapped on a conversation: the list filters by it.
            val tagRequest by entry.savedStateHandle
                .getStateFlow<String?>(
                    TAG_REQUEST,
                    null,
                ).collectAsStateWithLifecycle()
            ConversationsScreen(
                status,
                onMute,
                deleted,
                tagRequest = tagRequest,
                onTagRequestHandled = { entry.savedStateHandle[TAG_REQUEST] = null },
                onOpen = { navController.navigate("conversation/$it") },
                onSearch = { navController.navigate("search") },
            )
        }
        composable("conversation/{id}") { entry ->
            ConversationScreen(
                developerMode = status.developerMode,
                onBack = { navController.popBackStack() },
                onOpenPerson = onOpenPerson,
                onOpenTag = { name ->
                    navController.getBackStackEntry("list").savedStateHandle[TAG_REQUEST] = name
                    navController.popBackStack("list", inclusive = false)
                },
                onDeleted = {
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        DELETED,
                        entry.arguments?.getString("id"),
                    )
                    navController.popBackStack()
                },
            )
        }
        if (SEARCH_ENABLED) {
            composable("search") {
                SearchScreen(
                    onOpenConversation = { navController.navigate("conversation/$it") },
                    onOpenPerson = onOpenPerson,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private const val DELETED = "deleted"
private const val TAG_REQUEST = "tagRequest"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationsScreen(
    status: StatusUiState,
    onMute: (Boolean) -> Unit,
    deleted: String?,
    onOpen: (String) -> Unit,
    onSearch: () -> Unit,
    tagRequest: String? = null,
    onTagRequestHandled: () -> Unit = {},
    viewModel: ConversationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val mediaFilter by viewModel.mediaFilter.collectAsStateWithLifecycle()
    LaunchedEffect(deleted) { deleted?.let(viewModel::forget) }
    LaunchedEffect(tagRequest) {
        tagRequest?.let {
            viewModel.showTag(it)
            onTagRequestHandled()
        }
    }
    // Refreshes when the screen is shown again and every 30 seconds while it is, but never in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.keepFresh() }
    }
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (SEARCH_ENABLED || tagFilter || mediaFilter) {
                item {
                    TopActions(
                        tagFilter,
                        mediaFilter = mediaFilter && !state.hideMedia,
                        onHideMedia = viewModel::hideMedia,
                        onPickTag = viewModel::showTag,
                        onSearch = onSearch.takeIf { SEARCH_ENABLED },
                    )
                }
            }
            tagFilterChipItem(state.tag, onClear = viewModel::clearTag)
            if (state.hideMedia) item(key = "media-filter") { HideMediaChip(onClear = viewModel::showMedia) }
            item { StatusCard(status, onMute, notice) }
            conversationRows(state, viewModel, onOpen)
        }
    }
}

/** The rows below the status card, by day, or whatever stands in for them: loading, the failure, the empty text. */
private fun LazyListScope.conversationRows(
    state: ConversationsUiState,
    viewModel: ConversationsViewModel,
    onOpen: (String) -> Unit,
) {
    state.error?.let { error ->
        item(key = "error") { ListFailure(error, state.offline, onRetry = viewModel::refresh) }
    }
    if (state.loading && state.days.isEmpty() && state.error == null) {
        item(key = "loading") { ListState(ListStateKind.Loading, stringResource(R.string.list_loading)) }
    }
    state.days.forEach { day ->
        stickyHeader(key = "day-${day.title}") { DayHeader(day.title) }
        items(day.rows, key = { it.id }) { row -> ConversationCard(row, onOpen) }
    }
    if (state.days.isNotEmpty() && !state.endReached) {
        item(key = "more") { LaunchedEffect(state.days.sumOf { it.rows.size }) { viewModel.loadMore() } }
    }
    when (state.empty) {
        ListEmpty.Empty ->
            item {
                ListState(
                    ListStateKind.Empty,
                    stringResource(
                        if (state.hideMedia) R.string.speech_list_only_media else R.string.no_conversations_yet,
                    ),
                    body = if (state.hideMedia) null else stringResource(R.string.no_conversations_body),
                )
            }
        ListEmpty.EmptyForTag ->
            item { ListState(ListStateKind.Empty, tagFilterEmptyText(state.tag.orEmpty())) }
        ListEmpty.NotEmpty -> Unit
    }
}

/** The tab has no top bar: its actions sit in a row above the list. */
@Composable
private fun TopActions(
    tagFilter: Boolean,
    mediaFilter: Boolean,
    onHideMedia: () -> Unit,
    onPickTag: (String) -> Unit,
    onSearch: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mediaFilter) TextButton(onClick = onHideMedia) { Text(stringResource(R.string.speech_list_hide_media)) }
        if (tagFilter) TagFilterAction(count = { it.conversations }, onPick = onPickTag)
        if (onSearch != null) {
            IconButton(
                onClick = onSearch,
            ) { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search)) }
        }
    }
}

/** A small label for where the summary stands; with a triangle in the error colours when the model gave up. */
@Composable
internal fun AiChip(
    chip: SummaryChip,
    modifier: Modifier = Modifier,
) {
    val failed = chip == SummaryChip.Failed
    Surface(
        modifier,
        shape = MaterialTheme.shapes.small,
        color = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (failed) Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(
                stringResource(if (failed) R.string.summary_failed else R.string.summarizing),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** On a row whose speech is mostly media. */
@Composable
private fun MediaChip(modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            stringResource(R.string.speech_chip_media),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Under the bar while media is hidden; its **✕** shows it again. */
@Composable
private fun HideMediaChip(onClear: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.speech_list_media_hidden),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            )
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.speech_list_show_media),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** The day's name, pinned over the cards of that day while they scroll. */
@Composable
private fun DayHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.headlineMedium,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(top = 8.dp, bottom = 4.dp)
                .semantics { heading() },
    )
}

/**
 * One conversation as a card: title, two lines of summary, time and length, and the pipeline line while the
 * summary is not ready. Only a failed summary turns the border red.
 */
@Composable
private fun ConversationCard(
    row: ConversationRow,
    onOpen: (String) -> Unit,
) {
    val failed = row.chip == SummaryChip.Failed
    Card(
        onClick = { onOpen(row.id) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow),
        border =
            BorderStroke(
                1.dp,
                if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    row.title ?: row.timeRange,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (row.bookmarks > 0) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = stringResource(R.string.has_bookmarks),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp).size(20.dp),
                    )
                }
            }
            Text(
                row.preview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.time_range_and_length_format, row.timeRange, row.length),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            row.chip?.let { PipelineLine(it) }
            if (row.media) MediaChip(Modifier.padding(top = 4.dp))
            TagRow(row.tags, Modifier.padding(top = 4.dp))
        }
    }
}

/** Where the summary stands, on its own line: a thin moving line while it is written, a triangle if it failed. */
@Composable
private fun PipelineLine(chip: SummaryChip) {
    val failed = chip == SummaryChip.Failed
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (failed) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                stringResource(if (failed) R.string.summary_failed else R.string.summarizing),
                style = MaterialTheme.typography.labelMedium,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!failed) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}
