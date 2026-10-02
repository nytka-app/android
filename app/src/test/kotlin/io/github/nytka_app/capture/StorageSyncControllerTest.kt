package io.github.nytka_app.capture

import io.github.nytka_app.FakeEventLog
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.queue.FrameSink
import io.github.nytka_app.core.queue.QueueUsage
import io.github.nytka_app.core.ring.ClockPair
import io.github.nytka_app.core.ring.MuteChange
import io.github.nytka_app.core.ring.RingPosition
import io.github.nytka_app.core.ring.TimedFrame
import io.github.nytka_app.pendant.FakePendant
import io.github.nytka_app.pendant.FakeRing
import io.github.nytka_app.pendant.LinkStats
import io.github.nytka_app.pendant.Pendant
import io.github.nytka_app.pendant.PendantStorage
import io.github.nytka_app.pendant.RingEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.util.UUID

class StorageSyncControllerTest {
    /** The queue as the controller sees it: frames wait in [queued] until a test says the server took them. */
    private class FakeSink : FrameSink {
        var stored: RingPosition? = null
        val queued = mutableListOf<TimedFrame>()
        val committedFrames = mutableListOf<TimedFrame>()
        var commits = 0
        var seals = 0
        var mutes = listOf<MuteChange>()
        var failCommitOnce: Exception? = null
        val advanced = mutableListOf<Long>()
        private val acked = MutableStateFlow<Long?>(null)

        override val ackedThrough: Flow<Long> = acked.filterNotNull()

        override suspend fun add(
            session: UUID,
            seq: Long,
            capturedAtMs: Long,
            payload: ByteArray,
        ) = Unit

        override suspend fun seal(includePartialStored: Boolean): Int = 1.also { seals++ }

        override suspend fun position(): RingPosition? = stored

        override suspend fun commit(
            frames: List<TimedFrame>,
            position: RingPosition,
        ) {
            failCommitOnce?.let {
                failCommitOnce = null
                throw it
            }
            commits++
            queued += frames
            committedFrames += frames
            stored = position
            refresh()
        }

        override suspend fun markAdvanced(seq: Long) {
            advanced += seq
            stored = stored?.copy(advanced = seq)
        }

        override suspend fun muteChanges(): List<MuteChange> = mutes

        /** The server took every stored frame from a ring record below [seq]. */
        fun accept(seq: Long) {
            queued.removeAll { it.ringSeq < seq }
            refresh()
        }

        private fun refresh() {
            acked.value =
                stored?.let { minOf(queued.minOfOrNull { f -> f.ringSeq } ?: it.committedNext, it.committedNext) }
        }
    }

    private class FakeInfo(
        var features: List<String>? = listOf("offline-sync"),
    ) : InfoClient {
        var calls = 0

        override suspend fun info(): ApiResult<ServerInfo> {
            calls++
            return features?.let { ApiResult.Ok(ServerInfo("0.4.0", 1, features = it)) }
                ?: ApiResult.Failure(FailureKind.Network, "down")
        }
    }

    /** A pendant whose link statistics and read windows a test steers. */
    private class SteeredPendant(
        private val inner: FakePendant,
    ) : Pendant by inner {
        val steeredStats = MutableStateFlow(LinkStats())
        var loseOnDone = 0L
        var reads = mutableListOf<Long>()

        override val stats: StateFlow<LinkStats> = steeredStats

        override val storage: PendantStorage =
            object : PendantStorage by inner.storage {
                override fun read(
                    fromSeq: Long,
                    count: Int,
                ): Flow<RingEvent> =
                    inner.storage.read(fromSeq, count).onEach {
                        if (it is RingEvent.Begin) reads += it.startSeq
                        if (it is RingEvent.Done) {
                            steeredStats.value =
                                steeredStats.value.let { s ->
                                    s.copy(
                                        notifications = s.notifications + 100 - loseOnDone,
                                        lostNotifications = s.lostNotifications + loseOnDone,
                                    )
                                }
                        }
                    }
            }
    }

