package io.github.nytka_app.ui.tasks

import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.ui.tasks.FakeTasks.Companion.task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class TasksViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC)
    private val api =
        FakeTasks(listOf(task("3"), task("2", startedAt = "2026-09-28T09:00:00Z"), task("1", done = true)))

    private fun viewModel() = TasksViewModel(api, clock)

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
    fun `a server without the endpoint says it needs an update`() {
        api.failure = ApiResult.Failure(FailureKind.NotFound, "Not found.")

        val state = viewModel().state.value

        assertEquals("This server needs an update.", state.error)
        assertTrue(state.open.isEmpty())
        assertFalse(state.loading)
    }
}
