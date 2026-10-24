package io.github.nytka_app.ui.today

/** Where Today's "More" entries go: the screens that left the tab bar. */
class TodayLinks(
    val onOpenTasks: () -> Unit,
    val onOpenMemories: () -> Unit,
    val onOpenPeople: () -> Unit,
    val onOpenAsk: () -> Unit,
    /** Open decisions about people; 0 shows no badge. */
    val peopleBadge: Int = 0,
    /** The topic pages, on a server with `topics`; null shows no entry. */
    val onOpenTopics: (() -> Unit)? = null,
)