    private class Rig(
        val controller: StorageSyncController,
        val pendant: FakePendant,
        val steered: SteeredPendant,
        val sink: FakeSink,
        val info: FakeInfo,
        val usage: MutableStateFlow<QueueUsage>,
        val log: FakeEventLog,
    ) {
        val ring get() = pendant.ring
        val opcodes get() = ring.commands.map { it[0].toInt() and 0xFF }
        val reads get() = ring.commands.filter { it[0].toInt() == READ }.map { ByteBuffer.wrap(it, 1, 8).long }
        val advances get() = ring.commands.filter { it[0].toInt() == ADVANCE }.map { ByteBuffer.wrap(it, 1, 8).long }
        val status get() = controller.status.value
    }

    private fun TestScope.rig(
        firmware: String = "3.0.20",
        tuning: SyncTuning = SyncTuning(windowPackets = 50, commitRecords = 20),
        server: List<String>? = listOf("offline-sync"),
        start: Boolean = true,
    ): Rig {
        val now = { BASE_MS + testScheduler.currentTime }
        val pendant = FakePendant(listOf(byteArrayOf(1)), backgroundScope, now)
        pendant.ring.firmware = firmware
        pendant.ring.autoAdvance = firmware == "3.0.21"
        val steered = SteeredPendant(pendant)
        val sink = FakeSink()
        val info = FakeInfo(server)
        val usage = MutableStateFlow(QueueUsage(capBytes = 100))
        val log = FakeEventLog()
        val controller =
            StorageSyncController(
                steered,
                sink,
                usage,
                info,
                backgroundScope,
                "fake",
                now = now,
                tuning = tuning,
                log = log,
            )
        if (start) controller.start()
        return Rig(controller, pendant, steered, sink, info, usage, log)
    }

    /** [records] records of four frames each, stamped every 12 records like the pendant's 80 ms records. */
    private fun FakeRing.fill(
        records: Int,
        firstStampS: Long = STAMP_S,
    ) {
        repeat(records) { i -> store(firstStampS + i / 12, List(4) { ByteArray(97) { (i and 0x7F).toByte() } }) }
    }

    /** `advanceUntilIdle` ignores background work, which is where the controller and the fake ring run. */
    private fun TestScope.settle() = runCurrent()

    private fun Rig.connect() {
        pendant.connect("fake")
    }

    @Test
    fun `a pendant below firmware 3_0_20 gets no storage write`() =
        runTest {
            val rig = rig(firmware = "3.0.19")
            rig.ring.fill(30)

            rig.connect()
            settle()
            assertTrue(rig.ring.commands.isEmpty())
            assertFalse(rig.ring.subscribed)
            assertTrue(rig.status.state is SyncState.Unsupported)
            assertEquals(0, rig.sink.commits)
        }

    @Test
    fun `a server without the offline-sync feature is not synced against and the sync begins once it is upgraded`() =
        runTest {
            val rig = rig(server = emptyList())
            rig.ring.fill(30)

            rig.connect()
            advanceTimeBy(10_000)

            assertTrue(rig.ring.commands.isEmpty())
            assertEquals(SyncState.ServerOutdated(30_000), rig.status.state)

            rig.info.features = listOf("other", "offline-sync")
            advanceTimeBy(30_001)
            settle()

            assertEquals(30L, rig.sink.stored?.committedNext)
        }

    @Test
    fun `a server that does not answer keeps the audio on the pendant`() =
        runTest {
            val rig = rig(server = null)
            rig.ring.fill(30)

            rig.connect()
            advanceTimeBy(60_000)

            assertTrue(rig.ring.commands.isEmpty())
            assertTrue(rig.status.state is SyncState.ServerUnavailable)
        }

    @Test
    fun `reads the ring in windows and commits frames with the position`() =
        runTest {
            val rig = rig()
            rig.ring.fill(120)

            rig.connect()
            settle()

            assertEquals(listOf(0L, 50L, 100L), rig.reads)
            assertEquals(120L, rig.sink.stored?.committedNext)
            assertEquals(480, rig.sink.committedFrames.size)
            // One session per run, numbered without holes, in ring order.
            assertEquals(
                1,
                rig.sink.committedFrames
                    .map { it.session }
                    .distinct()
                    .size,
            )
            assertEquals((0L until 480L).toList(), rig.sink.committedFrames.map { it.seq })
            assertEquals(
                rig.sink.committedFrames
                    .map { it.ringSeq }
                    .sorted(),
                rig.sink.committedFrames.map { it.ringSeq },
            )
            assertEquals(SyncState.Idle, rig.status.state)
            assertEquals(0L, rig.status.storedPackets)
            assertTrue(rig.sink.seals >= 3)
            assertTrue(rig.log.messages.any { it.startsWith("sync starts: 120 packets") })
            assertTrue(rig.log.messages.any { it.startsWith("window from 50:") })
        }

