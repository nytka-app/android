package io.github.nytka_app.briefs

import io.github.nytka_app.FakeSettings
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.BriefText
import io.github.nytka_app.core.api.BriefsClient
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.UpcomingBrief
import io.github.nytka_app.core.settings.Settings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class BriefPollTest {
    private val now = Instant.parse("2026-10-05T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val settings = FakeSettings(Settings(briefNotifications = true))
    private val posted = mutableListOf<String>()
    private var canPost = true

    private class FakeBriefs : BriefsClient {
        var answer: ApiResult<List<UpcomingBrief>> = ApiResult.Ok(emptyList())
        val asked = mutableListOf<Int>()

        override suspend fun upcoming(minutes: Int): ApiResult<List<UpcomingBrief>> {
            asked += minutes
            return answer
        }
    }

    private val client = FakeBriefs()
    private val poll =
        BriefPoll(client, settings, {
            if (canPost) posted += it.brief!!.id
            canPost
        }, clock)

    private fun event(
        briefId: String?,
        endsAt: String = "2026-10-05T13:00:00Z",
    ) = UpcomingBrief(
        uid = "uid-$briefId",
        title = "Sync",
        startsAt = "2026-10-05T12:30:00Z",
        endsAt = endsAt,
        brief = briefId?.let { BriefText(it, "text") },
    )

    private fun listed(vararg events: UpcomingBrief) {
        client.answer = ApiResult.Ok(events.toList())
    }

    @Test
    fun `a new brief posts once`() =
        runTest {
            listed(event("a"))

            poll.run()

            assertEquals(listOf("a"), posted)
            assertEquals(listOf("a"), settings.state.value.notifiedBriefs)
            assertEquals(listOf(240), client.asked)
        }

    @Test
    fun `the same id on the next run posts nothing`() =
        runTest {
            listed(event("a"))

            poll.run()
            poll.run()

            assertEquals(listOf("a"), posted)
        }

    @Test
    fun `an event that is over posts nothing`() =
        runTest {
            listed(event("a", endsAt = "2026-10-05T12:00:00Z"), event("b", endsAt = "2026-10-05T11:00:00Z"))

            poll.run()

            assertEquals(emptyList<String>(), posted)
            assertEquals(emptyList<String>(), settings.state.value.notifiedBriefs)
        }

    @Test
    fun `a time with no offset counts as UTC`() =
        runTest {
            listed(event("a", endsAt = "2026-10-05T11:59:00"), event("b", endsAt = "2026-10-05T12:01:00"))

            poll.run()

            assertEquals(listOf("b"), posted)
        }

    @Test
    fun `an event without a brief posts nothing`() =
        runTest {
            listed(event(null))

            poll.run()

            assertEquals(emptyList<String>(), posted)
            assertEquals(emptyList<String>(), settings.state.value.notifiedBriefs)
        }

    @Test
    fun `ids no longer listed are dropped`() =
        runTest {
            settings.state.value = settings.state.value.copy(notifiedBriefs = listOf("old", "a"))
            listed(event("a"), event("b"))

            poll.run()

            assertEquals(listOf("b"), posted)
            assertEquals(listOf("a", "b"), settings.state.value.notifiedBriefs)
        }

    @Test
    fun `at most 50 ids are kept`() =
        runTest {
            val ids = (1..60).map { "id$it" }
            listed(*ids.map { event(it) }.toTypedArray())

            poll.run()

            assertEquals(60, posted.size)
            assertEquals(ids.takeLast(50), settings.state.value.notifiedBriefs)
        }

    @Test
    fun `a failure posts nothing and keeps the ids`() =
        runTest {
            settings.state.value = settings.state.value.copy(notifiedBriefs = listOf("a"))
            client.answer = ApiResult.Failure(FailureKind.Network, "down")

            poll.run()

            assertEquals(emptyList<String>(), posted)
            assertEquals(listOf("a"), settings.state.value.notifiedBriefs)
        }

    @Test
    fun `a server without the route posts nothing`() =
        runTest {
            client.answer = ApiResult.Failure(FailureKind.NotFound, "no")

            poll.run()

            assertEquals(emptyList<String>(), posted)
        }

    @Test
    fun `a brief that could not be posted is tried again`() =
        runTest {
            listed(event("a"))
            canPost = false
            poll.run()
            assertEquals(emptyList<String>(), settings.state.value.notifiedBriefs)

            canPost = true
            poll.run()

            assertEquals(listOf("a"), posted)
        }

    @Test
    fun `with the switch off nothing is asked or posted`() =
        runTest {
            settings.state.value = settings.state.value.copy(briefNotifications = false)
            listed(event("a"))

            poll.run()

            assertEquals(emptyList<String>(), posted)
            assertEquals(emptyList<Int>(), client.asked)
        }
}
