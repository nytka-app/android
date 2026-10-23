package io.github.nytka_app.quiet

import io.github.nytka_app.FakeSettings
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.AudioLevelClient
import io.github.nytka_app.core.api.DayLevel
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.ServerInfo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class QuietDayCheckTest {
    private val zone = ZoneId.of("Europe/Kyiv")
    private var clock = at("2026-10-24T21:00:00+03:00")
    private val settings = FakeSettings()
    private var features = listOf(ServerInfo.FEATURE_AUDIO_LEVEL)
    private var canPost = true
    private val posted = mutableListOf<DayLevel>()
    private val asked = mutableListOf<LocalDate>()
    private var answer: ApiResult<DayLevel> = ApiResult.Ok(level(quiet = true))

    private fun at(time: String) = Clock.fixed(Instant.parse(time), zone)

    private fun level(quiet: Boolean) = DayLevel("2026-10-24", -44.0, -28.5, 0.4, quiet)

    private val client =
        object : AudioLevelClient {
            override suspend fun level(day: LocalDate): ApiResult<DayLevel> {
                asked += day
                return answer
            }
        }

    private fun check() =
        QuietDayCheck({ ApiResult.Ok(ServerInfo("0.34.0", 1, features = features)) }, client, settings, {
            if (canPost) posted += it
            canPost
        }, clock)

    @Test
    fun `a quiet day posts once, for the phone's own day`() =
        runTest {
            check().run()
            check().run()

            assertEquals(listOf(LocalDate.of(2026, 10, 24)), asked)
            assertEquals(1, posted.size)
            assertEquals("2026-10-24", settings.state.value.quietNoticeDay)
        }

    @Test
    fun `nothing is asked before 20 00 or on a server without audio-level`() =
        runTest {
            clock = at("2026-10-24T19:59:00+03:00")
            check().run()
            clock = at("2026-10-24T21:00:00+03:00")
            features = emptyList()
            check().run()

            assertTrue(asked.isEmpty())
            assertTrue(posted.isEmpty())
        }

    @Test
    fun `a usual day, a day without speech and a refused post leave it for the next run`() =
        runTest {
            answer = ApiResult.Ok(level(quiet = false))
            check().run()
            answer = ApiResult.Failure(FailureKind.NotFound, "no speech")
            check().run()
            answer = ApiResult.Ok(level(quiet = true))
            canPost = false
            check().run()

            assertTrue(posted.isEmpty())
            assertNull(settings.state.value.quietNoticeDay)
            assertEquals(3, asked.size)
        }

    @Test
    fun `the notice says dB under the usual when that is the reason, else the quiet share`() {
        assertEquals(16, QuietNotifications.decibelsUnder(level(quiet = true)))
        assertNull(QuietNotifications.decibelsUnder(DayLevel("2026-10-24", -33.0, -30.0, 0.3, true)))
    }
}
