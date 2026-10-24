package io.github.nytka_app.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.nytka_app.R

/**
 * The tabs. A server that lists `today` gets Today, Timeline and Over time ([LifeNavHost]); Tasks, Memories, People
 * and Ask open from Today, conversations from the Timeline and search. An older server keeps the five [legacy] tabs
 * ([NytkaNavHost]). Device and its settings open from the capture status in the top bar ([DEVICE_ROUTE]).
 */
enum class AppTab(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    val legacy: Boolean = false,
) {
    Today("today", R.string.tab_today, R.drawable.ic_tab_today),
    Timeline("timeline", R.string.tab_timeline, R.drawable.ic_tab_timeline),
    OverTime("over-time", R.string.tab_over_time, R.drawable.ic_tab_over_time),
    Conversations("conversations", R.string.tab_conversations, R.drawable.ic_tab_conversations, legacy = true),
    Tasks("tasks", R.string.tab_tasks, R.drawable.ic_tab_tasks, legacy = true),
    Ask("ask", R.string.tab_ask, R.drawable.ic_tab_ask, legacy = true),
    People("people", R.string.tab_people, R.drawable.ic_tab_people, legacy = true),
    Memories("memories", R.string.tab_memories, R.drawable.ic_tab_memories, legacy = true),
    ;

    companion object {
        /** The tabs of the bar: the three, or the five of before. */
        fun bar(threeTabs: Boolean): List<AppTab> = entries.filter { it.legacy != threeTabs }

        fun start(threeTabs: Boolean): AppTab = if (threeTabs) Today else Conversations
    }
}

/** The device screen with the app's settings: no tab, reached from the capture status every screen shows. */
const val DEVICE_ROUTE = "device"
