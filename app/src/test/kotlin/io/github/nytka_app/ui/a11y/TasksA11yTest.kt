package io.github.nytka_app.ui.a11y

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.NytkaTask
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.ui.tags.fakeInfo
import io.github.nytka_app.ui.tasks.FakeTasks
import io.github.nytka_app.ui.tasks.FakeTasks.Companion.task
import io.github.nytka_app.ui.tasks.TasksTab
import io.github.nytka_app.ui.tasks.TasksViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h3000dp")
class TasksA11yTest {
    @get:Rule(order = 0)
    val main = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC)

    private fun tasks(): List<NytkaTask> =
        listOf(
            task("4", "Send Olena the contract before Friday so she can sign it", due = "2026-09-27T00:00:00Z"),
            task("3", "Book the dentist", conversationId = null, title = null, startedAt = null),
            task("2", "Call Mykola about the flat", due = "2026-10-05T00:00:00Z"),
            task("1", "Buy a charging cable", done = true),
        )

    private fun show(
        fontScale: Float,
        dark: Boolean,
        items: List<NytkaTask>,
        doneShown: Boolean,
        failure: ApiResult.Failure?,
    ) {
        val api = FakeTasks(items).also { it.failure = failure }
        val model = TasksViewModel(api, fakeInfo(ServerInfo.FEATURE_TASK_CREATE), clock)
        if (doneShown) model.toggleCompleted()
        compose.setScreen(fontScale, dark) { TasksTab(onOpenConversation = {}, viewModel = model) }
    }

    private fun clean(
        fontScale: Float,
        dark: Boolean = false,
        items: List<NytkaTask> = tasks(),
        doneShown: Boolean = false,
        failure: ApiResult.Failure? = null,
    ) {
        show(fontScale, dark, items, doneShown, failure)
        compose.assertClean()
    }

    @Test
    fun `tasks at 1_0 in light`() = clean(1f)

    @Test
    fun `tasks at 1_0 in dark`() = clean(1f, dark = true)

    @Test
    fun `tasks at 1_3 in light`() = clean(1.3f)

    @Test
    fun `tasks at 2_0 in light`() = clean(2f)

    @Test
    fun `tasks at 2_0 in dark`() = clean(2f, dark = true)

    @Test
    fun `tasks with the done list open at 2_0`() = clean(2f, doneShown = true)

    @Test
    fun `no tasks at 2_0`() = clean(2f, items = emptyList())

    @Test
    fun `a failed list at 2_0`() =
        clean(2f, failure = ApiResult.Failure(FailureKind.Network, "The server did not answer."))

    @Test
    fun `tasks read in this order`() {
        show(1f, false, tasks(), doneShown = true, failure = null)
        assertEquals(
            listOf(
                "Send Olena the contract before Friday so she can sign it, Standup · Today, Due Sunday, 27 September",
                "Send Olena the contract before Friday so she can sign it [Checkbox]",
                "More [Button]",
                "Book the dentist",
                "Book the dentist [Checkbox]",
                "More [Button]",
                "Call Mykola about the flat, Standup · Today, Due Monday, 5 October",
                "Call Mykola about the flat [Checkbox]",
                "More [Button]",
                "Hide done tasks, Done [Button]",
                "Buy a charging cable, Standup · Today",
                "Buy a charging cable [Checkbox]",
                "More [Button]",
                "Add a task [Button]",
            ),
            compose.spokenOrder(),
        )
    }
}
