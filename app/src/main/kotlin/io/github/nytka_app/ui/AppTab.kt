package io.github.nytka_app.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.nytka_app.R

/** The five tabs. Device and its settings open from the capture status in the top bar ([DEVICE_ROUTE]). */
enum class AppTab(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    Conversations("conversations", R.string.tab_conversations, R.drawable.ic_tab_conversations),
    Tasks("tasks", R.string.tab_tasks, R.drawable.ic_tab_tasks),
    Ask("ask", R.string.tab_ask, R.drawable.ic_tab_ask),
    People("people", R.string.tab_people, R.drawable.ic_tab_people),
    Memories("memories", R.string.tab_memories, R.drawable.ic_tab_memories),
    ;

    companion object {
        val start = Conversations
    }
}

/** The device screen with the app's settings: no tab, reached from the capture status every screen shows. */
const val DEVICE_ROUTE = "device"
