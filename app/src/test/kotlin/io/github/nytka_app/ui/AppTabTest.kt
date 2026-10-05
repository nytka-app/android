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
    fun `the bar has the six tabs in that order`() {
        assertEquals(
            listOf("Conversations", "Tasks", "Memories", "People", "Ask", "Device"),
            AppTab.entries.map { label(it) },
        )
        assertEquals(AppTab.Conversations, AppTab.start)
    }

    @Test
    fun `every tab has a route of its own`() {
        val routes = AppTab.entries.map { it.route }

        assertEquals(routes.distinct(), routes)
    }

    @Test
    fun `every label is a string resource with text`() {
        assertTrue(AppTab.entries.all { it.labelRes != 0 && label(it).isNotBlank() })
    }

    private fun label(tab: AppTab): String =
        ApplicationProvider.getApplicationContext<android.app.Application>().getString(tab.labelRes)
}
