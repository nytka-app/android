package io.github.nytka_app.ui.a11y

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.DayEvent
import io.github.nytka_app.core.api.DayEvents
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.Scene
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.api.Timeline
import io.github.nytka_app.ui.timeline.TimelineScreen
import io.github.nytka_app.ui.timeline.TimelineViewModel
import io.github.nytka_app.ui.today.FakeEvents
import io.github.nytka_app.ui.today.FakeItemMarks
import io.github.nytka_app.ui.today.FakeToday
import io.github.nytka_app.ui.today.item
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
class TimelineA11yTest {
    @get:Rule(order = 0)
    val main = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC)
    private val withEvents =
        InfoClient { ApiResult.Ok(ServerInfo("0.36.7", 1, features = listOf(ServerInfo.FEATURE_EVENTS))) }

    private fun scenes() =
        listOf(
            Scene(
                "a",
                "c-a",
                "2026-10-23T08:00:00Z",
                "2026-10-23T08:40:00Z",
                title = "Breakfast with Olena about the flat",
                summary = "They agreed to look at two flats on Saturday and to send the contract before Friday.",
                place = "Kyiv",
                items = listOf(item("i-a"), item("i-b", kind = "memory")),
            ),
            Scene(
                "b",
                "c-b",
                "2026-10-23T10:00:00Z",
                "2026-10-23T10:05:00Z",
                noise = true,
                items = listOf(item("i-c")),
            ),
        )

    private fun show(
        fontScale: Float,
        dark: Boolean,
        scenes: List<Scene>,
        events: List<DayEvent> = emptyList(),
    ) {
        val api = FakeToday().also { it.timelines["2026-10-23"] = ApiResult.Ok(Timeline("2026-10-23", scenes)) }
        val feed = FakeEvents().also { it.days["2026-10-23"] = ApiResult.Ok(DayEvents("2026-10-23", events)) }
        val model = TimelineViewModel(api, FakeItemMarks(), feed, withEvents, clock)
        compose.setScreen(fontScale, dark) {
            TimelineScreen(
                onOpenMoment = { _, _ -> },
                onOpenPerson = {},
                onOpenConversations = {},
                viewModel = model,
            )
        }
    }

    private fun clean(
        fontScale: Float,
        dark: Boolean = false,
        scenes: List<Scene> = scenes(),
        events: List<DayEvent> =
            listOf(DayEvent("e1", DayEvent.PERSON_JOINED, "2026-10-23T07:00:00Z", voiceLabel = "Olena")),
    ) {
        show(fontScale, dark, scenes, events)
        compose.assertClean()
    }

    @Test
    fun `timeline at 1_0 in light`() = clean(1f)

    @Test
    fun `timeline at 1_0 in dark`() = clean(1f, dark = true)

    @Test
    fun `timeline at 1_3 in light`() = clean(1.3f)

    @Test
    fun `timeline at 2_0 in light`() = clean(2f)

    @Test
    fun `timeline at 2_0 in dark`() = clean(2f, dark = true)

    @Test
    fun `empty timeline at 2_0`() = clean(2f, scenes = emptyList(), events = emptyList())

    @Test
    fun `a day reads in this order`() {
        show(1f, false, scenes(), emptyList())
        assertEquals(
            listOf(
                "Previous day [Button]",
                "Today [heading]",
                "Next day [Button]",
                "08:00–08:40 · Kyiv, Breakfast with Olena about the flat, " +
                    "They agreed to look at two flats on Saturday and to send the contract before Friday.",
                "Vital",
                "Task, Item i-a, 07:00",
                "Actions for “Item i-a” [Button]",
                "Vital",
                "Memory, Item i-b, 07:00",
                "Actions for “Item i-b” [Button]",
                "1 short fragment",
                "All conversations [Button]",
            ),
            compose.spokenOrder(),
        )
    }
}
