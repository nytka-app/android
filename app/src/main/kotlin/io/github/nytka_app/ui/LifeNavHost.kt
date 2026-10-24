package io.github.nytka_app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * What [LifeNavHost] shows outside its three tabs. Each slot gets the navigation it needs: a conversation opens at a
 * moment with [Navigation.openMoment], a person with [Navigation.openPerson].
 */
class LifeScreens(
    val today: @Composable (Navigation) -> Unit,
    val timeline: @Composable (Navigation) -> Unit,
    val overTime: @Composable (Navigation) -> Unit,
    val conversation: @Composable (Navigation) -> Unit,
    val conversations: @Composable (Navigation, tag: String?) -> Unit,
    val person: @Composable (Navigation) -> Unit,
    val people: @Composable (Navigation) -> Unit,
    val tasks: @Composable (Navigation) -> Unit,
    val memories: @Composable (Navigation) -> Unit,
    val ask: @Composable (Navigation) -> Unit,
    val search: @Composable (Navigation) -> Unit,
    val device: @Composable () -> Unit,
)

/** How a screen in [LifeNavHost] moves on. Every route but the tabs stacks over the tab it came from. */
class Navigation(
    private val navController: NavHostController,
) {
    /** Opens a conversation; with [atMs] (epoch milliseconds) it scrolls and seeks to that moment. */
    fun openMoment(
        conversationId: String,
        atMs: Long? = null,
    ) = navController.navigate(conversationRoute(conversationId, atMs))

    fun openConversation(conversationId: String) = openMoment(conversationId)

    /** A moment a link from outside the app asked for ([MomentUri]). */
    fun open(moment: Moment) =
        navController.navigate(conversationRoute(moment.conversationId, moment.atMs, moment.segmentId))

    fun openPerson(personId: String) = navController.navigate("person/$personId")

    fun openConversations(tag: String? = null) =
        navController.navigate(if (tag == null) CONVERSATIONS else "$CONVERSATIONS?tag=$tag")

    fun open(route: String) = navController.navigate(route)

    fun back() {
        navController.popBackStack()
    }
}

/**
 * The route of a conversation, at a moment when [atMs] is given, on its line when [segmentId] is:
 * `conversation/{id}?at=1729667642000&segment=48121`.
 */
fun conversationRoute(
    id: String,
    atMs: Long? = null,
    segmentId: Long? = null,
): String {
    val query = listOfNotNull(atMs?.let { "at=$it" }, segmentId?.let { "segment=$it" })
    return if (query.isEmpty()) "conversation/$id" else "conversation/$id?${query.joinToString("&")}"
}

/**
 * The three tabs, for a server that lists `today`: Today, Timeline, Over time. Conversations, people, tasks,
 * memories, Ask and search stack over them; [topBar] gets a back action on those and a search action everywhere.
 */
@Composable
fun LifeNavHost(
    screens: LifeScreens,
    topBar: @Composable (onOpenDevice: () -> Unit, onSearch: () -> Unit, onBack: (() -> Unit)?) -> Unit,
    /** Called with the route each time another screen shows, so the caller can refresh what the bar shows. */
    onRouteShown: (String?) -> Unit = {},
    /** Moments that links from outside the app ask for; each opens over the current tab. */
    moments: Flow<Moment> = emptyFlow(),
) {
    val navController = rememberNavController()
    val navigation = Navigation(navController)
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    LaunchedEffect(current) { onRouteShown(current) }
    // Waits for the graph: a link that started the app arrives before the first composition sets it.
    LaunchedEffect(navController, moments) { moments.collect { moment -> navigation.open(moment) } }
    val tabs = AppTab.bar(threeTabs = true)
    val onTab = tabs.any { it.route == current }

    val select = { route: String ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        topBar = {
            topBar(
                { navigation.open(DEVICE_ROUTE) },
                { navigation.open(SEARCH) },
                if (onTab || current == null) null else navigation::back,
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = { select(tab.route) },
                        icon = { TabIcon(tab, 0) },
                        label = { TabLabel(stringResource(tab.labelRes)) },
                        colors = tabColors(),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController,
            startDestination = AppTab.start(threeTabs = true).route,
            modifier = Modifier.padding(padding),
            enterTransition = { ScreenEnter },
            exitTransition = { ScreenExit },
            popEnterTransition = { ScreenEnter },
            popExitTransition = { ScreenExit },
        ) {
            composable(AppTab.Today.route) { screens.today(navigation) }
            composable(AppTab.Timeline.route) { screens.timeline(navigation) }
            composable(AppTab.OverTime.route) { screens.overTime(navigation) }
            composable(
                "conversation/{id}?at={at}&segment={segment}",
                arguments =
                    listOf(
                        navArgument("at") {
                            type = NavType.StringType
                            nullable = true
                        },
                        navArgument("segment") {
                            type = NavType.StringType
                            nullable = true
                        },
                    ),
            ) { screens.conversation(navigation) }
            composable(
                "$CONVERSATIONS?tag={tag}",
                arguments =
                    listOf(
                        navArgument("tag") {
                            type = NavType.StringType
                            nullable = true
                        },
                    ),
            ) { entry -> screens.conversations(navigation, entry.arguments?.getString("tag")) }
            composable("person/{id}") { screens.person(navigation) }
            composable(AppTab.People.route) { screens.people(navigation) }
            composable(AppTab.Tasks.route) { screens.tasks(navigation) }
            composable(AppTab.Memories.route) { screens.memories(navigation) }
            composable(AppTab.Ask.route) { screens.ask(navigation) }
            composable(SEARCH) { screens.search(navigation) }
            composable(DEVICE_ROUTE) { screens.device() }
        }
    }
}

private const val CONVERSATIONS = "conversations"
private const val SEARCH = "search"
