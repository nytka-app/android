package io.github.nytka_app.ui.timeline

import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.Salience
import io.github.nytka_app.core.api.Scene
import io.github.nytka_app.core.api.Timeline
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

    private fun scene(
        id: String,
        noise: Boolean = false,
    ) = Scene(id, "c-$id", "2026-10-23T08:00:00Z", "2026-10-23T08:10:00Z", noise = noise, items = listOf(item("i-$id")))

    @Test
    fun `opens on today and steps back and forth, never past today`() {
        val model = TimelineViewModel(api, marks, clock)
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
        val model = TimelineViewModel(api, marks, clock)

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
}
