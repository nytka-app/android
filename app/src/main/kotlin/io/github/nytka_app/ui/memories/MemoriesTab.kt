package io.github.nytka_app.ui.memories

import androidx.compose.runtime.Composable
import io.github.nytka_app.ui.Placeholder

/**
 * The Memories tab: a placeholder until v0.4's track I builds it. A memory opens its source conversation
 * through [onOpenConversation].
 */
@Suppress("UnusedParameter") // the placeholder keeps the signature its track builds on
@Composable
fun MemoriesTab(onOpenConversation: (String) -> Unit) {
    Placeholder()
}
