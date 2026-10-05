package io.github.nytka_app.ui.people

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.Flow

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
        composable("list") { PeopleScreen(onOpenPerson = { navController.navigate("person/$it") }) }
        composable("person/{id}") {
            PersonScreen(onBack = { navController.popBackStack() }, onOpenConversation = onOpenConversation)
        }
    }
}
