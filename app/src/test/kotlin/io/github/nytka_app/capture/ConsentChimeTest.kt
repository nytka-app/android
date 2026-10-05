package io.github.nytka_app.capture

import io.github.nytka_app.core.settings.Settings
import io.github.nytka_app.pendant.PendantConnection
import io.github.nytka_app.pendant.PendantInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConsentChimeTest {
    private val minute = 60_000L
    private val recording = CaptureStatus(running = true, connection = PendantConnection.Connected(PendantInfo("Omi")))
    private val status = MutableStateFlow(recording)
    private val enrolling = MutableStateFlow(false)
    private val settings = MutableStateFlow(Settings(consentChime = true, consentChimeMinutes = 5))
    private var plays = 0

    private fun TestScope.chime() =
        ConsentChime(status, enrolling, settings, { plays++ }, { currentTime }, backgroundScope).start()

    private suspend fun TestScope.after(minutes: Long) {
        advanceTimeBy(minutes * minute)
        runCurrent()
    }

    @Test
    fun `switched off, it never chimes`() =
        runTest {
            settings.value = Settings(consentChime = false)
            chime()
            after(120)
            assertEquals(0, plays)
        }

    @Test
    fun `recording chimes at once and then every interval`() =
        runTest {
            chime()
            runCurrent()
            assertEquals(1, plays)
            after(4)
            assertEquals(1, plays)
            after(1)
            assertEquals(2, plays)
            after(5)
            assertEquals(3, plays)
        }

    @Test
    fun `muted, it is silent and chimes again when recording resumes`() =
        runTest {
            status.value = recording.copy(muted = true)
            chime()
            after(30)
            assertEquals(0, plays)
            status.value = recording
            runCurrent()
            assertEquals(1, plays)
        }

    @Test
    fun `disconnected or not running, it is silent`() =
        runTest {
            status.value = recording.copy(connection = PendantConnection.Disconnected)
            chime()
            after(30)
            assertEquals(0, plays)
            status.value = recording.copy(running = false)
            after(30)
            assertEquals(0, plays)
        }

    @Test
    fun `during enrollment it is silent, and counts from the last chime afterwards`() =
        runTest {
            chime()
            runCurrent()
            enrolling.value = true
            after(30)
            assertEquals(1, plays)
            enrolling.value = false
            runCurrent()
            assertEquals(2, plays)
        }

    @Test
    fun `a reconnect within the interval does not chime`() =
        runTest {
            chime()
            runCurrent()
            after(1)
            status.value = recording.copy(connection = PendantConnection.Disconnected)
            after(1)
            status.value = recording
            runCurrent()
            assertEquals(1, plays)
            after(3)
            assertEquals(2, plays)
        }

    @Test
    fun `a longer interval takes effect from the last chime`() =
        runTest {
            chime()
            runCurrent()
            after(2)
            settings.value = settings.value.copy(consentChimeMinutes = 10)
            after(7)
            assertEquals(1, plays)
            after(1)
            assertEquals(2, plays)
        }

    @Test
    fun `a shorter interval chimes at once when it is already due`() =
        runTest {
            settings.value = settings.value.copy(consentChimeMinutes = 30)
            chime()
            runCurrent()
            after(10)
            settings.value = settings.value.copy(consentChimeMinutes = 5)
            runCurrent()
            assertEquals(2, plays)
        }
}
