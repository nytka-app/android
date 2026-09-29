package io.github.nytka_app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppTabTest {
    @Test
    fun `the bar has the five tabs of the vision in that order`() {
        assertEquals(
            listOf("Conversations", "Tasks", "Memories", "Ask", "Device"),
            AppTab.entries.map { it.label },
        )
        assertEquals(AppTab.Conversations, AppTab.start)
    }

    @Test
    fun `every tab has a route of its own`() {
        val routes = AppTab.entries.map { it.route }

        assertEquals(routes.distinct(), routes)
    }
}
