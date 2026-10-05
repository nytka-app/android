package io.github.nytka_app.ui

import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
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
 * The tab bar and one slot per tab. Any tab can open a conversation: [tasks], [memories] and [ask] receive
 * `onOpenConversation(id)`, which selects the Conversations tab and hands the id to [conversations] as
 * `openRequests`; that tab opens `conversation/{id}` in its own NavHost. Any tab can open a person the same way
 * through `onOpenPerson(id)` and [people]'s `openRequests`.
 */
@Composable
fun NytkaNavHost(
    conversations: @Composable (openRequests: Flow<String>, onOpenPerson: (String) -> Unit) -> Unit,
    tasks: @Composable (onOpenConversation: (String) -> Unit, onOpenPerson: (String) -> Unit) -> Unit,
    memories: @Composable (onOpenConversation: (String) -> Unit, onOpenPerson: (String) -> Unit) -> Unit,
    people: @Composable (openRequests: Flow<String>) -> Unit,
    ask: @Composable (onOpenConversation: (String) -> Unit, onOpenPerson: (String) -> Unit) -> Unit,
    device: @Composable () -> Unit,
    topBar: @Composable () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    // The Conversations tab is not composed while another tab shows: a request waits here until it collects.
    val requests = remember { Channel<String>(Channel.CONFLATED) }
    val openRequests = remember(requests) { requests.receiveAsFlow() }
    val personRequests = remember { Channel<String>(Channel.CONFLATED) }
    val openPersonRequests = remember(personRequests) { personRequests.receiveAsFlow() }

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
    val onOpenPerson = { id: String ->
        personRequests.trySend(id)
        select(AppTab.People)
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
                        alwaysShowLabel = false,
                        label = { TabLabel(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = AppTab.start.route, modifier = Modifier.padding(padding)) {
            composable(AppTab.Conversations.route) { conversations(openRequests, onOpenPerson) }
            composable(AppTab.Tasks.route) { tasks(onOpenConversation, onOpenPerson) }
            composable(AppTab.Memories.route) { memories(onOpenConversation, onOpenPerson) }
            composable(AppTab.People.route) { people(openPersonRequests) }
            composable(AppTab.Ask.route) { ask(onOpenConversation, onOpenPerson) }
            composable(AppTab.Device.route) { device() }
        }
    }
}

/**
 * Six items leave about 60 dp each on a 360 dp phone, so only the selected one shows its label, and
 * "Conversations" wraps in the default label style. Smaller, unspaced type on one line keeps it whole.
 */
@Composable
private fun TabLabel(text: String) {
    Text(
        text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
    )
}
