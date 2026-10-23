package io.github.nytka_app.ui

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppTabTest {
    @Test
    fun `a server with today gets three tabs, starting on Today`() {
        assertEquals(listOf("Today", "Timeline", "Over time"), AppTab.bar(threeTabs = true).map { label(it) })
        assertEquals(AppTab.Today, AppTab.start(threeTabs = true))
    }

    @Test
    fun `an older server keeps the five tabs in that order`() {
        assertEquals(
            listOf("Conversations", "Tasks", "Ask", "People", "Memories"),
            AppTab.bar(threeTabs = false).map { label(it) },
        )
        assertEquals(AppTab.Conversations, AppTab.start(threeTabs = false))
    }

    @Test
    fun `every tab has a route of its own, and the device screen has none of theirs`() {
        val routes = AppTab.entries.map { it.route } + DEVICE_ROUTE

        assertEquals(routes.distinct(), routes)
    }

    @Test
    fun `every label is a string resource with text`() {
        assertTrue(AppTab.entries.all { it.labelRes != 0 && label(it).isNotBlank() })
    }

    @Test
    fun `a moment rides on the conversation route`() {
        assertEquals("conversation/c1", conversationRoute("c1"))
        assertEquals("conversation/c1?at=1729667642000", conversationRoute("c1", 1_729_667_642_000))
    }

    private fun label(tab: AppTab): String =
        ApplicationProvider.getApplicationContext<android.app.Application>().getString(tab.labelRes)
}
