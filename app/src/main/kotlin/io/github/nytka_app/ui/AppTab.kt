package io.github.nytka_app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** The v0.1 tabs. Tasks, Memories and Ask join later; the bar leaves room for five. */
enum class AppTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Conversations("conversations", "Conversations", Icons.AutoMirrored.Filled.List),
    Device("device", "Device", Icons.Filled.Settings),
    ;

    companion object {
        val start = Conversations
    }
}
