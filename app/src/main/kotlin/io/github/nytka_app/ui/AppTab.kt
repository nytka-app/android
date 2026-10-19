package io.github.nytka_app.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.nytka_app.R

/** The five tabs. Device and its settings open from the capture status in the top bar ([DEVICE_ROUTE]). */
enum class AppTab(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Conversations("conversations", R.string.tab_conversations, Icons.AutoMirrored.Filled.List),
    Tasks("tasks", R.string.tab_tasks, Icons.Filled.CheckCircle),
    Ask("ask", R.string.tab_ask, Icons.Filled.Face),
    People("people", R.string.tab_people, Icons.Filled.Person),
    Memories("memories", R.string.tab_memories, Icons.Filled.Favorite),
    ;

    companion object {
        val start = Conversations
    }
}

/** The device screen with the app's settings: no tab, reached from the capture status every screen shows. */
const val DEVICE_ROUTE = "device"
