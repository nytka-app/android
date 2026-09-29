package io.github.nytka_app.ui.search

import androidx.compose.runtime.Composable
import io.github.nytka_app.ui.PlaceholderScreen

/** The search icon and route of the Conversations tab stay hidden until v0.4's track I sets this to true. */
const val SEARCH_ENABLED = false

/** Full-text search: a placeholder until v0.4's track I builds it. A hit opens through [onOpenConversation]. */
@Suppress("UnusedParameter") // the placeholder keeps the signature its track builds on
@Composable
fun SearchScreen(
    onOpenConversation: (String) -> Unit,
    onBack: () -> Unit,
) {
    PlaceholderScreen("Search", onBack)
}