    @Test
    fun `commits after 20 records, not once per window`() =
        runTest {
            val rig = rig(tuning = SyncTuning(windowPackets = 100, commitRecords = 20))
            rig.ring.fill(100)

            rig.connect()
            settle()

            assertTrue("commits were ${rig.sink.commits}", rig.sink.commits >= 100 / 20)
            assertEquals(100L, rig.sink.stored?.committedNext)
        }

    @Test
    fun `advance goes only as far as the server has accepted`() =
        runTest {
            val rig = rig()
            rig.ring.fill(120)
            rig.connect()
            settle()

            // Everything is copied into the queue, nothing is uploaded yet.
            assertTrue(rig.advances.isEmpty())
            assertEquals(0L, rig.ring.readSeq)

            rig.sink.accept(60)
            settle()
            assertEquals(listOf(60L), rig.advances)
            assertEquals(60L, rig.ring.readSeq)
            assertEquals(listOf(60L), rig.sink.advanced)

            // The next ADVANCE waits for the 10 s gap and never passes what was accepted.
            rig.sink.accept(90)
            advanceTimeBy(9_000)
            assertEquals(listOf(60L), rig.advances)
            advanceTimeBy(1_001)
            assertEquals(listOf(60L, 90L), rig.advances)
            assertEquals(90L, rig.ring.readSeq)
        }

    @Test
    fun `with the server unreachable after the sync no advance is sent`() =
        runTest {
            val rig = rig()
            rig.ring.fill(60)
            rig.connect()
            settle()

            advanceTimeBy(600_000)

            assertEquals(60L, rig.sink.stored?.committedNext)
            assertTrue(rig.advances.isEmpty())
            assertEquals(0L, rig.ring.readSeq)
        }

    @Test
    fun `records covered by a mute are consumed and freed but never queued`() =
        runTest {
            val rig = rig()
            rig.ring.fill(120) // stamps STAMP_S to STAMP_S + 9
            // Muted from 4 s into the stored stretch until 6 s in (frames within 2 s of it go too).
            rig.sink.mutes =
                listOf(MuteChange((STAMP_S + 4) * 1000, true), MuteChange((STAMP_S + 6) * 1000, false))

            rig.connect()
            settle()

            assertEquals(120L, rig.sink.stored?.committedNext)
            assertTrue(rig.sink.committedFrames.size < 480)
            assertTrue(rig.status.mutedFrames > 0)
            assertEquals(
                480L - rig.status.mutedFrames,
                rig.sink.committedFrames.size
                    .toLong(),
            )
            assertTrue(
                rig.sink.committedFrames.none {
                    it.capturedAtMs in
                        ((STAMP_S + 3) * 1000 + 1)..<(STAMP_S + 7) * 1000
                },
            )
            // Numbered without holes, since a hole makes the server wait ten minutes.

            val expected = (0L until rig.sink.committedFrames.size).toList()
            val actual = rig.sink.committedFrames.map { it.seq }
            assertEquals(expected, actual)

            // Nothing of the muted part is in the queue, so once the rest is accepted the whole ring is freed.
            rig.sink.accept(120)
            settle()
            assertEquals(120L, rig.advances.last())
        }

    @Test
    fun `a muted absence leaves the pendant empty`() =
        runTest {
            val rig = rig()
            rig.ring.fill(60)
            rig.sink.mutes = listOf(MuteChange((STAMP_S - 60) * 1000, true)) // muted before and still open

            rig.connect()
            settle()

            assertTrue(rig.sink.committedFrames.isEmpty())
            assertEquals(60L, rig.sink.stored?.committedNext)
            // ackedThrough is committedNext with an empty queue, so it is freed without any upload.
            advanceTimeBy(10_001)
            assertEquals(60L, rig.advances.last())
            assertEquals(0L, rig.ring.unread)
        }

