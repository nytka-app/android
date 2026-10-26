package io.github.nytka_app.ui.today

import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.DayPart
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.LifeItem
import io.github.nytka_app.core.api.Salience
import io.github.nytka_app.core.api.TodayPage
import io.github.nytka_app.ui.NEEDS_UPDATE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class TodayViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val zone = ZoneId.of("Europe/Kyiv")
    private val clock = Clock.fixed(Instant.parse("2026-10-23T17:00:00Z"), zone)
    private val api = FakeToday()
    private val marks = FakeItemMarks()

    private fun newViewModel() = TodayViewModel(api, marks, clock)

    private fun ids(model: TodayViewModel) =
        model.state.value.sections
            .flatMap { s -> s.items.map { it.id } }

    @Test
    fun `asks for today in the phone's zone`() {
        newViewModel()

        assertEquals(listOf("today Europe/Kyiv"), api.calls)
    }

    @Test
    fun `items fall into morning, day and evening by the hour said in the phone's zone`() {
        // Kyiv is UTC+3 on this day: 08:00, 13:00 and 19:00 local.
        api.today =
            ApiResult.Ok(
                TodayPage(
                    "2026-10-23",
                    listOf(
                        item("e", "2026-10-23T16:00:00Z"),
                        item("m", "2026-10-23T05:00:00Z"),
                        item("d", "2026-10-23T10:00:00Z"),
                    ),
                ),
            )

        val state = newViewModel().state.value

        assertEquals(listOf(DayPart.MORNING, DayPart.DAY, DayPart.EVENING), state.sections.map { it.part })
        assertEquals(listOf("m", "d", "e"), state.sections.map { it.items.single().id })
    }

    @Test
    fun `the server's part wins over the hour`() {
        api.today =
            ApiResult.Ok(TodayPage("2026-10-23", listOf(item("due", "2026-10-22T06:00:00Z", part = DayPart.EVENING))))

        assertEquals(
            DayPart.EVENING,
            newViewModel()
                .state.value.sections
                .single()
                .part,
        )
    }

    @Test
    fun `the server's pick shows, but never noise, nothing sensitive and seven at most`() {
        val vital = (1..8).map { item("v$it", "2026-10-23T0$it:00:00Z") }
        api.today =
            ApiResult.Ok(
                TodayPage(
                    "2026-10-23",
                    listOf(
                        item("owed", "2026-10-23T00:30:00Z", salience = Salience.ORDINARY),
                        item("therapy", sensitivity = "therapy"),
                        item("noise", salience = Salience.NOISE),
                    ) + vital,
                ),
            )

        val shown = ids(newViewModel())

        assertEquals(listOf("owed") + (1..6).map { "v$it" }, shown)
    }

    @Test
    fun `an empty day is empty, not loading`() {
        val state = newViewModel().state.value

        assertTrue(state.empty)
        assertFalse(state.loading)
        assertNull(state.error)
    }

    @Test
    fun `an older server needs an update and an unreachable one is offline`() {
        api.today = ApiResult.Failure(FailureKind.NotFound, "Not found")
        assertEquals(NEEDS_UPDATE, newViewModel().state.value.error)

        api.today = ApiResult.Failure(FailureKind.Network, "No network")
        val offline = newViewModel().state.value
        assertTrue(offline.offline)
        assertNull(offline.error)
    }

    @Test
    fun `not important takes the item off at once and marks it noise`() {
        api.today = ApiResult.Ok(TodayPage("2026-10-23", listOf(item("a"), item("b", kind = LifeItem.KIND_MEMORY))))
        val model = newViewModel()

        model.notImportant(
            model.state.value.sections
                .single()
                .items[1],
        )

        assertEquals(listOf("a"), ids(model))
        assertEquals(listOf("salience memory b noise"), marks.marks)
        assertEquals(
            CorrectionKind.NotImportant,
            model.state.value.notice
                ?.kind,
        )
        assertEquals(
            "b",
            model.state.value.notice
                ?.undoable
                ?.id,
        )
    }

    @Test
    fun `a refused mark puts the item back and says why`() {
        api.today = ApiResult.Ok(TodayPage("2026-10-23", listOf(item("a"))))
        marks.answer = ApiResult.Failure(FailureKind.Forbidden, "Forbidden")
        val model = newViewModel()

        model.notImportant(
            model.state.value.sections
                .single()
                .items
                .single(),
        )

        assertEquals(listOf("a"), ids(model))
        assertEquals(
            "The app needs an admin token.",
            model.state.value.notice
                ?.failure,
        )
    }

    @Test
    fun `undo clears the mark the wearer made and reloads`() {
        api.today = ApiResult.Ok(TodayPage("2026-10-23", listOf(item("a"))))
        val model = newViewModel()

        model.undo(item("a"))

        assertEquals(listOf("salience task a null"), marks.marks)
        assertEquals(2, api.calls.size)
        assertEquals(
            CorrectionKind.Undone,
            model.state.value.notice
                ?.kind,
        )
    }

    @Test
    fun `undo puts back a mark the wearer had made before`() {
        val model = newViewModel()

        model.undo(item("a", salience = Salience.VITAL).copy(salienceManual = true))

        assertEquals(listOf("salience task a vital"), marks.marks)
    }

    @Test
    fun `not me marks the line and reloads`() {
        val model = newViewModel()

        model.notMe(line(7))

        assertEquals(listOf("line 7 false"), marks.marks)
        assertEquals(2, api.calls.size)
    }

    @Test
    fun `someone else's line stops being the wearer's, then gets the person`() {
        val model = newViewModel()

        model.otherPerson(line(7), "p2")
        model.otherPerson(line(8, isUser = false), "p3")

        assertEquals(listOf("line 7 false", "person 7 p2", "person 8 p3"), marks.marks)
    }

    @Test
    fun `an item without a time counts as the day's`() {
        assertEquals(DayPart.DAY, TodayViewModel.partOf(item("x", at = null), zone))
    }
}
