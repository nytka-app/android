package io.github.nytka_app.ui.conversations

import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.MemoriesClient
import io.github.nytka_app.core.api.Memory
import io.github.nytka_app.core.api.MemoryPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LineMemoryViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    /** Only the add call is used; every other call is a test bug. */
    private class FakeMemories(
        var answer: ApiResult<Memory>,
    ) : MemoriesClient {
        val added = mutableListOf<String>()

        override suspend fun addMemory(text: String) = answer.also { added += text }

        override suspend fun memories(
            before: String?,
            limit: Int,
        ): ApiResult<MemoryPage> = error("unused")

        override suspend fun editMemory(
            id: String,
            text: String,
        ): ApiResult<Memory> = error("unused")

        override suspend fun deleteMemory(id: String): ApiResult<Unit> = error("unused")
    }

    private val memory =
        Memory(
            id = "m1",
            text = "Olena: I will send the contract.",
            source = "manual",
            createdAt = "2026-10-19T10:00:00Z",
            updatedAt = "2026-10-19T10:00:00Z",
        )

    @Test
    fun `saving sends the trimmed text and reports saved`() {
        val api = FakeMemories(ApiResult.Ok(memory))
        val vm = LineMemoryViewModel(api)

        vm.save("  Olena: I will send the contract.  ")

        assertEquals(listOf("Olena: I will send the contract."), api.added)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun `a fact the server already holds counts as saved, without an error`() {
        val vm = LineMemoryViewModel(FakeMemories(ApiResult.Failure(FailureKind.Conflict, "conflict")))

        vm.save("Olena: I will send the contract.")

        assertTrue(vm.state.value.saved)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `another failure stays in the dialog with its sentence`() {
        val vm = LineMemoryViewModel(FakeMemories(ApiResult.Failure(FailureKind.Network, "offline")))

        vm.save("Olena: I will send the contract.")

        assertFalse(vm.state.value.saved)
        assertTrue(vm.state.value.error != null)
    }

    @Test
    fun `an empty text sends nothing`() {
        val api = FakeMemories(ApiResult.Ok(memory))

        LineMemoryViewModel(api).save("   ")

        assertEquals(emptyList<String>(), api.added)
    }

    @Test
    fun `the draft names the speaker, leaves the wearer's line bare and fits the limit`() {
        assertEquals("Olena: Hi", LineMemoryViewModel.draft("Olena", "Hi"))
        assertEquals("Hi", LineMemoryViewModel.draft(null, "Hi"))
        assertEquals(300, LineMemoryViewModel.draft("Olena", "x".repeat(400)).length)
    }
}
