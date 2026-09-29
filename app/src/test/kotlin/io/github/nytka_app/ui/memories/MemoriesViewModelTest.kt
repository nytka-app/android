package io.github.nytka_app.ui.memories

import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.MemoriesClient
import io.github.nytka_app.core.api.Memory
import io.github.nytka_app.core.api.MemoryPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class MemoriesViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class FakeMemories : MemoriesClient {
        val pages = mutableMapOf<String?, ApiResult<MemoryPage>>()
        var write: ApiResult<Memory>? = null
        var deleteResult: ApiResult<Unit> = ApiResult.Ok(Unit)
        val requested = mutableListOf<String?>()
        val added = mutableListOf<String>()
        val edited = mutableListOf<Pair<String, String>>()
        val deleted = mutableListOf<String>()

        override suspend fun memories(
            before: String?,
            limit: Int,
        ): ApiResult<MemoryPage> {
            requested += before
            return pages.getValue(before)
        }

        override suspend fun addMemory(text: String): ApiResult<Memory> {
            added += text
            return write ?: ApiResult.Ok(memory("new", text))
        }

        override suspend fun editMemory(
            id: String,
            text: String,
        ): ApiResult<Memory> {
            edited += id to text
            return write ?: ApiResult.Ok(memory(id, text))
        }

        override suspend fun deleteMemory(id: String): ApiResult<Unit> = deleteResult.also { deleted += id }
    }

    private val clock = Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC)
    private val api = FakeMemories()

    private fun newViewModel() = MemoriesViewModel(api, clock)

    @Test
    fun `lists memories with the source title and day`() {
        api.pages[null] =
            ApiResult.Ok(
                MemoryPage(
                    listOf(
                        memory("a", "Lives in Kyiv", "Lunch", "2026-09-29T09:00:00Z", "c1"),
                        memory("b", "Has a sister"),
                    ),
                ),
            )

        val rows = newViewModel().state.value.rows

        assertEquals(MemoryRow("a", "Lives in Kyiv", "Lunch · Today", "c1"), rows[0])
        assertNull(rows[1].source)
        assertNull(rows[1].conversationId)
    }

    @Test
    fun `loads the next page with nextBefore`() {
        api.pages[null] = ApiResult.Ok(MemoryPage(listOf(memory("a", "one")), nextBefore = "a"))
        api.pages["a"] = ApiResult.Ok(MemoryPage(listOf(memory("b", "two"))))
        val viewModel = newViewModel()

        viewModel.loadMore()

        assertEquals(
            listOf("a", "b"),
            viewModel.state.value.rows
                .map { it.id },
        )
        assertTrue(viewModel.state.value.endReached)
        assertEquals(listOf(null, "a"), api.requested)
    }

    @Test
    fun `an older server says it needs an update`() {
        api.pages[null] = ApiResult.Failure(FailureKind.NotFound, "Not found.")

        val state = newViewModel().state.value

        assertEquals("This server needs an update", state.error)
        assertTrue(state.rows.isEmpty())
    }

    @Test
    fun `adding puts the memory first and closes the dialog`() {
        api.pages[null] = ApiResult.Ok(MemoryPage(listOf(memory("a", "one"))))
        val viewModel = newViewModel()

        viewModel.startAdd()
        viewModel.save("  two ")

        assertEquals(listOf("two"), api.added)
        assertEquals(
            listOf("new", "a"),
            viewModel.state.value.rows
                .map { it.id },
        )
        assertNull(viewModel.state.value.editor)
    }

    @Test
    fun `a duplicate keeps the dialog open with the reason`() {
        api.pages[null] = ApiResult.Ok(MemoryPage(emptyList()))
        api.write = ApiResult.Failure(FailureKind.Conflict, "conflict")
        val viewModel = newViewModel()

        viewModel.startAdd()
        viewModel.save("one")

        assertEquals(
            "This memory already exists.",
            viewModel.state.value.editor
                ?.error,
        )
    }

    @Test
    fun `text over 300 characters or blank is not sent`() {
        api.pages[null] = ApiResult.Ok(MemoryPage(emptyList()))
        val viewModel = newViewModel()

        viewModel.startAdd()
        viewModel.save("   ")
        viewModel.save("x".repeat(301))

        assertTrue(api.added.isEmpty())
    }

    @Test
    fun `editing rewrites the row in place`() {
        api.pages[null] = ApiResult.Ok(MemoryPage(listOf(memory("a", "one"), memory("b", "two"))))
        val viewModel = newViewModel()

        viewModel.startEdit("b")
        assertEquals(
            "b",
            viewModel.state.value.editor
                ?.target
                ?.id,
        )
        viewModel.save("three")

        assertEquals(listOf("b" to "three"), api.edited)
        assertEquals(
            listOf("one", "three"),
            viewModel.state.value.rows
                .map { it.text },
        )
    }

    @Test
    fun `deleting removes the row, also when it is already gone`() {
        api.pages[null] = ApiResult.Ok(MemoryPage(listOf(memory("a", "one"), memory("b", "two"))))
        val viewModel = newViewModel()

        viewModel.delete("a")
        api.deleteResult = ApiResult.Failure(FailureKind.NotFound, "Not found.")
        viewModel.delete("b")

        assertTrue(
            viewModel.state.value.rows
                .isEmpty(),
        )
    }

    @Test
    fun `a timestamp with an offset parses and a bad one leaves no date`() {
        api.pages[null] =
            ApiResult.Ok(
                MemoryPage(
                    listOf(
                        memory("a", "one", "Lunch", "2026-09-29T09:00:00+00:00", "c1"),
                        memory("b", "two", "Walk", "not a date", "c2"),
                    ),
                ),
            )

        val rows = newViewModel().state.value.rows

        assertEquals("Lunch · Today", rows[0].source)
        assertEquals("Walk", rows[1].source)
    }

    @Test
    fun `editing a memory that is gone says so and reads the list again`() {
        api.pages[null] = ApiResult.Ok(MemoryPage(listOf(memory("a", "one"))))
        api.write = ApiResult.Failure(FailureKind.NotFound, "Not found.")
        val viewModel = newViewModel()

        viewModel.startEdit("a")
        viewModel.save("two")

        assertEquals(
            "This item no longer exists",
            viewModel.state.value.editor
                ?.error,
        )
        assertEquals(listOf(null, null), api.requested)
    }

    @Test
    fun `a read token cannot delete`() {
        api.pages[null] = ApiResult.Ok(MemoryPage(listOf(memory("a", "one"))))
        api.deleteResult = ApiResult.Failure(FailureKind.Forbidden, "no")
        val viewModel = newViewModel()

        viewModel.delete("a")

        assertEquals("The app needs an admin token.", viewModel.state.value.error)
        assertEquals(1, viewModel.state.value.rows.size)
    }
}

private fun memory(
    id: String,
    text: String,
    title: String? = null,
    started: String? = null,
    conversationId: String? = null,
) = Memory(
    id = id,
    text = text,
    source = if (conversationId == null) "user" else "ai",
    conversationId = conversationId,
    conversationTitle = title,
    conversationStartedAt = started,
    createdAt = "2026-09-29T10:00:00Z",
    updatedAt = "2026-09-29T10:00:00Z",
)