    @Test
    fun `a first backlog over the limit asks once and imports on yes`() =
        runTest {
            val rig = rig(tuning = SyncTuning(windowPackets = 50, backlogPackets = 40))
            rig.ring.fill(90)

            rig.connect()
            settle()

            assertEquals(SyncState.AwaitingBacklog(90), rig.status.state)
            assertTrue(rig.reads.isEmpty())
            assertEquals(0L, rig.sink.stored?.committedNext) // the position exists, so a restart imports

            rig.controller.importBacklog()
            settle()

            assertEquals(90L, rig.sink.stored?.committedNext)
            assertEquals(360, rig.sink.committedFrames.size)

            // The next connection does not ask again.
            rig.pendant.dropLink()
            settle()
            rig.ring.fill(60, firstStampS = STAMP_S + 100)
            rig.connect()
            settle()
            assertEquals(150L, rig.sink.stored?.committedNext)
        }

    @Test
    fun `discarding a first backlog frees the ring through the normal advance`() =
        runTest {
            val rig = rig(tuning = SyncTuning(windowPackets = 50, backlogPackets = 40))
            rig.ring.fill(90)
            rig.connect()
            settle()

            rig.controller.discardBacklog()
            settle()

            assertTrue(rig.reads.isEmpty())
            assertTrue(rig.sink.committedFrames.isEmpty())
            assertEquals(90L, rig.sink.stored?.committedNext)
            assertEquals(listOf(90L), rig.advances)
            assertEquals(SyncState.Idle, rig.status.state)
        }

    @Test
    fun `a backlog under the limit imports without asking`() =
        runTest {
            val rig = rig(tuning = SyncTuning(windowPackets = 50, backlogPackets = 100))
            rig.ring.fill(90)

            rig.connect()
            settle()

            assertEquals(90L, rig.sink.stored?.committedNext)
            assertTrue(rig.log.messages.none { it.contains("asking") })
        }

    @Test
    fun `waits while the queue is over half full and resumes below 40 percent`() =
        runTest {
            val rig = rig()
            rig.ring.fill(120)
            rig.usage.value = QueueUsage(bytes = 60, capBytes = 100)

            rig.connect()
            settle()

            assertTrue(rig.reads.isEmpty())
            assertTrue(rig.status.state is SyncState.WaitingForUploads)

            rig.usage.value = QueueUsage(bytes = 45, capBytes = 100) // under the cap's half, but not under 40%
            settle()
            assertTrue(rig.reads.isEmpty())

            rig.usage.value = QueueUsage(bytes = 39, capBytes = 100)
            settle()
            assertEquals(120L, rig.sink.stored?.committedNext)
        }

    @Test
    fun `a transfer that stays quiet is read again at once, then retried after 30 seconds`() =
        runTest {
            val rig = rig()
            rig.ring.fill(120)
            rig.ring.stallAfterBytes = 100L // less than one record: nothing to commit

            rig.connect()
            advanceTimeBy(45_000) // four quiet windows of 10 s

            assertEquals(SyncState.Retrying(-1, 30_000), rig.status.state)
            assertEquals(4, rig.reads.size)
            assertEquals(0L, rig.sink.stored?.committedNext)

            rig.ring.stallAfterBytes = null
            advanceTimeBy(30_001)
            settle()

            assertEquals(120L, rig.sink.stored?.committedNext)
            // No record is queued twice, and none is missing.
            assertEquals(
                (0L until 120L).toList(),
                rig.sink.committedFrames
                    .map { it.ringSeq }
                    .distinct(),
            )
            assertEquals(480, rig.sink.committedFrames.size)
        }

    @Test
    fun `a read timeout frees nothing the phone has not read`() =
        runTest {
            val rig = rig(firmware = "3.0.21", tuning = SyncTuning(windowPackets = 100, commitRecords = 20))
            rig.ring.fill(100)
            rig.ring.stallAfterBytes = 10L * 444
            rig.ring.unseenBytesAtStall = 90L * 444 // the firmware counts these as sent

            rig.connect()
            advanceTimeBy(12_000) // no data for 10 s ends the window
            rig.ring.stallAfterBytes = null
            advanceTimeBy(40_000)
            settle()

            assertEquals(100L, rig.sink.stored?.committedNext)
            assertEquals(0L, rig.status.lostPackets)
            assertEquals(
                (0L until 100L).toList(),
                rig.sink.committedFrames
                    .map { it.ringSeq }
                    .distinct(),
            )
        }

