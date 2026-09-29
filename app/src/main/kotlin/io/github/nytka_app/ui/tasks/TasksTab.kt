package io.github.nytka_app.ui.tasks

import androidx.compose.runtime.Composable
import io.github.nytka_app.ui.Placeholder

/**
 * The Tasks tab: a placeholder until v0.2's track D builds it. A task opens its conversation through
 * [onOpenConversation].
 */
@Suppress("UnusedParameter") // the placeholder keeps the signature its track builds on
@Composable
fun TasksTab(onOpenConversation: (String) -> Unit) {
    Placeholder()
}
