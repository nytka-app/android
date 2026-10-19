package io.github.nytka_app.ui.tasks

import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.ui.tags.fakeInfo
import io.github.nytka_app.ui.tasks.FakeTasks.Companion.task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class TasksViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC)
    private val api =
        FakeTasks(listOf(task("3"), task("2", startedAt = "2026-09-28T09:00:00Z"), task("1", done = true)))

    private var features = arrayOf(ServerInfo.FEATURE_TASK_CREATE)

    private fun viewModel() = TasksViewModel(api, fakeInfo(*features), clock)

    @Test
    fun `open tasks come newest first with their conversation and day`() {
        val state = viewModel().state.value

        assertEquals(listOf("3", "2"), state.open.map { it.id })
        assertEquals("Standup · Today", state.open[0].source)
        assertEquals("Standup · Yesterday", state.open[1].source)
        assertEquals("c1", state.open[0].conversationId)
        assertFalse(state.loading)
    }

    @Test
    fun `a task without conversation fields shows no source`() {
        api.all[0] = task("3", title = null, startedAt = null)

        assertEquals(
            "",
            viewModel()
                .state.value.open[0]
                .source,
        )
    }

    @Test
    fun `done tasks stay collapsed and unloaded until asked for`() {
        val viewModel = viewModel()

        assertFalse(viewModel.state.value.completedShown)
        assertTrue(api.calls.none { it.startsWith("list done") })

        viewModel.toggleCompleted()

        assertEquals(
            listOf("1"),
            viewModel.state.value.completed
                .map { it.id },
        )
        assertTrue(viewModel.state.value.completedShown)
    }

    @Test
    fun `done tasks load 30 at a time`() {
        repeat(35) { api.all += task("d%02d".format(it), done = true) }
        val viewModel = viewModel()

        viewModel.toggleCompleted()
        assertEquals(30, viewModel.state.value.completed.size)
        assertFalse(viewModel.state.value.completedEndReached)

        viewModel.loadMoreCompleted()
        assertEquals(36, viewModel.state.value.completed.size)
        assertTrue(viewModel.state.value.completedEndReached)
    }

    @Test
    fun `open tasks page on with the cursor`() {
        repeat(60) { api.all += task("t%02d".format(it)) }
        val viewModel = viewModel()
        assertEquals(50, viewModel.state.value.open.size)

        viewModel.loadMoreOpen()

        assertEquals(62, viewModel.state.value.open.size)
        assertTrue(viewModel.state.value.openEndReached)
    }

    @Test
    fun `ticking moves a task to done and tells the server`() {
        val viewModel = viewModel()
        viewModel.toggleCompleted()

        viewModel.setDone("3", true)

        assertEquals(
            listOf("2"),
            viewModel.state.value.open
                .map { it.id },
        )
        assertEquals(
            listOf("3", "1"),
            viewModel.state.value.completed
                .map { it.id },
        )
        assertTrue(api.all.first { it.id == "3" }.done)
    }

    @Test
    fun `a ticked task is still ticked after a refresh`() {
        val viewModel = viewModel()

        viewModel.setDone("3", true)
        viewModel.refresh()

        assertEquals(
            listOf("2"),
            viewModel.state.value.open
                .map { it.id },
        )
    }

    @Test
    fun `unticking brings a task back among the open ones in order`() {
        val viewModel = viewModel()
        viewModel.toggleCompleted()

        viewModel.setDone("1", false)

        assertEquals(
            listOf("3", "2", "1"),
            viewModel.state.value.open
                .map { it.id },
        )
        assertTrue(
            viewModel.state.value.completed
                .isEmpty(),
        )
    }

    @Test
    fun `a refused tick reloads what the server holds, with a message`() {
        val viewModel = viewModel()
        api.failure = ApiResult.Failure(FailureKind.Forbidden, "The token is not allowed to do this.")

        viewModel.setDone("3", true)

        assertEquals("The app needs an admin token.", viewModel.state.value.error)
        api.failure = null
        viewModel.refresh()
        assertEquals(
            listOf("3", "2"),
            viewModel.state.value.open
                .map { it.id },
        )
    }

    @Test
    fun `editing changes the text in place`() {
        val viewModel = viewModel()

        viewModel.edit("3", "  Call Anna by Friday  ")

        assertEquals(
            "Call Anna by Friday",
            viewModel.state.value.open
                .first { it.id == "3" }
                .text,
        )
        assertEquals("Call Anna by Friday", api.all.first { it.id == "3" }.text)
    }

    @Test
    fun `an empty edit is not sent`() {
        val viewModel = viewModel()

        viewModel.edit("3", "   ")

        assertTrue(api.calls.none { it.startsWith("edit") })
    }

    @Test
    fun `deleting drops the task`() {
        val viewModel = viewModel()

        viewModel.delete("3")

        assertEquals(
            listOf("2"),
            viewModel.state.value.open
                .map { it.id },
        )
        assertEquals(listOf("2", "1"), api.all.map { it.id }.sortedDescending())
    }

    @Test
    fun `a task that is gone says so instead of asking for an update`() {
        val viewModel = viewModel()
        api.failure = ApiResult.Failure(FailureKind.NotFound, "Not found.")

        viewModel.delete("3")

        assertEquals("This item no longer exists", viewModel.state.value.error)
    }

    @Test
    fun `a server without the endpoint says it needs an update`() {
        api.failure = ApiResult.Failure(FailureKind.NotFound, "Not found.")

        val state = viewModel().state.value

        assertEquals("This server needs an update", state.error)
        assertTrue(state.open.isEmpty())
        assertFalse(state.loading)
    }

    @Test
    fun `adding needs the task-create feature`() {
        assertTrue(viewModel().state.value.canCreate)

        features = emptyArray()
        val old = viewModel()
        old.create("Pay rent", null)

        assertFalse(old.state.value.canCreate)
        assertTrue(api.calls.none { it.startsWith("create") })
    }

    @Test
    fun `a new task goes on top with its due date and no conversation`() {
        val viewModel = viewModel()

        viewModel.create("  Pay rent  ", LocalDate.of(2026, 10, 3))

        val added =
            viewModel.state.value.open
                .first()
        assertEquals("Pay rent", added.text)
        assertEquals(null, added.conversationId)
        assertEquals("", added.source)
        assertEquals(LocalDate.of(2026, 10, 3), added.due)
        assertEquals("Due Saturday, 3 October", added.dueText)
        assertFalse(added.overdue)
        assertTrue("create Pay rent due=2026-10-03" in api.calls)
    }

    @Test
    fun `a blank text is never sent`() {
        val viewModel = viewModel()

        viewModel.create("   ", null)

        assertTrue(api.calls.none { it.startsWith("create") })
    }

    @Test
    fun `a refused add says so and keeps the list`() {
        val viewModel = viewModel()
        api.failure = ApiResult.Failure(FailureKind.NotFound, "Not found.")

        viewModel.create("Pay rent", null)

        assertEquals("This server needs an update", viewModel.state.value.error)
        assertEquals(
            listOf("3", "2"),
            viewModel.state.value.open
                .map { it.id },
        )
    }

    @Test
    fun `a due date is set, shown as overdue when past, and cleared`() {
        val viewModel = viewModel()

        viewModel.setDue("3", LocalDate.of(2026, 9, 28))

        val late =
            viewModel.state.value.open
                .first { it.id == "3" }
        assertEquals("Due Yesterday", late.dueText)
        assertTrue(late.overdue)

        viewModel.setDue("3", null)

        val none =
            viewModel.state.value.open
                .first { it.id == "3" }
        assertEquals(null, none.due)
        assertEquals(null, none.dueText)
        assertFalse(none.overdue)
    }

    @Test
    fun `a done task past its date is not overdue`() {
        api.all[2] = task("1", done = true, due = "2026-09-01T00:00:00Z")
        val viewModel = viewModel()
        viewModel.toggleCompleted()

        val done =
            viewModel.state.value.completed
                .single()
        assertEquals(LocalDate.of(2026, 9, 1), done.due)
        assertFalse(done.overdue)
    }

    @Test
    fun `a due date that is gone says the item no longer exists`() {
        val viewModel = viewModel()

        viewModel.setDue("nope", LocalDate.of(2026, 10, 1))

        assertEquals("This item no longer exists", viewModel.state.value.error)
    }
}