    @Test
    fun `a sync cut by a disconnect resumes from the position on the next connection`() =
        runTest {
            val rig = rig(tuning = SyncTuning(windowPackets = 100, commitRecords = 20))
            rig.ring.fill(100)
            rig.ring.stallAfterBytes = 45L * 444

            rig.connect()
            advanceTimeBy(1_000)
            rig.pendant.dropLink()
            advanceTimeBy(1_000)

            assertTrue(rig.status.state is SyncState.Paused)
            val committed = rig.sink.stored?.committedNext ?: 0L
            assertTrue("committed $committed", committed in 20..45)

            rig.ring.stallAfterBytes = null
            rig.connect()
            settle()

            assertEquals(100L, rig.sink.stored?.committedNext)
            val rings =
                rig.sink.committedFrames
                    .map { it.ringSeq }
                    .distinct()
            assertEquals((0L until 100L).toList(), rings)
            assertEquals(400, rig.sink.committedFrames.size)
            // The new connection is a new session.
            assertEquals(
                2,
                rig.sink.committedFrames
                    .map { it.session }
                    .distinct()
                    .size,
            )
            assertEquals(SyncState.Idle, rig.status.state)
        }

    @Test
    fun `audio the firmware freed before it was read is counted as lost and logged`() =
        runTest {
            val rig = rig(firmware = "3.0.21", tuning = SyncTuning(windowPackets = 500, commitRecords = 20))
            rig.ring.fill(1_010) // the ring holds 1,000: the first 10 were overwritten
            rig.sink.stored = RingPosition("fake", committedNext = 0, lastDropped = 0)

            rig.connect()
            settle()

            assertEquals(10L, rig.status.lostPackets)
            assertTrue(rig.log.messages.any { it.startsWith("lost 10 packets") })
            assertEquals(1_010L, rig.sink.stored?.committedNext)
            assertEquals(
                (10L until 1_010L).toList(),
                rig.sink.committedFrames
                    .map { it.ringSeq }
                    .distinct(),
            )
        }

    @Test
    fun `on 3_0_21 an advance below the firmware's own readSeq answers 10 and is still recorded`() =
        runTest {
            val rig = rig(firmware = "3.0.21")
            rig.ring.fill(60)
            rig.connect()
            settle()
            assertEquals(60L, rig.ring.readSeq) // the firmware moved readSeq to what it sent

            rig.sink.accept(20)
            settle()
            assertEquals(listOf(20L), rig.advances) // status 10, since the firmware is already past it
            assertEquals(listOf(20L), rig.sink.advanced)

            rig.sink.accept(60)
            advanceTimeBy(10_001)
            assertEquals(listOf(20L, 60L), rig.advances)
            assertEquals(listOf(20L, 60L), rig.sink.advanced)
        }

    @Test
    fun `a new epoch is freed from its own numbers, not from the last epoch's`() =
        runTest {
            val rig = rig()
            rig.ring.fill(60)
            rig.connect()
            settle()
            rig.sink.accept(60)
            settle()
            assertEquals(listOf(60L), rig.advances)

            // The ring is cleared and refilled: sequence numbers restart below the last ADVANCE.
            rig.pendant.dropLink()
            settle()
            rig.ring.clear()
            rig.ring.fill(30, firstStampS = STAMP_S + 100)
            rig.connect()
            settle()
            rig.sink.accept(30)
            advanceTimeBy(10_001)

            assertEquals(30L, rig.advances.last())
            assertEquals(30L, rig.ring.readSeq)
        }

    @Test
    fun `a failure that is not a database error costs a retry, not the process`() =
        runTest {
            val rig = rig()
            rig.ring.fill(30)
            rig.sink.failCommitOnce = IllegalStateException("boom")

            rig.connect()
            settle()

            assertTrue(rig.status.state is SyncState.Retrying)
            advanceTimeBy(30_001)
            settle()
            assertEquals(30L, rig.sink.stored?.committedNext)
        }

