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

        assertEquals(MemoryRow("a", "Lives in Kyiv", "Lunch · Today", "c1", MemoryGroup.Today), rows[0])
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
    fun `forgetting hides the row at once and asks the server only on commit`() {
        api.pages[null] = ApiResult.Ok(MemoryPage(listOf(memory("a", "one"), memory("b", "two"))))
        val viewModel = newViewModel()

        viewModel.forget("a")

        assertEquals(
            listOf("b"),
            viewModel.state.value.rows
                .map { it.id },
        )
        assertEquals("a", viewModel.state.value.forgotten)
        assertTrue(api.deleted.isEmpty())

        viewModel.commitForget("a")

        assertEquals(listOf("a"), api.deleted)
        assertNull(viewModel.state.value.forgotten)
    }

    @Test
    fun `undo puts the row back in its place and sends nothing`() {
        api.pages[null] = ApiResult.Ok(MemoryPage(listOf(memory("a", "one"), memory("b", "two"), memory("c", "three"))))
        val viewModel = newViewModel()

        viewModel.forget("b")
        viewModel.undoForget("b")
        viewModel.commitForget("b")

        assertEquals(
            listOf("a", "b", "c"),
            viewModel.state.value.rows
                .map { it.id },
        )
        assertTrue(api.deleted.isEmpty())
    }

    @Test
    fun `a second forget commits the first`() {
        api.pages[null] = ApiResult.Ok(MemoryPage(listOf(memory("a", "one"), memory("b", "two"))))
        val viewModel = newViewModel()

        viewModel.forget("a")
        viewModel.forget("b")

        assertEquals(listOf("a"), api.deleted)
        assertEquals("b", viewModel.state.value.forgotten)
    }

    @Test
    fun `a memory that is already gone stays gone`() {
        api.pages[null] = ApiResult.Ok(MemoryPage(listOf(memory("a", "one"))))
        val viewModel = newViewModel()

        viewModel.forget("a")
        api.deleteResult = ApiResult.Failure(FailureKind.NotFound, "Not found.")
        viewModel.commitForget("a")

        assertTrue(
            viewModel.state.value.rows
                .isEmpty(),
        )
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `a refresh while a memory waits for Undo does not bring it back`() {
        api.pages[null] = ApiResult.Ok(MemoryPage(listOf(memory("a", "one"), memory("b", "two"))))
        val viewModel = newViewModel()

        viewModel.forget("a")
        viewModel.refresh()

        assertEquals(
            listOf("b"),
            viewModel.state.value.rows
                .map { it.id },
        )
    }

    @Test
    fun `memories are grouped by how long ago they were learned`() {
        api.pages[null] =
            ApiResult.Ok(
                MemoryPage(
                    listOf(
                        memory("a", "one", created = "2026-09-29T08:00:00Z"),
                        memory("b", "two", created = "2026-09-25T08:00:00Z"),
                        memory("c", "three", created = "2026-09-10T08:00:00Z"),
                        memory("d", "four", created = "2026-07-01T08:00:00Z"),
                    ),
                ),
            )

        assertEquals(
            listOf(MemoryGroup.Today, MemoryGroup.ThisWeek, MemoryGroup.ThisMonth, MemoryGroup.Earlier),
            newViewModel()
                .state.value.rows
                .map { it.group },
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
    fun `a read token cannot delete and the row comes back`() {
        api.pages[null] = ApiResult.Ok(MemoryPage(listOf(memory("a", "one"))))
        api.deleteResult = ApiResult.Failure(FailureKind.Forbidden, "no")
        val viewModel = newViewModel()

        viewModel.forget("a")
        viewModel.commitForget("a")

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
    created: String = "2026-09-29T10:00:00Z",
) = Memory(
    id = id,
    text = text,
    source = if (conversationId == null) "user" else "ai",
    conversationId = conversationId,
    conversationTitle = title,
    conversationStartedAt = started,
    createdAt = created,
    updatedAt = "2026-09-29T10:00:00Z",
)
