package io.github.nytka_app.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.nytka_app.R

/** The six tabs of the finished app. Ask is a placeholder until its version arrives. */
enum class AppTab(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Conversations("conversations", R.string.tab_conversations, Icons.AutoMirrored.Filled.List),
    Tasks("tasks", R.string.tab_tasks, Icons.Filled.CheckCircle),
    Memories("memories", R.string.tab_memories, Icons.Filled.Favorite),
    People("people", R.string.tab_people, Icons.Filled.Person),
    Ask("ask", R.string.tab_ask, Icons.Filled.Face),
    Device("device", R.string.tab_device, Icons.Filled.Settings),
    ;

    companion object {
        val start = Conversations
    }
}
