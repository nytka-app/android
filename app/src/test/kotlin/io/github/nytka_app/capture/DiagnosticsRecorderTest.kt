package io.github.nytka_app.capture

import io.github.nytka_app.core.diagnostics.DiagnosticSample
import io.github.nytka_app.core.diagnostics.DiagnosticsSink
import io.github.nytka_app.core.queue.QueueUsage
import io.github.nytka_app.core.upload.UploadState
import io.github.nytka_app.pendant.LinkStats
import io.github.nytka_app.pendant.PendantConnection
import io.github.nytka_app.pendant.PendantInfo
import io.github.nytka_app.pendant.RingInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class DiagnosticsRecorderTest {
    private class FakeSink : DiagnosticsSink {
        val samples = mutableListOf<DiagnosticSample>()
        var prunes = 0

        override suspend fun add(sample: DiagnosticSample) {
            samples += sample
        }

        override suspend fun prune() {
            prunes++
        }
    }

    private val status = MutableStateFlow(CaptureStatus())
    private val usage = MutableStateFlow(QueueUsage())
    private val upload = MutableStateFlow(UploadState())
    private val sink = FakeSink()
    private val sync = MutableStateFlow(StorageSyncStatus())

    /** The clock is the test scheduler's, from a Monday-ish instant so `at` reads like the contract. */
    private fun TestScope.recorder() =
        DiagnosticsRecorder(
            status,
            usage,
            upload,
            sink,
            backgroundScope,
            appVersion = "0.2.0",
            device = "Pixel 8 / Android 16",
            now = { START_MS + testScheduler.currentTime },
            newId = { UUID(0, it) },
            sync = sync,
        )

    @Test
    fun `takes a sample every 10 seconds on the phone's clock`() =
        runTest {
            recorder().start()

            runCurrent()
            advanceTimeBy(25_000)

            assertEquals(
                listOf("2026-09-29T09:15:00Z", "2026-09-29T09:15:10Z", "2026-09-29T09:15:20Z"),
                sink.samples.map { it.at },
            )
            assertEquals(
                3,
                sink.samples
                    .map { it.id }
                    .toSet()
                    .size,
            )
        }

    @Test
    fun `a sample carries the sync counters as words and numbers only`() =
        runTest {
            usage.value = QueueUsage(parkedChunks = 2)
            sync.value =
                StorageSyncStatus(
                    state = SyncState.Retrying(status = 9, retryInMs = 30_000),
                    ring =
                        RingInfo(
                            readSeq = 10,
                            writeSeq = 500,
                            capacityPackets = 1_000,
                            droppedPackets = 3,
                            packetBytes = 444,
                        ),
                    lastDoneStatus = 9,
                    syncedPackets = 200,
                    lostPackets = 22,
                    kbPerSecond = 31.5,
                    liveLoss = 0.02,
                    mutedFrames = 40,
                    badStampRecords = 1,
                    skewS = 6,
                    segments = 2,
                )
            recorder().start()

            runCurrent()

            val sample = sink.samples.single()
            assertEquals("Retrying", sample.syncState)
            assertEquals(
                listOf(10L, 500L, 1_000L, 3L),
                listOf(sample.ringReadSeq, sample.ringWriteSeq, sample.ringCapacity, sample.ringDropped),
            )
            assertEquals(9, sample.lastDoneStatus)
            assertEquals(
                listOf(200L, 22L, 40L, 1L, 2L),
                listOf(
                    sample.syncedPackets,
                    sample.lostPackets,
                    sample.mutedFrames,
                    sample.badStampRecords,
                    sample.segments,
                ),
            )
            assertEquals(31.5, sample.syncKbPerSecond)
            assertEquals(0.02, sample.syncLiveLoss)
            assertEquals(6L, sample.clockSkewS)
            assertEquals(2, sample.parkedChunks)
        }

    @Test
    fun `a sample carries the link, the queue and the uploader`() =
        runTest {
            status.value =
                CaptureStatus(
                    running = true,
                    connection = PendantConnection.Connected(PendantInfo("Omi")),
                    battery = 82,
                    stats =
                        LinkStats(
                            notifications = 123,
                            lostNotifications = 4,
                            droppedFrames = 3,
                            frames = 100,
                            rssi = -71,
                            lastDisconnectStatus = 147,
                        ),
                    session = "session-1",
                    framesQueued = 98,
                )
            usage.value = QueueUsage(bytes = 4096, chunks = 2, frames = 7)
            upload.value =
                UploadState(
                    lastUploadAtMs = START_MS - 5_000,
                    lastResult = "202: stored through 41",
                    failures = 2,
                    paused = "The server refused the token.",
                )
            recorder().start()

            runCurrent()

            assertEquals(
                DiagnosticSample(
                    id = UUID(0, START_MS).toString(),
                    at = "2026-09-29T09:15:00Z",
                    session = "session-1",
                    syncState = "Idle",
                    connection = "connected",
                    battery = 82,
                    notifications = 123,
                    lostNotifications = 4,
                    droppedFrames = 3,
                    frames = 100,
                    framesQueued = 98,
                    queueBytes = 4096,
                    queueChunks = 2,
                    queueFrames = 7,
                    uploadFailures = 2,
                    uploadPaused = "Unauthorized",
                    lastUploadAt = "2026-09-29T09:14:55Z",
                    lastResult = "202",
                    appVersion = "0.2.0",
                    device = "Pixel 8 / Android 16",
                    rssi = -71,
                    lastDisconnectStatus = 147,
                ),
                sink.samples.single(),
            )
        }

    @Test
    fun `an idle service has no session and a muted pendant says muted`() =
        runTest {
            val recorder = recorder()
            recorder.start()
            runCurrent()
            status.value =
                CaptureStatus(
                    running = true,
                    connection = PendantConnection.Connected(PendantInfo("Omi")),
                    muted = true,
                )
            advanceTimeBy(10_001)

            assertNull(sink.samples[0].session)
            assertEquals(listOf("disconnected", "muted"), sink.samples.map { it.connection })
        }

    @Test
    fun `stopping takes a last sample and ends the sampling`() =
        runTest {
            val recorder = recorder()
            recorder.start()
            runCurrent()

            recorder.stop()
            advanceTimeBy(60_000)

            assertEquals(2, sink.samples.size)
        }

    @Test
    fun `prunes when it starts and every hour after`() =
        runTest {
            recorder().start()
            runCurrent()
            assertEquals(1, sink.prunes)

            advanceTimeBy(60 * 60 * 1000L + 1)
            assertEquals(2, sink.prunes)
        }

    @Test
    fun `no server address, host, token or transcript can reach a sample`() =
        runTest {
            status.value =
                CaptureStatus(running = true, connection = PendantConnection.Refused("Pendant not supported"))
            upload.value =
                UploadState(
                    lastResult = "Failed to connect to nytka.example/203.0.113.7:443",
                    paused = "Enter a full address that starts with https://",
                )
            recorder().start()
            runCurrent()
            upload.value = UploadState(lastResult = "Unexpected answer: {\"text\":\"hello, my secret is 42\"}")
            advanceTimeBy(10_001)
            upload.value = UploadState(lastResult = "The server answered 503.")
            advanceTimeBy(10_001)

            val json = sink.samples.joinToString { Json.encodeToString(DiagnosticSample.serializer(), it) }

            assertEquals(listOf("Retry", "Retry", "503"), sink.samples.map { it.lastResult })
            assertEquals("NotConfigured", sink.samples[0].uploadPaused)
            listOf(
                "nytka",
                "203.0.113",
                "https",
                "secret",
                "hello",
                "token",
            ).forEach { assertFalse(it, json.contains(it)) }
        }

    @Test
    fun `result words keep the status and drop the rest`() {
        assertEquals("202", DiagnosticsRecorder.resultWord("202: stored through 9"))
        assertEquals("200", DiagnosticsRecorder.resultWord("200: already stored"))
        assertEquals("409", DiagnosticsRecorder.resultWord("409: dropped the chunk from frame 3"))
        assertEquals("Unauthorized", DiagnosticsRecorder.resultWord("The server refused the token."))
        assertEquals(
            "Upload failed: SQLiteFullException",
            DiagnosticsRecorder.resultWord("Upload failed: SQLiteFullException"),
        )
        assertEquals("Retry", DiagnosticsRecorder.resultWord("timeout"))
    }

    private companion object {
        /** 2026-09-29T09:15:00Z */
        const val START_MS = 1_790_673_300_000L
    }
}
