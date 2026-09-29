package io.github.nytka_app.pendant

import io.github.nytka_app.pendant.RingFixtures.frame
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeRingTest {
    private fun TestScope.setUp(
        firmware: String = "3.0.21",
        autoAdvance: Boolean = true,
    ): Pair<FakeRing, OmiStorage> {
        val ring = FakeRing(backgroundScope, firmware = firmware, autoAdvance = autoAdvance)
        val storage = OmiStorage(ring).also { ring.listener = it::onNotification }
        storage.evaluate(firmware, ring.features)
        return ring to storage
    }

    private fun records(ring: FakeRing) =
        ring.store(1_800_000_000L, List(8) { frame(97, it + 1) }) // 2 records of 4 frames

    @Test
    fun `stores frames with the firmware's packing and reads them back`() =
        runTest {
            val (ring, storage) = setUp()
            records(ring)
            assertEquals(2L, ring.writeSeq)

            val events = storage.read(0, 0).toList()
            val got = events.filterIsInstance<RingEvent.Records>().flatMap { it.records }
            assertEquals(listOf(0L, 1L), got.map { it.seq })
            assertEquals(listOf(4, 4), got.map { it.frames.size })
            assertEquals(listOf<Byte>(1, 2, 3, 4), got[0].frames.map { it[0] })
            assertEquals(2L, (events.last() as RingEvent.Done).nextSeq)
        }

    @Test
    fun `a frame that does not fit starts the next record and stale bytes stay unread`() =
        runTest {
            val (ring, storage) = setUp()
            // Record 1 ends after the 20-byte frame; record 2 overflows at 351 over bytes record 1 left behind.
            val sizes = listOf(99, 99, 99, 99, 20, 99, 250, 99)
            val stored = sizes.mapIndexed { i, n -> frame(n, i + 1) }
            ring.store(1_800_000_000L, stored)
            assertEquals(3L, ring.writeSeq)

            val got =
                storage
                    .read(0, 0)
                    .toList()
                    .filterIsInstance<RingEvent.Records>()
                    .flatMap { it.records }
            assertEquals(listOf(5, 2, 1), got.map { it.frames.size })
            got.flatMap { it.frames }.forEachIndexed { i, f -> assertArrayEquals(stored[i], f) }
            assertTrue(got.all { it.stampS == 1_800_000_000L })
        }

    @Test
    fun `reading frees nothing on 3_0_20 and everything sent on 3_0_21`() =
        runTest {
            val (old, oldStorage) = setUp(firmware = "3.0.20", autoAdvance = false)
            records(old)
            oldStorage.read(0, 0).toList()
            assertEquals(0L, old.readSeq)

            val (new, newStorage) = setUp()
            records(new)
            newStorage.read(0, 0).toList()
            assertEquals(2L, new.readSeq)
        }

    @Test
    fun `an interrupted read costs 3_0_21 what it advanced past and 3_0_20 nothing`() =
        runTest {
            for ((firmware, auto, expected) in listOf(Triple("3.0.20", false, 0L), Triple("3.0.21", true, 3L))) {
                val (ring, storage) = setUp(firmware, auto)
                ring.store(1_800_000_000L, List(40) { frame(97, 1) }) // 10 records
                ring.stallAfterBytes = 444 * 3 + 10L
                val got = mutableListOf<RingRecord>()
                val job =
                    backgroundScope.launch {
                        storage.read(0, 0).collect { if (it is RingEvent.Records) got += it.records }
                    }
                runCurrent()
                assertEquals(listOf(0L, 1L, 2L), got.map { it.seq })
                job.cancel() // the app stops; the ring was left where the firmware put it
                runCurrent()
                assertEquals(firmware, expected, ring.readSeq)
            }
        }

    @Test
    fun `advance moves readSeq within range and answers 10 outside it`() =
        runTest {
            val (ring, storage) = setUp(autoAdvance = false)
            records(ring)
            assertEquals(0, storage.advance(1))
            assertEquals(1L, ring.readSeq)
            assertEquals(RingStatus.OUT_OF_RANGE, storage.advance(3))
            assertEquals(RingStatus.OUT_OF_RANGE, storage.advance(0))
            assertEquals(1L, ring.readSeq)
        }

    @Test
    fun `a read outside the ring answers ACK 10 and a not ready card ACK 9`() =
        runTest {
            val (ring, storage) = setUp()
            records(ring)
            assertEquals(RingStatus.OUT_OF_RANGE, (storage.read(5, 0).toList().single() as RingEvent.Done).status)
            ring.notReadyAnswers = 1
            assertNull(storage.info())
            assertEquals(RingStatus.NOT_READY, storage.lastStatus.value)
            assertEquals(2L, storage.info()?.writeSeq)
        }

    @Test
    fun `stray DATA after the stop ack does not leak into the next window`() =
        runTest {
            val (ring, storage) = setUp("3.0.20", autoAdvance = false)
            ring.strayDataAfterStop = 3
            ring.stallAfterBytes = 454
            records(ring)
            val job = backgroundScope.launch { storage.read(0, 0).collect { } }
            runCurrent()
            job.cancel()
            runCurrent()
            ring.stallAfterBytes = null
            val got =
                storage
                    .read(0, 0)
                    .toList()
                    .filterIsInstance<RingEvent.Records>()
                    .flatMap { it.records }
            assertEquals(listOf(0L, 1L), got.map { it.seq })
        }

    @Test
    fun `a clear restarts the sequence numbers`() =
        runTest {
            val (ring, storage) = setUp()
            records(ring)
            ring.clear()
            val info = storage.info()!!
            assertEquals(0L, info.readSeq)
            assertEquals(0L, info.writeSeq)
        }

    @Test
    fun `a full ring overwrites its oldest record and counts it dropped`() =
        runTest {
            val ring = FakeRing(backgroundScope, capacityPackets = 2)
            repeat(3) { ring.storeRaw(RingFixtures.record(it.toLong(), emptyList())) }
            assertEquals(1L, ring.readSeq)
            assertEquals(3L, ring.writeSeq)
            assertEquals(1L, ring.droppedPackets)
        }

    @Test
    fun `an unknown command answers status 6`() =
        runTest {
            val (ring, storage) = setUp()
            val seen = mutableListOf<ByteArray>()
            ring.listener = { seen += it }
            ring.subscribeControl()
            ring.writeControl(byteArrayOf(0x42))
            assertArrayEquals(byteArrayOf(0x01, 6), seen.single())
            assertEquals(StorageSupport.Supported, storage.support.value)
        }

    @Test
    fun `the fake pendant sets its clock and exposes the ring once connected`() =
        runTest {
            val pendant = FakePendant(listOf(byteArrayOf(1)), backgroundScope) { 1_800_000_030_000L }
            pendant.ring.clockS = 1_800_000_090L
            assertEquals(StorageSupport.Unknown, pendant.storage.support.value)
            pendant.connect("fake")
            runCurrent()
            assertEquals(StorageSupport.Supported, pendant.storage.support.value)
            assertEquals(60L, pendant.storage.clockSkew.value)
            assertEquals(listOf(1_800_000_030L), pendant.ring.clockWrites)

            pendant.dropLink()
            assertEquals(StorageSupport.Unknown, pendant.storage.support.value)
        }
}
