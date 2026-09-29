package io.github.nytka_app.ui.conversations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.nytka_app.ui.StatusCard
import io.github.nytka_app.ui.StatusUiState

/** The Conversations tab: the list, and a conversation opened from it. */
@Composable
fun ConversationsTab(
    status: StatusUiState,
    onMute: (Boolean) -> Unit,
) {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "list") {
        composable("list") { entry ->
            // A conversation deleted on its own screen comes back as this entry's result.
            val deleted by entry.savedStateHandle.getStateFlow<String?>(DELETED, null).collectAsStateWithLifecycle()
            ConversationsScreen(status, onMute, deleted, onOpen = { navController.navigate("conversation/$it") })
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
    viewModel: ConversationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(deleted) { deleted?.let(viewModel::forget) }
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { StatusCard(status, onMute) }
            state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
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
                        headlineContent = { Text("${row.timeRange} · ${row.length}") },
                        supportingContent = { Text(row.preview, maxLines = 2, overflow = TextOverflow.Ellipsis) },
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
