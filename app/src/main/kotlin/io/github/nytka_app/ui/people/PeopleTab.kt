package io.github.nytka_app.ui.people

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.nytka_app.R
import io.github.nytka_app.ui.developer.server.ServerSettingsScreen
import io.github.nytka_app.ui.people.review.ReviewScreen
import kotlinx.coroutines.flow.Flow

private const val TAG_REQUEST = "tagRequest"

/**
 * The People tab: its own NavHost, so person pages, the review inbox and settings open inside it. A person that
 * another tab asks for arrives in [openRequests] and opens over the list; a conversation a page links to opens
 * through [onOpenConversation], which selects the Conversations tab.
 */
@Composable
fun PeopleTab(
    openRequests: Flow<String>,
    onOpenConversation: (String) -> Unit,
) {
    val navController = rememberNavController()
    LaunchedEffect(navController, openRequests) {
        openRequests.collect { id -> navController.navigate("person/$id") { popUpTo("list") } }
    }
    NavHost(navController, startDestination = "list") {
        composable("list") { entry ->
            // A tag tapped on a person page comes back as this entry's result: the list filters by it.
            val tagRequest by entry.savedStateHandle
                .getStateFlow<String?>(
                    TAG_REQUEST,
                    null,
                ).collectAsStateWithLifecycle()
            PeopleScreen(
                tagRequest = tagRequest,
                onTagRequestHandled = { entry.savedStateHandle[TAG_REQUEST] = null },
                onOpenPerson = { navController.navigate("person/$it") },
                onOpenReview = { navController.navigate("review") },
                onOpenSettings = { navController.navigate("settings?prefixes=people") },
            )
        }
        composable("person/{id}") {
            PersonScreen(
                onBack = { navController.popBackStack() },
                onOpenConversation = onOpenConversation,
                onOpenTag = { name ->
                    navController.getBackStackEntry("list").savedStateHandle[TAG_REQUEST] = name
                    navController.popBackStack("list", inclusive = false)
                },
            )
        }
        composable("review") {
            ReviewScreen(onBack = navController::popBackStack, onOpenConversation = onOpenConversation)
        }
        composable(
            "settings?prefixes={prefixes}",
            arguments =
                listOf(
                    navArgument("prefixes") {
                        type = NavType.StringType
                        nullable = true
                    },
                ),
        ) {
            ServerSettingsScreen(
                onBack = { navController.popBackStack() },
                title = stringResource(R.string.people_settings_title),
                footer = { VoiceModelsSection() },
            )
        }
    }
}
