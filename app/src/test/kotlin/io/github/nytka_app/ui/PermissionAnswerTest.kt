package io.github.nytka_app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionAnswerTest {
    private val required = listOf("scan", "connect")

    private fun answer(
        granted: Set<String>,
        canAskAgain: Set<String> = emptySet(),
    ) = permissionAnswer(required, granted = { it in granted }, canAskAgain = { it in canAskAgain })

    @Test
    fun `everything required allowed is granted`() {
        assertEquals(PermissionAnswer.Granted, answer(granted = setOf("scan", "connect")))
    }

    @Test
    fun `a refusal Android will still ask about is denied`() {
        assertEquals(PermissionAnswer.Denied, answer(granted = emptySet(), canAskAgain = setOf("scan", "connect")))
    }

    @Test
    fun `a refusal Android no longer asks about is blocked`() {
        assertEquals(PermissionAnswer.Blocked, answer(granted = emptySet()))
    }

    @Test
    fun `one permission Android no longer asks about is enough to block`() {
        assertEquals(PermissionAnswer.Blocked, answer(granted = setOf("scan")))
        assertEquals(PermissionAnswer.Blocked, answer(granted = emptySet(), canAskAgain = setOf("scan")))
    }

    @Test
    fun `what was not required does not count`() {
        // Notifications are asked along with nearby devices; refusing them is not an answer to the request.
        assertEquals(PermissionAnswer.Granted, answer(granted = setOf("scan", "connect", "other")))
    }
}
