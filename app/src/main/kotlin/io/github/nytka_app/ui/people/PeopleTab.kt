package io.github.nytka_app.ui.people

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/** The People tab: its own NavHost, so person pages, the review inbox and settings open inside it. */
@Composable
fun PeopleTab() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "list") {
        composable("list") { PeopleScreen() }
    }
}
