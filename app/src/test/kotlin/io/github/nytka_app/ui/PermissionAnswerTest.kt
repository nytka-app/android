package io.github.nytka_app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionAnswerTest {
    private val required = listOf("scan", "connect")
    private val both = setOf("scan", "connect")

    private fun answer(
        granted: Set<String> = emptySet(),
        rationaleBefore: Set<String> = emptySet(),
        rationaleAfter: Set<String> = emptySet(),
    ) = permissionAnswer(
        required,
        granted = { it in granted },
        rationaleBefore = { it in rationaleBefore },
        rationaleAfter = { it in rationaleAfter },
    )

    @Test
    fun `everything required allowed is granted`() {
        assertEquals(PermissionAnswer.Granted, answer(granted = both))
        assertEquals(PermissionAnswer.Granted, answer(granted = both, rationaleBefore = both))
    }

    @Test
    fun `backing out of the first prompt is no refusal for good`() {
        // Android answers "denied" and sets no flag: the rationale is false before and after.
        assertEquals(PermissionAnswer.Denied, answer())
    }

    @Test
    fun `a first refusal turns the rationale on and Android will show the prompt again`() {
        assertEquals(PermissionAnswer.Denied, answer(rationaleAfter = both))
    }

    @Test
    fun `a second refusal turns the rationale off and is final`() {
        assertEquals(PermissionAnswer.Blocked, answer(rationaleBefore = both))
    }

    @Test
    fun `backing out of the second prompt leaves the rationale on`() {
        assertEquals(PermissionAnswer.Denied, answer(rationaleBefore = both, rationaleAfter = both))
    }

    @Test
    fun `one permission refused for good is enough to block`() {
        assertEquals(PermissionAnswer.Blocked, answer(granted = setOf("scan"), rationaleBefore = setOf("connect")))
        assertEquals(
            PermissionAnswer.Blocked,
            answer(rationaleBefore = both, rationaleAfter = setOf("scan")),
        )
    }

    @Test
    fun `what was not required does not count`() {
        // Notifications are asked along with nearby devices; refusing them is not an answer to the request.
        assertEquals(PermissionAnswer.Granted, answer(granted = both + "other", rationaleBefore = setOf("other")))
    }

    @Test
    fun `a refusal that repeats is final, since backing out and a silent prompt look the same`() {
        assertEquals(PermissionAnswer.Denied, PermissionAnswer.Denied.after(null))
        assertEquals(PermissionAnswer.Blocked, PermissionAnswer.Denied.after(PermissionAnswer.Denied))
    }

    @Test
    fun `a final refusal stays final`() {
        assertEquals(PermissionAnswer.Blocked, PermissionAnswer.Denied.after(PermissionAnswer.Blocked))
        assertEquals(PermissionAnswer.Blocked, PermissionAnswer.Blocked.after(PermissionAnswer.Denied))
    }

    @Test
    fun `an allowed permission is never turned into a refusal`() {
        assertEquals(PermissionAnswer.Granted, PermissionAnswer.Granted.after(PermissionAnswer.Denied))
    }
}
