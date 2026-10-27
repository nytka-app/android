package io.github.nytka_app.ui.timeline

import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.DayEvent
import io.github.nytka_app.core.api.DayEvents
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.Salience
import io.github.nytka_app.core.api.Scene
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.api.Timeline
import io.github.nytka_app.ui.today.FakeEvents
import io.github.nytka_app.ui.today.FakeItemMarks
import io.github.nytka_app.ui.today.FakeToday
import io.github.nytka_app.ui.today.item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class TimelineViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC)
    private val api = FakeToday()
    private val marks = FakeItemMarks()
    private val events = FakeEvents()
    private val withEvents =
        InfoClient { ApiResult.Ok(ServerInfo("0.36.7", 1, features = listOf(ServerInfo.FEATURE_EVENTS))) }

    private fun event(
        id: String,
        at: String,
        kind: String = DayEvent.PERSON_JOINED,
        conversationId: String? = null,
    ) = DayEvent(id, kind, at, conversationId, voiceLabel = "Olena")

    private fun at(time: String) = "2026-10-23T$time:00Z"

    private fun sceneAt(
        id: String,
        start: String,
        noise: Boolean = false,
        sensitivity: String? = null,
    ) = Scene(
        id,
        "c-$id",
        at(start),
        at("$start".replace(Regex(":.."), ":59")),
        noise = noise,
        sensitivity = sensitivity,
    )

    private fun ids(rows: List<TimelineRow>) =
        rows.map {
            when (it) {
                is TimelineRow.Of -> it.scene.id
                is TimelineRow.Fragments -> "fragments"
                is TimelineRow.Event -> it.event.id
            }
        }

    private fun scene(
        id: String,
        noise: Boolean = false,
    ) = Scene(id, "c-$id", "2026-10-23T08:00:00Z", "2026-10-23T08:10:00Z", noise = noise, items = listOf(item("i-$id")))

    @Test
    fun `opens on today and steps back and forth, never past today`() {
        val model = TimelineViewModel(api, marks, events, withEvents, clock)
        assertFalse(model.state.value.canGoForward)

        model.nextDay()
        model.previousDay()
        model.previousDay()
        model.nextDay()

        assertEquals(LocalDate.parse("2026-10-22"), model.state.value.date)
        assertEquals(
            listOf("timeline 2026-10-23 Z", "timeline 2026-10-22 Z", "timeline 2026-10-21 Z", "timeline 2026-10-22 Z"),
            api.calls,
        )
        model.showToday()
        assertEquals(LocalDate.parse("2026-10-23"), model.state.value.date)
    }

    @Test
    fun `runs of fragments fold into one row between scenes`() {
        val rows = TimelineViewModel.rows(listOf(scene("a", true), scene("b", true), scene("c"), scene("d", true)))

        assertEquals(
            listOf(
                TimelineRow.Fragments(listOf(scene("a", true), scene("b", true))),
                TimelineRow.Of(scene("c")),
                TimelineRow.Fragments(listOf(scene("d", true))),
            ),
            rows,
        )
    }

    @Test
    fun `not important keeps the item on the Timeline, marked noise by the wearer`() {
        api.timelines["2026-10-23"] = ApiResult.Ok(Timeline("2026-10-23", listOf(scene("a"))))
        val model = TimelineViewModel(api, marks, events, withEvents, clock)

        model.notImportant(item("i-a"))

        val shown =
            (
                model.state.value.rows
                    .single() as TimelineRow.Of
            ).scene.items.single()
        assertEquals(Salience.NOISE, shown.salience)
        assertTrue(shown.salienceManual)
        assertEquals(listOf("salience task i-a noise"), marks.marks)
    }

    @Test
    fun `events sit between scenes and fragments by time, an event first on a tie`() {
        val rows =
            TimelineViewModel.rows(
                listOf(sceneAt("a", "08:00"), sceneAt("f", "09:00", noise = true), sceneAt("b", "10:00")),
            )

        val merged =
            TimelineViewModel.withEvents(
                rows,
                listOf(
                    event("late", at("11:00")),
                    event("tie", at("10:00")),
                    event("early", at("07:00")),
                    event("mid", at("08:30")),
                ),
            )

        assertEquals(listOf("early", "a", "mid", "fragments", "tie", "b", "late"), ids(merged))
    }

    @Test
    fun `unknown kinds, unreadable times and events of a private scene are dropped`() {
        val rows = TimelineViewModel.rows(listOf(sceneAt("a", "08:00", sensitivity = "therapy"), sceneAt("b", "09:00")))

        val merged =
            TimelineViewModel.withEvents(
                rows,
                listOf(
                    event("hidden", at("08:10"), conversationId = "c-a"),
                    event("open", at("09:10"), conversationId = "c-b"),
                    event("odd", at("09:20"), kind = "place_changed"),
                    event("badtime", "yesterday"),
                    event("free", at("08:20")),
                ),
            )

        assertEquals(listOf("a", "free", "b", "open"), ids(merged))
    }

    @Test
    fun `a server with events gets them for the day shown and again after stepping back`() {
        api.timelines["2026-10-23"] = ApiResult.Ok(Timeline("2026-10-23", listOf(sceneAt("a", "08:00"))))
        events.days["2026-10-23"] =
            ApiResult.Ok(DayEvents("2026-10-23", listOf(event("e1", at("07:00")), event("e2", at("09:00")))))
        val model = TimelineViewModel(api, marks, events, withEvents, clock)

        assertEquals(listOf("e1", "a", "e2"), ids(model.state.value.rows))

        model.previousDay()

        assertEquals(listOf("events 2026-10-23 Z", "events 2026-10-22 Z"), events.calls)
        assertTrue(
            model.state.value.rows
                .isEmpty(),
        )
    }

    @Test
    fun `a server without events is not asked`() {
        val without = InfoClient { ApiResult.Ok(ServerInfo("0.35.0", 1, features = listOf(ServerInfo.FEATURE_TODAY))) }
        api.timelines["2026-10-23"] = ApiResult.Ok(Timeline("2026-10-23", listOf(sceneAt("a", "08:00"))))

        val model = TimelineViewModel(api, marks, events, without, clock)

        assertEquals(listOf("a"), ids(model.state.value.rows))
        assertTrue(events.calls.isEmpty())
    }

    @Test
    fun `a failed events call leaves the scenes and sets no error`() {
        api.timelines["2026-10-23"] = ApiResult.Ok(Timeline("2026-10-23", listOf(sceneAt("a", "08:00"))))
        events.days["2026-10-23"] = ApiResult.Failure(FailureKind.Server, "The server answered 500.")

        val model = TimelineViewModel(api, marks, events, withEvents, clock)

        assertEquals(listOf("a"), ids(model.state.value.rows))
        assertEquals(null, model.state.value.error)
        assertFalse(model.state.value.offline)
    }

    @Test
    fun `an event row survives marking an item not important`() {
        api.timelines["2026-10-23"] = ApiResult.Ok(Timeline("2026-10-23", listOf(scene("a"))))
        events.days["2026-10-23"] = ApiResult.Ok(DayEvents("2026-10-23", listOf(event("e1", at("07:00")))))
        val model = TimelineViewModel(api, marks, events, withEvents, clock)

        model.notImportant(item("i-a"))

        assertEquals(listOf("e1", "a"), ids(model.state.value.rows))
    }
}
