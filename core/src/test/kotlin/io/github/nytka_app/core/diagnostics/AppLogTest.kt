package io.github.nytka_app.core.diagnostics

import android.util.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class AppLogTest {
    private class FakeSink : LogEventSink {
        val events = mutableListOf<DiagnosticLogEvent>()
        var failing = false

        override suspend fun addLog(event: DiagnosticLogEvent) {
            if (failing) error("disk on fire")
            events += event
        }
    }

    private class Recorded : EventLog {
        val lines = mutableListOf<Triple<Int, String, String>>()

        override fun log(
            priority: Int,
            tag: String,
            message: String,
        ) {
            lines += Triple(priority, tag, message)
        }
    }

    private val sink = FakeSink()
    private val logcat = Recorded()
    private var nowMs = 1_800_000_000_000

    private fun TestScope.appLog(
        capacity: Int = 16,
        hourlyLimit: Int = 2000,
    ) = AppLog(
        sink,
        backgroundScope,
        appVersion = "0.2.0",
        device = "Pixel 8 / Android 16",
        io = StandardTestDispatcher(testScheduler),
        now = { nowMs },
        newId = { UUID(it, 0) },
        logcat = logcat,
        capacity = capacity,
        hourlyLimit = hourlyLimit,
    )

    @Test
    fun `a line reaches logcat unchanged and the table with its level`() =
        runTest {
            val log = appLog()
            val run = log.start()

            log.i("OmiPendant", "setUp: connected")
            log.w("OmiPendant", "watchdog: no audio")
            runCurrent()

            assertEquals(listOf(Triple(Log.INFO, "OmiPendant", "setUp: connected")), logcat.lines.take(1))
            assertEquals(listOf("I", "W"), sink.events.map { it.level })
            assertEquals(listOf("OmiPendant", "OmiPendant"), sink.events.map { it.tag })
            assertEquals("2027-01-15T08:00:00Z", sink.events.first().at)
        }

    @Test
    fun `lines before start and after stop go to logcat only`() =
        runTest {
            val log = appLog()
            log.i("A", "before")
            val run = log.start()
            log.i("A", "during")
            runCurrent()
            log.stop(run)
            log.i("A", "after")
            runCurrent()

            assertEquals(3, logcat.lines.size)
            assertEquals(listOf("during"), sink.events.map { it.message })
        }

    @Test
    fun `stop writes the lines still queued`() =
        runTest {
            val log = appLog()
            val run = log.start()
            log.i("A", "last words")

            log.stop(run)

            assertEquals(listOf("last words"), sink.events.map { it.message })
        }

    @Test
    fun `a full queue drops the oldest waiting lines`() =
        runTest {
            val log = appLog(capacity = 2)
            val run = log.start()

            repeat(5) { log.i("A", "line $it") }
            runCurrent()

            assertEquals(listOf("line 3", "dropped 3 log lines", "line 4"), sink.events.map { it.message })
            assertEquals("W", sink.events[1].level)
            assertEquals("AppLog", sink.events[1].tag)
        }

    @Test
    fun `a stop that completes after a newer start does not end the newer run`() =
        runTest {
            val log = appLog()
            val old = log.start()
            log.i("A", "old run")
            val new = log.start() // the replacing service began first; the old one's stop comes late

            log.stop(old)
            log.i("A", "new run")
            runCurrent()

            assertEquals(listOf("old run", "new run"), sink.events.map { it.message })
            log.stop(new)
            log.i("A", "after")
            runCurrent()
            assertEquals(2, sink.events.size)
        }

    @Test
    fun `a failing sink throws nothing at the caller or the writer`() =
        runTest {
            val log = appLog()
            val run = log.start()
            sink.failing = true

            log.e("A", "one")
            runCurrent()
            sink.failing = false
            log.e("A", "two")
            runCurrent()

            assertEquals(listOf("two"), sink.events.map { it.message })
        }

    @Test
    fun `more than the hourly limit is dropped after one line saying so, and the next hour starts again`() =
        runTest {
            val log = appLog(hourlyLimit = 3)
            val run = log.start()

            repeat(6) { log.i("A", "line $it") }
            runCurrent()

            assertEquals(listOf("line 0", "line 1", "line 2"), sink.events.take(3).map { it.message })
            assertEquals(4, sink.events.size)
            assertEquals("W", sink.events.last().level)
            assertEquals(
                "log rate limit hit",
                sink.events
                    .last()
                    .message
                    .substringBefore(":"),
            )

            nowMs += 60 * 60 * 1000L
            log.i("A", "next hour")
            runCurrent()

            assertEquals("next hour", sink.events.last().message)
        }

    @Test
    fun `the wire JSON has only the listed fields`() {
        val event =
            DiagnosticLogEvent(
                id = "00000000-0000-7000-8000-000000000001",
                at = "2027-01-15T08:00:00Z",
                kind = DiagnosticLogEvent.KIND,
                level = "I",
                tag = "OmiPendant",
                message = "open: connectGatt …AB:CD",
                appVersion = "0.2.0",
                device = "Pixel 8 / Android 16",
            )

        val fields = Json.parseToJsonElement(Json.encodeToString(DiagnosticLogEvent.serializer(), event)).jsonObject

        assertEquals(setOf("id", "at", "kind", "level", "tag", "message", "appVersion", "device"), fields.keys)
        assertEquals("log", fields.getValue("kind").toString().trim('"'))
    }
}
