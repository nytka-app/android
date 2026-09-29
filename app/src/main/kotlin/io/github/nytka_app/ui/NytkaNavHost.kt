package io.github.nytka_app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

@Composable
fun NytkaNavHost(
    conversations: @Composable () -> Unit,
    device: @Composable () -> Unit,
    topBar: @Composable () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        topBar = topBar,
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = AppTab.start.route, modifier = Modifier.padding(padding)) {
            composable(AppTab.Conversations.route) { conversations() }
            composable(AppTab.Device.route) { device() }
        }
    }
}