    @Test
    fun `the persisted skew pair is kept while records below its writeSeq are unread`() =
        runTest {
            val rig = rig()
            rig.ring.fill(120)
            val old = ClockPair(writeSeq = 80, skewS = 300)
            rig.sink.stored = RingPosition("fake", committedNext = 10, clock = old, staleClock = true)

            rig.connect()
            settle()

            assertEquals(120L, rig.sink.stored?.committedNext)
            assertEquals(old, rig.sink.stored?.clock)
            assertEquals(300L, rig.status.skewS)

            // Next connection: nothing below the old writeSeq is unread, so the pair is replaced.
            rig.pendant.dropLink()
            settle()
            rig.ring.fill(12, firstStampS = STAMP_S + 30)
            rig.connect()
            settle()
            val pair = rig.sink.stored?.clock
            assertNotNull(pair)
            assertEquals(132L, pair?.writeSeq)
            assertEquals(0L, pair?.skewS)
        }

    @Test
    fun `live loss over five percent pauses 30 seconds and the third pause stops the sync`() =
        runTest {
            val rig = rig(tuning = SyncTuning(windowPackets = 20, commitRecords = 20))
            rig.steered.loseOnDone = 20 // 20 of 100 notifications a window
            rig.ring.fill(100)

            rig.connect()
            advanceTimeBy(1_000)

            assertEquals(listOf(0L), rig.steered.reads)
            assertEquals(SyncState.Paused(PauseReason.LiveLoss), rig.status.state)
            advanceTimeBy(29_000)
            assertEquals(listOf(0L), rig.steered.reads)
            advanceTimeBy(1_500)
            assertEquals(listOf(0L, 20L), rig.steered.reads)
            advanceTimeBy(31_000)
            advanceTimeBy(31_000)

            assertEquals(listOf(0L, 20L, 40L), rig.steered.reads) // the third pause ended the run
            assertEquals(SyncState.Paused(PauseReason.LiveLoss), rig.status.state)
            advanceTimeBy(600_000)
            assertEquals(3, rig.steered.reads.size)
            assertTrue(rig.status.liveLoss!! > 0.05)
        }

    @Test
    fun `stop holds the sync until the next connection, sync now lifts it`() =
        runTest {
            val rig = rig()
            rig.ring.fill(60)
            rig.usage.value = QueueUsage(bytes = 60, capBytes = 100) // holds the sync before its first window
            rig.connect()
            settle()

            rig.controller.stopSync()
            rig.usage.value = QueueUsage(bytes = 0, capBytes = 100)
            settle()

            assertEquals(SyncState.Paused(PauseReason.Stopped), rig.status.state)
            assertTrue(rig.reads.isEmpty())

            rig.controller.syncNow()
            settle()
            assertEquals(60L, rig.sink.stored?.committedNext)

            rig.controller.stopSync()
            rig.pendant.dropLink()
            settle()
            rig.ring.fill(12, firstStampS = STAMP_S + 100)
            rig.connect()
            settle()
            assertEquals(72L, rig.sink.stored?.committedNext)
        }

    @Test
    fun `status 9 is retried every 2 seconds before the sync gives up`() =
        runTest {
            val rig = rig()
            rig.ring.fill(30)
            rig.ring.notReadyAnswers = 3

            rig.connect()
            advanceTimeBy(7_000)
            settle()

            assertEquals(30L, rig.sink.stored?.committedNext)
            assertEquals(3, rig.opcodes.count { it == INFO } - 1)
        }

    @Test
    fun `an empty ring completes at once and reports nothing stored`() =
        runTest {
            val rig = rig()

            rig.connect()
            settle()

            assertTrue(rig.reads.isEmpty())
            assertEquals(SyncState.Idle, rig.status.state)
            assertNull(rig.sink.stored?.takeIf { it.committedNext != 0L })
            assertEquals(0L, rig.status.storedPackets)
        }

    private companion object {
        const val BASE_MS = 1_800_000_000_000L
        const val STAMP_S = 1_800_000_000L - 600
        const val INFO = 0x10
        const val READ = 0x11
        const val ADVANCE = 0x12
    }
}
