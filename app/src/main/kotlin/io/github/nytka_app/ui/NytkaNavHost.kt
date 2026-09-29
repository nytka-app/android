package io.github.nytka_app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The tab bar and one slot per tab. Any tab can open a conversation: [tasks] and [memories] receive
 * `onOpenConversation(id)`, which selects the Conversations tab and hands the id to [conversations] as
 * `openRequests`; that tab opens `conversation/{id}` in its own NavHost.
 */
@Composable
fun NytkaNavHost(
    conversations: @Composable (openRequests: Flow<String>) -> Unit,
    tasks: @Composable (onOpenConversation: (String) -> Unit) -> Unit,
    memories: @Composable (onOpenConversation: (String) -> Unit) -> Unit,
    ask: @Composable () -> Unit,
    device: @Composable () -> Unit,
    topBar: @Composable () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    // The Conversations tab is not composed while another tab shows: a request waits here until it collects.
    val requests = remember { Channel<String>(Channel.CONFLATED) }
    val openRequests = remember(requests) { requests.receiveAsFlow() }

    val select = { tab: AppTab ->
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val onOpenConversation = { id: String ->
        requests.trySend(id)
        select(AppTab.Conversations)
    }

    Scaffold(
        topBar = topBar,
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = { select(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { TabLabel(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = AppTab.start.route, modifier = Modifier.padding(padding)) {
            composable(AppTab.Conversations.route) { conversations(openRequests) }
            composable(AppTab.Tasks.route) { tasks(onOpenConversation) }
            composable(AppTab.Memories.route) { memories(onOpenConversation) }
            composable(AppTab.Ask.route) { ask() }
            composable(AppTab.Device.route) { device() }
        }
    }
}

/**
 * Five items leave about 64 dp per label on a 360 dp phone, and "Conversations" wraps in the default label
 * style. Smaller, unspaced type and a floor for the shrinking keep every label on one line.
 */
@Composable
private fun TabLabel(text: String) {
    Text(
        text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
        autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 11.sp),
    )
}
