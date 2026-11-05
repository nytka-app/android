package io.github.nytka_app.ui.a11y

import io.github.nytka_app.ui.AppTab
import io.github.nytka_app.ui.DEVICE_ROUTE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec check 11: a new screen is either covered by an a11y test or named here as a debt. */
class CoverageA11yTest {
    private val covered =
        mapOf(
            AppTab.Today.route to "HomeA11yTest",
            AppTab.Timeline.route to "TimelineA11yTest",
            AppTab.Conversations.route to "ConversationsA11yTest",
            DEVICE_ROUTE to "DeviceA11yTest",
        )

    private val debt =
        setOf(
            AppTab.OverTime.route,
            AppTab.Tasks.route,
            AppTab.Ask.route,
            AppTab.People.route,
            AppTab.Memories.route,
        )

    private val routes = AppTab.entries.map { it.route } + DEVICE_ROUTE

    @Test
    fun `every route is covered or named as a debt`() {
        val missing = routes.filter { it !in covered && it !in debt }
        assertTrue("add $missing to covered or debt in CoverageA11yTest", missing.isEmpty())
    }

    @Test
    fun `a route is not both covered and a debt`() {
        assertEquals(emptySet<String>(), covered.keys.intersect(debt))
    }

    @Test
    fun `no entry names a route that is gone`() {
        assertEquals(emptySet<String>(), (covered.keys + debt) - routes.toSet())
    }

    @Test
    fun `a covered route has its test`() {
        val here = javaClass.packageName
        covered.values.forEach { name -> assertEquals(name, Class.forName("$here.$name").simpleName) }
    }
}
