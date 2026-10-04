package io.github.nytka_app.firmware

import io.github.nytka_app.FakeSettings
import io.github.nytka_app.core.settings.Settings
import io.github.nytka_app.pendant.FirmwareStream
import io.github.nytka_app.pendant.FirmwareVersion
import io.github.nytka_app.pendant.PendantInfo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class FirmwareUpdateCheckerTest {
    private class MovableClock(
        var now: Instant = Instant.parse("2026-10-04T10:00:00Z"),
    ) : Clock() {
        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId?) = this

        override fun instant() = now

        fun advance(by: Duration) {
            now = now.plus(by)
        }
    }

    private class FakeReleases(
        var answer: ReleaseAnswer = ReleaseAnswer.Latest(FirmwareVersion(3, 0, 21)),
    ) : FirmwareReleases {
        var calls = 0

        override suspend fun latest(stream: FirmwareStream): ReleaseAnswer {
            calls++
            return answer
        }
    }

    private val settings = FakeSettings()
    private val releases = FakeReleases()
    private val clock = MovableClock()
    private val checker = FirmwareUpdateChecker(settings, releases, clock)
    private val omi = PendantInfo("Omi", model = "Omi CV 1", firmware = "3.0.20")

    @Test
    fun `a newer release gives a notice with both versions`() =
        runTest {
            val notice = checker.notice(omi)

            assertEquals(FirmwareNotice(FirmwareVersion(3, 0, 20), FirmwareVersion(3, 0, 21)), notice)
            assertEquals("Omi_CV1_v3.0.21", settings.state.value.firmwareLatest)
            assertEquals(clock.millis(), settings.state.value.firmwareCheckedAt)
        }

    @Test
    fun `the same or a newer pendant firmware gives no notice`() =
        runTest {
            assertNull(checker.notice(omi.copy(firmware = "3.0.21")))
            assertNull(checker.notice(omi.copy(firmware = "3.1.0")))
        }

    @Test
    fun `asks GitHub once a day, however often the screen asks`() =
        runTest {
            checker.notice(omi)
            clock.advance(Duration.ofHours(23))
            val again = checker.notice(omi)

            assertEquals(1, releases.calls)
            assertEquals(FirmwareVersion(3, 0, 21), again?.latest) // from the saved tag, with no request

            clock.advance(Duration.ofHours(1))
            checker.notice(omi)
            assertEquals(2, releases.calls)
        }

    @Test
    fun `an answer without a release still uses up the day and keeps the saved tag`() =
        runTest {
            settings.state.value =
                Settings(
                    firmwareCheckedAt = clock.millis() - Duration.ofDays(2).toMillis(),
                    firmwareLatest = "Omi_CV1_v3.0.21",
                )
            releases.answer = ReleaseAnswer.NoRelease

            val notice = checker.notice(omi)
            checker.notice(omi)

            assertEquals(1, releases.calls)
            assertEquals(clock.millis(), settings.state.value.firmwareCheckedAt)
            assertEquals(FirmwareVersion(3, 0, 21), notice?.latest)
        }

    @Test
    fun `offline does not use up the day, but is not retried for an hour`() =
        runTest {
            releases.answer = ReleaseAnswer.Unreachable

            assertNull(checker.notice(omi))
            assertNull(checker.notice(omi))
            assertEquals(1, releases.calls)
            assertEquals(0L, settings.state.value.firmwareCheckedAt)

            clock.advance(Duration.ofMinutes(61))
            releases.answer = ReleaseAnswer.Latest(FirmwareVersion(3, 0, 21))
            assertEquals(FirmwareVersion(3, 0, 21), checker.notice(omi)?.latest)
            assertEquals(2, releases.calls)
        }

    @Test
    fun `with the setting off nothing is asked and nothing is shown`() =
        runTest {
            settings.state.value = Settings(firmwareCheck = false, firmwareLatest = "Omi_CV1_v3.0.21")

            assertNull(checker.notice(omi))
            assertEquals(0, releases.calls)
        }

    @Test
    fun `a pendant that maps to no stream is never checked`() =
        runTest {
            assertNull(checker.notice(PendantInfo("Omi", model = "Omi DevKit 2", firmware = "2.0.9")))
            assertNull(checker.notice(PendantInfo("Fake pendant", model = "Fake", firmware = "fake")))
            assertNull(checker.notice(PendantInfo("Omi", model = null, firmware = null)))
            assertEquals(0, releases.calls)
        }

    @Test
    fun `a saved tag of another stream shows nothing`() =
        runTest {
            settings.state.value =
                Settings(firmwareCheckedAt = clock.millis(), firmwareLatest = "Omi_DK2_v2.0.10")

            assertNull(checker.notice(omi))
        }

    @Test
    fun `due after a day, when never checked, or when the clock went back`() {
        val day = Duration.ofDays(1).toMillis()
        assertTrue(FirmwareUpdateChecker.isDue(0, 1_000))
        assertFalse(FirmwareUpdateChecker.isDue(1_000, 1_000 + day - 1))
        assertTrue(FirmwareUpdateChecker.isDue(1_000, 1_000 + day))
        assertTrue(FirmwareUpdateChecker.isDue(5_000, 1_000))
    }
}
