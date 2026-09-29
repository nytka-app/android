package io.github.nytka_app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppTabTest {
    @Test
    fun `v0_1 ships the conversations and device tabs in that order`() {
        assertEquals(listOf("Conversations", "Device"), AppTab.entries.map { it.label })
        assertEquals(AppTab.Conversations, AppTab.start)
    }
}
