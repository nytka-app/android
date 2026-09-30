package io.github.nytka_app.ui.conversations

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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import io.github.nytka_app.ui.StatusCard
import io.github.nytka_app.ui.StatusUiState
import io.github.nytka_app.ui.search.SEARCH_ENABLED
import io.github.nytka_app.ui.search.SearchScreen
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
) {
    val navController = rememberNavController()
    LaunchedEffect(navController, openRequests) {
        openRequests.collect { id -> navController.navigate("conversation/$id") { popUpTo("list") } }
    }
    NavHost(navController, startDestination = "list") {
        composable("list") { entry ->
            // A conversation deleted on its own screen comes back as this entry's result.
            val deleted by entry.savedStateHandle.getStateFlow<String?>(DELETED, null).collectAsStateWithLifecycle()
            ConversationsScreen(
                status,
                onMute,
                deleted,
                onOpen = { navController.navigate("conversation/$it") },
                onSearch = { navController.navigate("search") },
            )
        }
        composable("conversation/{id}") { entry ->
            ConversationScreen(
                developerMode = status.developerMode,
                onBack = { navController.popBackStack() },
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
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private const val DELETED = "deleted"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    status: StatusUiState,
    onMute: (Boolean) -> Unit,
    deleted: String?,
    onOpen: (String) -> Unit,
    onSearch: () -> Unit,
    viewModel: ConversationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    LaunchedEffect(deleted) { deleted?.let(viewModel::forget) }
    // Refreshes when the screen is shown again and every 30 seconds while it is, but never in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.keepFresh() }
    }
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (SEARCH_ENABLED) item { SearchButton(onSearch) }
            item { StatusCard(status, onMute, notice) }
            state.error?.let { error ->
                item {
                    Column {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = viewModel::refresh) { Text("Retry") }
                    }
                }
            }
            state.days.forEach { day ->
                item(key = "day-${day.title}") {
                    Text(
                        day.title,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                items(day.rows, key = { it.id }) { row ->
                    ListItem(
                        headlineContent = {
                            Text(row.title ?: row.timeRange, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent =
                            if (row.bookmarks > 0) {
                                {
                                    Icon(
                                        Icons.Filled.Star,
                                        contentDescription = "Has bookmarks",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            } else {
                                null
                            },
                        supportingContent = {
                            Column {
                                Text(row.preview, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${row.timeRange} · ${row.length}",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                row.chip?.let { chip ->
                                    AiChip(chip, Modifier.padding(top = 4.dp))
                                }
                            }
                        },
                        modifier = Modifier.clickable { onOpen(row.id) },
                    )
                }
            }
            if (state.days.isNotEmpty() && !state.endReached) {
                item(key = "more") { LaunchedEffect(state.days.sumOf { it.rows.size }) { viewModel.loadMore() } }
            }
            if (state.days.isEmpty() && !state.loading && state.error == null) {
                item { Text("No conversations yet. Speech shows up here a few minutes after it is said.") }
            }
        }
    }
}

@Composable
private fun SearchButton(onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        IconButton(onClick = onClick) { Icon(Icons.Filled.Search, contentDescription = "Search") }
    }
}

/** A small label for where the summary stands; red when the model gave up. */
@Composable
internal fun AiChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    val failed = text == "Summary failed"
    Surface(
        modifier,
        shape = MaterialTheme.shapes.small,
        color = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
