package io.github.nytka_app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** The five tabs of the finished app. Tasks, Memories and Ask are placeholders until their versions arrive. */
enum class AppTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Conversations("conversations", "Conversations", Icons.AutoMirrored.Filled.List),
    Tasks("tasks", "Tasks", Icons.Filled.CheckCircle),
    Memories("memories", "Memories", Icons.Filled.Favorite),
    Ask("ask", "Ask", Icons.Filled.Face),
    Device("device", "Device", Icons.Filled.Settings),
    ;

    companion object {
        val start = Conversations
    }
}
