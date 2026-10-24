package io.github.nytka_app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.nytka_app.capture.CaptureState
import io.github.nytka_app.ui.AppTab
import io.github.nytka_app.ui.AppViewModel
import io.github.nytka_app.ui.CaptureStatusIndicator
import io.github.nytka_app.ui.CaptureThread
import io.github.nytka_app.ui.LayoutViewModel
import io.github.nytka_app.ui.LifeNavHost
import io.github.nytka_app.ui.LifeScreens
import io.github.nytka_app.ui.Moment
import io.github.nytka_app.ui.MomentUri
import io.github.nytka_app.ui.NytkaNavHost
import io.github.nytka_app.ui.StatusUiState
import io.github.nytka_app.ui.StatusViewModel
import io.github.nytka_app.ui.ask.AskTab
import io.github.nytka_app.ui.conversations.ConversationScreen
import io.github.nytka_app.ui.conversations.ConversationsTab
import io.github.nytka_app.ui.device.DeviceTab
import io.github.nytka_app.ui.firstrun.FirstRunScreen
import io.github.nytka_app.ui.memories.MemoriesTab
import io.github.nytka_app.ui.overtime.OverTimeScreen
import io.github.nytka_app.ui.people.PeopleTab
import io.github.nytka_app.ui.people.PersonScreen
import io.github.nytka_app.ui.people.review.ReviewViewModel
import io.github.nytka_app.ui.search.SearchScreen
import io.github.nytka_app.ui.tasks.TasksTab
import io.github.nytka_app.ui.theme.NytkaTheme
import io.github.nytka_app.ui.timeline.TimelineScreen
import io.github.nytka_app.ui.today.TodayLinks
import io.github.nytka_app.ui.today.TodayScreen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow

/** The screens of the three-tab layout, wired to the app's view models. */
private fun lifeScreens(
    status: StatusUiState,
    onMute: (Boolean) -> Unit,
    peopleBadge: Int,
) = LifeScreens(
    today = { nav ->
        TodayScreen(
            onOpenMoment = nav::openMoment,
            onOpenPerson = nav::openPerson,
            links =
                TodayLinks(
                    onOpenTasks = { nav.open(AppTab.Tasks.route) },
                    onOpenMemories = { nav.open(AppTab.Memories.route) },
                    onOpenPeople = { nav.open(AppTab.People.route) },
                    onOpenAsk = { nav.open(AppTab.Ask.route) },
                    peopleBadge = peopleBadge,
                ),
        )
    },
    timeline = { nav ->
        TimelineScreen(
            onOpenMoment = nav::openMoment,
            onOpenPerson = nav::openPerson,
            onOpenConversations = { nav.openConversations() },
        )
    },
    overTime = { OverTimeScreen() },
    conversation = { nav ->
        ConversationScreen(
            developerMode = status.developerMode,
            onBack = nav::back,
            onDeleted = nav::back,
            onOpenPerson = nav::openPerson,
            onOpenTag = { nav.openConversations(it) },
        )
    },
    conversations = { nav, tag -> ConversationsTab(status, onMute, emptyFlow(), nav::openPerson, initialTag = tag) },
    person = { nav ->
        PersonScreen(onBack = nav::back, onOpenConversation = nav::openConversation, onOpenTag = {
            nav.open(AppTab.People.route)
        })
    },
    people = { nav -> PeopleTab(emptyFlow(), nav::openConversation) },
    tasks = { nav -> TasksTab(nav::openConversation) },
    memories = { nav -> MemoriesTab(nav::openConversation) },
    ask = { nav -> AskTab(nav::openConversation) },
    search = { nav ->
        SearchScreen(onOpenConversation = nav::openConversation, onOpenPerson = nav::openPerson, onBack = nav::back)
    },
    device = { DeviceTab(status, onMute) },
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** Moments that `nytka://conversation/…` links ask for ([MomentUri]); the layout opens each once. */
    private val moments = Channel<Moment>(Channel.CONFLATED)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        MomentUri.parse(intent.dataString)?.let(moments::trySend)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) MomentUri.parse(intent?.dataString)?.let(moments::trySend)
        val momentFlow = moments.receiveAsFlow()
        setContent {
            val app: AppViewModel = hiltViewModel()
            val wallpaperColors by app.wallpaperColors.collectAsStateWithLifecycle()
            NytkaTheme(wallpaperColors = wallpaperColors) {
                val onboarded by app.onboarded.collectAsStateWithLifecycle()
                when (onboarded) {
                    null -> Unit
                    false -> FirstRunScreen()
                    true -> {
                        val statusViewModel: StatusViewModel = hiltViewModel()
                        val status by statusViewModel.state.collectAsStateWithLifecycle()
                        val captureState = CaptureState.of(status.capture, status.muted)
                        // Activity-scoped, apart from the People screen's own: it only counts for the tab badge.
                        val review: ReviewViewModel = hiltViewModel()
                        val reviewState by review.state.collectAsStateWithLifecycle()
                        val layout: LayoutViewModel = hiltViewModel()
                        val threeTabs by layout.threeTabs.collectAsStateWithLifecycle()
                        val peopleBadge = if (reviewState.available) reviewState.count else 0
                        when (threeTabs) {
                            null -> Unit
                            true ->
                                LifeNavHost(
                                    screens = lifeScreens(status, statusViewModel::setMuted, peopleBadge),
                                    onRouteShown = { review.refresh() },
                                    moments = momentFlow,
                                    topBar = { onOpenDevice, onSearch, onBack ->
                                        AppBar(captureState, onOpenDevice, onSearch, onBack)
                                    },
                                )

                            false ->
                                FiveTabs(
                                    status,
                                    statusViewModel::setMuted,
                                    captureState,
                                    peopleBadge,
                                    momentFlow,
                                ) { review.refresh() }
                        }
                    }
                }
            }
        }
    }
}

/** The app name, back on a screen stacked over a tab, search in the three-tab layout, and the capture status. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppBar(
    captureState: CaptureState,
    onOpenDevice: () -> Unit,
    onSearch: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    Column {
        TopAppBar(
            title = { Text(stringResource(R.string.app_name)) },
            navigationIcon = {
                onBack?.let {
                    IconButton(onClick = it) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                }
            },
            actions = {
                onSearch?.let {
                    IconButton(onClick = it) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = stringResource(R.string.search_content_description),
                        )
                    }
                }
                CaptureStatusIndicator(captureState, Modifier.padding(end = 8.dp), onClick = onOpenDevice)
            },
        )
        CaptureThread(captureState)
    }
}

/** The five tabs of before, for a server without `today`. */
@Composable
private fun FiveTabs(
    status: StatusUiState,
    onMute: (Boolean) -> Unit,
    captureState: CaptureState,
    peopleBadge: Int,
    moments: Flow<Moment>,
    onRouteShown: () -> Unit,
) {
    NytkaNavHost(
        peopleBadge = peopleBadge,
        conversationRequests = remember(moments) { moments.map { it.conversationId } },
        onRouteShown = { onRouteShown() },
        topBar = { onOpenDevice -> AppBar(captureState, onOpenDevice) },
        conversations = { openRequests, onOpenPerson -> ConversationsTab(status, onMute, openRequests, onOpenPerson) },
        tasks = { onOpenConversation, _ -> TasksTab(onOpenConversation) },
        memories = { onOpenConversation, _ -> MemoriesTab(onOpenConversation) },
        people = { openRequests, onOpenConversation -> PeopleTab(openRequests, onOpenConversation) },
        ask = { onOpenConversation, _ -> AskTab(onOpenConversation) },
        device = { DeviceTab(status, onMute) },
    )
}
