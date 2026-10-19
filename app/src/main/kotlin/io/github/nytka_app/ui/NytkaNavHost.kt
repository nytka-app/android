package io.github.nytka_app.ui

import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.nytka_app.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The tab bar and one slot per tab. Any tab can open a conversation: [tasks], [memories] and [ask] receive
 * `onOpenConversation(id)`, which selects the Conversations tab and hands the id to [conversations] as
 * `openRequests`; that tab opens `conversation/{id}` in its own NavHost. Any tab can open a person the same way
 * through `onOpenPerson(id)` and [people]'s `openRequests`; the People tab opens a conversation through its
 * own `onOpenConversation`. The device screen has no tab: [topBar] receives `onOpenDevice`, which the capture status
 * calls.
 */
@Composable
fun NytkaNavHost(
    conversations: @Composable (openRequests: Flow<String>, onOpenPerson: (String) -> Unit) -> Unit,
    tasks: @Composable (onOpenConversation: (String) -> Unit, onOpenPerson: (String) -> Unit) -> Unit,
    memories: @Composable (onOpenConversation: (String) -> Unit, onOpenPerson: (String) -> Unit) -> Unit,
    people: @Composable (openRequests: Flow<String>, onOpenConversation: (String) -> Unit) -> Unit,
    ask: @Composable (onOpenConversation: (String) -> Unit, onOpenPerson: (String) -> Unit) -> Unit,
    device: @Composable () -> Unit,
    topBar: @Composable (onOpenDevice: () -> Unit) -> Unit = {},
    /** Open decisions about people (name suggestions and other review items); 0 shows no badge. */
    peopleBadge: Int = 0,
    /** Called with the route each time another screen shows, so the caller can refresh what the bar shows. */
    onRouteShown: (String?) -> Unit = {},
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    LaunchedEffect(current) { onRouteShown(current) }
    // The Conversations tab is not composed while another tab shows: a request waits here until it collects.
    val requests = remember { Channel<String>(Channel.CONFLATED) }
    val openRequests = remember(requests) { requests.receiveAsFlow() }
    val personRequests = remember { Channel<String>(Channel.CONFLATED) }
    val openPersonRequests = remember(personRequests) { personRequests.receiveAsFlow() }

    val select = { route: String ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val onOpenConversation = { id: String ->
        requests.trySend(id)
        select(AppTab.Conversations.route)
    }
    val onOpenPerson = { id: String ->
        personRequests.trySend(id)
        select(AppTab.People.route)
    }

    Scaffold(
        topBar = { topBar { select(DEVICE_ROUTE) } },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = { select(tab.route) },
                        icon = { TabIcon(tab, if (tab == AppTab.People) peopleBadge else 0) },
                        label = { TabLabel(stringResource(tab.labelRes)) },
                        colors =
                            NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController,
            startDestination = AppTab.start.route,
            modifier = Modifier.padding(padding),
            enterTransition = { ScreenEnter },
            exitTransition = { ScreenExit },
            popEnterTransition = { ScreenEnter },
            popExitTransition = { ScreenExit },
        ) {
            composable(AppTab.Conversations.route) { conversations(openRequests, onOpenPerson) }
            composable(AppTab.Tasks.route) { tasks(onOpenConversation, onOpenPerson) }
            composable(AppTab.Memories.route) { memories(onOpenConversation, onOpenPerson) }
            composable(AppTab.People.route) { people(openPersonRequests, onOpenConversation) }
            composable(AppTab.Ask.route) { ask(onOpenConversation, onOpenPerson) }
            composable(DEVICE_ROUTE) { device() }
        }
    }
}

/** Motion reports a change of screen and nothing else: enter 300 ms ease-out, exit 150 ms ease-in. */
internal val ScreenEnter = fadeIn(tween(ENTER_MS, easing = EaseOutCubic))
internal val ScreenExit = fadeOut(tween(EXIT_MS, easing = EaseInCubic))
private const val ENTER_MS = 300
private const val EXIT_MS = 150

@Composable
private fun TabIcon(
    tab: AppTab,
    badge: Int,
) {
    val icon = @Composable { Icon(painterResource(tab.iconRes), contentDescription = null) }
    if (badge > 0) {
        val needsYou = pluralStringResource(R.plurals.needs_you_format, badge, badge)
        BadgedBox(
            badge = { DecisionBadge(badge, Modifier.semantics { contentDescription = needsYou }) },
        ) { icon() }
    } else {
        icon()
    }
}

/**
 * Every label shows. Five items leave 72 dp each on a 360 dp phone: "Conversations" alone does not fit at 12sp, so a
 * label too wide for its item shrinks, to 10sp at least; the Ukrainian labels fit at 12sp.
 */
@Composable
private fun TabLabel(text: String) {
    Text(
        text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp),
        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 12.sp, stepSize = 0.5.sp),
    )
}
