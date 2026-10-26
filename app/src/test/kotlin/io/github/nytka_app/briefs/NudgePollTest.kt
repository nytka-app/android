package io.github.nytka_app.briefs

import io.github.nytka_app.FakeSettings
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.Nudge
import io.github.nytka_app.core.api.NudgesClient
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.settings.Settings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class NudgePollTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneId.of("Europe/Kyiv"))
    private val settings = FakeSettings(Settings())
    private val posted = mutableListOf<String>()
    private var canPost = true
    private var features = listOf(ServerInfo.FEATURE_NUDGES)

    private class FakeNudges : NudgesClient {
        var answer: ApiResult<List<Nudge>> = ApiResult.Ok(emptyList())
        val asked = mutableListOf<String>()

        override suspend fun recent(zone: String): ApiResult<List<Nudge>> {
            asked += zone
            return answer
        }
    }

    private val client = FakeNudges()
    private val info = InfoClient { ApiResult.Ok(ServerInfo("0.36.6", 1, features = features)) }
    private val poll =
        NudgePoll(client, info, settings, {
            if (canPost) posted += it.id
            canPost
        }, clock)

    private fun nudge(id: String) = Nudge(id, "recap", "Recap", "Your day", "nytka://today", "2026-10-26T11:00:00Z")

    private fun listed(vararg ids: String) {
        client.answer = ApiResult.Ok(ids.map(::nudge))
    }

    @Test
    fun `a new nudge posts once and the zone is sent`() =
        runTest {
            listed("a")

            poll.run()
            poll.run()

            assertEquals(listOf("a"), posted)
            assertEquals(listOf("a"), settings.state.value.notifiedNudges)
            assertEquals(listOf("Europe/Kyiv", "Europe/Kyiv"), client.asked)
        }

    @Test
    fun `a server without nudges is not asked`() =
        runTest {
            features = emptyList()
            listed("a")

            poll.run()

            assertEquals(emptyList<String>(), client.asked)
            assertEquals(emptyList<String>(), posted)
        }

    @Test
    fun `with the switch off nothing is asked or posted`() =
        runTest {
            settings.state.value = settings.state.value.copy(nudgeNotifications = false)
            listed("a")

            poll.run()

            assertEquals(emptyList<String>(), client.asked)
            assertEquals(emptyList<String>(), posted)
        }

    @Test
    fun `ids no longer listed are dropped and at most 50 are kept`() =
        runTest {
            settings.state.value = settings.state.value.copy(notifiedNudges = listOf("old", "a"))
            listed("a", "b")
            poll.run()
            assertEquals(listOf("a", "b"), settings.state.value.notifiedNudges)

            val ids = (1..60).map { "id$it" }
            client.answer = ApiResult.Ok(ids.map(::nudge))
            poll.run()
            assertEquals(ids.takeLast(50), settings.state.value.notifiedNudges)
        }

    @Test
    fun `a failure posts nothing and keeps the ids`() =
        runTest {
            settings.state.value = settings.state.value.copy(notifiedNudges = listOf("a"))
            client.answer = ApiResult.Failure(FailureKind.Network, "down")

            poll.run()

            assertEquals(emptyList<String>(), posted)
            assertEquals(listOf("a"), settings.state.value.notifiedNudges)
        }

    @Test
    fun `a nudge that could not be posted is tried again`() =
        runTest {
            listed("a")
            canPost = false
            poll.run()
            assertEquals(emptyList<String>(), settings.state.value.notifiedNudges)

            canPost = true
            poll.run()

            assertEquals(listOf("a"), posted)
        }
}
