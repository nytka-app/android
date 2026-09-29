package io.github.nytka_app.pendant

import io.github.nytka_app.pendant.RingFixtures.ack
import io.github.nytka_app.pendant.RingFixtures.bytes
import io.github.nytka_app.pendant.RingFixtures.data
import io.github.nytka_app.pendant.RingFixtures.done
import io.github.nytka_app.pendant.RingFixtures.frame
import io.github.nytka_app.pendant.RingFixtures.info
import io.github.nytka_app.pendant.RingFixtures.readBegin
import io.github.nytka_app.pendant.RingFixtures.record
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The storage protocol against a link whose answers the test scripts byte by byte. */
class OmiStorageTest {
    private class ScriptedLink : StorageLink {
        lateinit var storage: OmiStorage
        val written = mutableListOf<ByteArray>()
        var subscriptions = 0
        var clock: Long? = null
        var clockWrites = mutableListOf<Long>()
        var features: Long? = 1L shl 6

        /** Answers a control write; the test pushes notifications through [notify]. */
        var onWrite: (ByteArray) -> Unit = {}

        fun notify(vararg values: ByteArray) = values.forEach { storage.onNotification(it) }

        override suspend fun subscribeControl(): Boolean {
            subscriptions++
            return true
        }

        override suspend fun writeControl(value: ByteArray): Boolean {
            written += value
            onWrite(value)
            return true
        }

        override suspend fun readClock() = clock

        override suspend fun writeClock(epochS: Long): Boolean {
            clockWrites += epochS
            return true
        }

        override suspend fun readFeatures() = features
    }

    private val link = ScriptedLink()
    private val storage = OmiStorage(link).also { link.storage = it }

    private fun supported() = storage.evaluate("3.0.21", 1L shl 6)

    private fun opcodes() = link.written.map { it[0].toInt() }

    /** Answers stop with ACK 0, like the firmware. */
    private fun answerStop(then: (ByteArray) -> Unit = {}) {
        link.onWrite = {
            if (it[0].toInt() == 0x03) link.notify(ack(0))
            then(it)
        }
    }

    private fun TestScope.collect(
        from: Long = 100,
        count: Int = 2_000,
    ): Pair<Job, MutableList<RingEvent>> {
        val events = mutableListOf<RingEvent>()
        val job = backgroundScope.launch { storage.read(from, count).collect { events += it } }
        runCurrent()
        return job to events
    }

    // --- support and gating

    @Test
    fun `nothing is written to an unsupported pendant`() =
        runTest {
            storage.evaluate("3.0.19", 1L shl 6)
            assertEquals(StorageSupport.Unsupported::class, storage.support.value::class)
            assertNull(storage.info())
            assertEquals(RingStatus.UNAVAILABLE, storage.advance(5))
            val events = storage.read(0, 10).toList()
            assertEquals(RingStatus.UNAVAILABLE, (events.single() as RingEvent.Done).status)
            assertTrue(link.written.isEmpty())
            assertEquals(0, link.subscriptions)
        }

    @Test
    fun `nothing is written before support is known`() =
        runTest {
            assertEquals(StorageSupport.Unknown, storage.support.value)
            assertNull(storage.info())
            assertTrue(link.written.isEmpty())
        }

    @Test
    fun `support needs firmware 3_0_20 and the feature bit`() {
        val tooOld = OmiStorage.supportFor("3.0.19", 1L shl 6) as StorageSupport.Unsupported
        assertEquals(
            "Offline sync needs firmware 3.0.20 or later; this pendant has 3.0.19. " +
                "Update it with the official Omi app.",
            tooOld.reason,
        )
        assertEquals(StorageSupport.Supported, OmiStorage.supportFor("3.0.20", 1L shl 6))
        assertEquals(StorageSupport.Supported, OmiStorage.supportFor("3.0.21", 0xFFL))
        assertTrue(OmiStorage.supportFor("3.0.21", 0b0011_1111L) is StorageSupport.Unsupported) // bit 6 clear
        assertTrue(OmiStorage.supportFor("3.0.21", null) is StorageSupport.Unsupported)
        assertTrue(OmiStorage.supportFor(null, 1L shl 6) is StorageSupport.Unsupported)
        assertTrue(OmiStorage.supportFor("fake", 1L shl 6) is StorageSupport.Unsupported)
    }

    @Test
    fun `losing the link forgets support and the subscription`() =
        runTest {
            supported()
            answerStop { link.notify(info(0, 0, 100, 0)) }
            storage.info()
            assertEquals(1, link.subscriptions)
            storage.linkLost()
            assertEquals(StorageSupport.Unknown, storage.support.value)
            supported()
            storage.info()
            assertEquals(2, link.subscriptions)
        }

    // --- clock

    @Test
    fun `the clock is read before it is written and the skew is pendant minus phone`() =
        runTest {
            link.clock = 1_800_000_360L
            assertTrue(storage.syncClock { 1_800_000_000L })
            assertEquals(360L, storage.clockSkew.value)
            assertEquals(listOf(1_800_000_000L), link.clockWrites)
        }

    @Test
    fun `an unreadable clock leaves the skew unknown but still writes the time`() =
        runTest {
            link.clock = null
            assertTrue(storage.syncClock { 1_800_000_000L })
            assertNull(storage.clockSkew.value)
            assertEquals(1, link.clockWrites.size)
        }

    // --- info

    @Test
    fun `info sends stop first, then INFO, and parses the answer`() =
        runTest {
            supported()
            answerStop { if (it[0].toInt() == 0x10) link.notify(info(40, 5_000, 1_180_000, 3)) }
            val ring = storage.info()
            assertEquals(RingInfo(40, 5_000, 1_180_000, 3, 444), ring)
            assertEquals(listOf(0x03, 0x10), opcodes())
            assertNull(storage.lastStatus.value)
        }

    @Test
    fun `info answering ACK 9 returns null with the status`() =
        runTest {
            supported()
            answerStop { if (it[0].toInt() == 0x10) link.notify(ack(9)) }
            assertNull(storage.info())
            assertEquals(9, storage.lastStatus.value)
        }

    @Test
    fun `info ignores DATA still in flight before the stop ACK`() =
        runTest {
            supported()
            link.onWrite = {
                when (it[0].toInt()) {
                    0x03 -> link.notify(data(bytes(1, 2, 3)), ack(0))
                    0x10 -> link.notify(data(bytes(4)), info(0, 1, 2, 3))
                }
            }
            assertEquals(1L, storage.info()?.writeSeq)
        }

    @Test
    fun `info without an answer times out`() =
        runTest {
            supported()
            val result = backgroundScope.launch { storage.info() }
            runCurrent()
            advanceTimeBy(8_000)
            assertTrue(result.isCompleted)
            assertEquals(RingStatus.TIMEOUT, storage.lastStatus.value)
        }

    // --- read

    private val a = record(1_800_000_000L, listOf(frame(97, 1), frame(97, 2)))
    private val b = record(1_800_000_001L, listOf(frame(97, 3)))
    private val c = record(1_800_000_001L, listOf(frame(97, 4)))

    @Test
    fun `a window yields begin, numbered records from unaligned data, and done`() =
        runTest {
            supported()
            val (job, events) = collect(from = 100, count = 3)
            assertArrayEquals(bytes(0x11, 0, 0, 0, 0, 0, 0, 0, 100, 0, 0, 0, 3), link.written.last())

            val stream = a + b + c
            link.notify(readBegin(100, 3))
            for (start in stream.indices step 240) {
                link.notify(data(stream.copyOfRange(start, minOf(start + 240, stream.size))))
            }
            link.notify(done(0, 103))
            runCurrent()

            assertTrue(job.isCompleted)
            val begin = events[0] as RingEvent.Begin
            assertEquals(100L, begin.startSeq)
            assertEquals(3L, begin.count)
            val records = events.filterIsInstance<RingEvent.Records>().flatMap { it.records }
            assertEquals(listOf(100L, 101L, 102L), records.map { it.seq })
            assertEquals(listOf(2, 1, 1), records.map { it.frames.size })
            assertEquals(listOf(1_800_000_000L, 1_800_000_001L, 1_800_000_001L), records.map { it.stampS })
            val end = events.last() as RingEvent.Done
            assertEquals(0, end.status)
            assertEquals(103L, end.nextSeq)
            assertEquals(listOf(0x11), opcodes()) // DONE ended it: no stop
        }

    @Test
    fun `a READ_BEGIN restarts the reassembler`() =
        runTest {
            supported()
            val (_, events) = collect(from = 100)
            link.notify(readBegin(100, 5), data(a.copyOf(300))) // a partial record ...
            link.notify(readBegin(100, 5), data(a), data(b)) // ... that the second begin drops
            runCurrent()

            val records = events.filterIsInstance<RingEvent.Records>().flatMap { it.records }
            assertEquals(listOf(100L, 101L), records.map { it.seq })
            assertEquals(2, records[0].frames.size)
            assertEquals(2, events.filterIsInstance<RingEvent.Begin>().size)
        }

    @Test
    fun `a stray DATA packet after the STOP ACK is dropped by the next window`() =
        runTest {
            supported()
            answerStop {
                if (it[0].toInt() ==
                    0x03
                ) {
                    link.notify(data(ByteArray(200) { 7 }), data(ByteArray(300) { 8 }))
                }
            }
            val (first, _) = collect(from = 100)
            link.notify(readBegin(100, 9), data(a.copyOf(100)))
            runCurrent()
            first.cancel()
            runCurrent()
            // stop, its ACK 0, then 500 stray bytes: all consumed while stop waited
            assertEquals(listOf(0x11, 0x03), opcodes())

            // 200 more stray bytes arrive before the next window's READ_BEGIN
            val (_, events) = collect(from = 101)
            link.notify(data(ByteArray(200) { 9 }), readBegin(101, 1), data(b), done(0, 102))
            runCurrent()

            val records = events.filterIsInstance<RingEvent.Records>().flatMap { it.records }
            assertEquals(listOf(101L), records.map { it.seq })
            assertEquals(1_800_000_001L, records.single().stampS)
            assertEquals(
                97,
                records
                    .single()
                    .frames
                    .single()
                    .size,
            )
        }

    @Test
    fun `stray DATA arriving after the stop is consumed by the stop`() =
        runTest {
            supported()
            answerStop()
            val (job, _) = collect(from = 100)
            link.notify(readBegin(100, 9))
            runCurrent()
            job.cancel()
            runCurrent()
            link.notify(data(a)) // late, no operation open: dropped
            val (_, events) = collect(from = 100)
            link.notify(readBegin(100, 1), data(b), done(0, 101))
            runCurrent()
            val records = events.filterIsInstance<RingEvent.Records>().flatMap { it.records }
            assertEquals(listOf(100L), records.map { it.seq })
        }

    @Test
    fun `a failed READ answers ACK and surfaces as done with that status`() =
        runTest {
            supported()
            link.onWrite = { if (it[0].toInt() == 0x11) link.notify(ack(10)) }
            val events = storage.read(999_999, 2_000).toList()
            val end = events.single() as RingEvent.Done
            assertEquals(10, end.status)
            assertEquals(999_999L, end.nextSeq)
            assertEquals(listOf(0x11), opcodes()) // the firmware ended it: no stop
        }

    @Test
    fun `ACK 9 to a READ is a done with status 9`() =
        runTest {
            supported()
            link.onWrite = { if (it[0].toInt() == 0x11) link.notify(ack(9)) }
            assertEquals(9, (storage.read(5, 10).toList().single() as RingEvent.Done).status)
        }

    @Test
    fun `an ACK 0 before READ_BEGIN is a stale stop answer, not a failure`() =
        runTest {
            supported()
            val (_, events) = collect()
            link.notify(ack(0), readBegin(100, 1), data(a), done(0, 101))
            runCurrent()
            assertEquals(1, events.filterIsInstance<RingEvent.Records>().size)
            assertEquals(0, (events.last() as RingEvent.Done).status)
        }

    @Test
    fun `cancelling the collector sends stop and waits for its ACK`() =
        runTest {
            supported()
            answerStop()
            val (job, _) = collect()
            link.notify(readBegin(100, 5), data(a))
            runCurrent()
            job.cancel()
            runCurrent()
            assertEquals(listOf(0x11, 0x03), opcodes())
            assertArrayEquals(bytes(0x03), link.written.last())
        }

    @Test
    fun `no READ_BEGIN in ten seconds ends the window and sends stop`() =
        runTest {
            supported()
            answerStop()
            val (job, events) = collect(from = 100)
            advanceTimeBy(9_999)
            assertTrue(events.isEmpty())
            advanceTimeBy(2)
            runCurrent()
            assertTrue(job.isCompleted)
            val end = events.single() as RingEvent.Done
            assertEquals(RingStatus.TIMEOUT, end.status)
            assertEquals(100L, end.nextSeq)
            assertEquals(listOf(0x11, 0x03), opcodes())
        }

    @Test
    fun `later windows wait seven seconds for READ_BEGIN`() =
        runTest {
            supported()
            answerStop()
            val (first, _) = collect(from = 100)
            link.notify(readBegin(100, 1), data(a), done(0, 101))
            runCurrent()
            assertTrue(first.isCompleted)
            val (second, events) = collect(from = 101)
            advanceTimeBy(7_001)
            runCurrent()
            assertTrue(second.isCompleted)
            assertEquals(RingStatus.TIMEOUT, (events.single() as RingEvent.Done).status)
        }

    @Test
    fun `ten seconds without data after the begin is a stall`() =
        runTest {
            supported()
            answerStop()
            val (job, events) = collect(from = 100)
            link.notify(readBegin(100, 5), data(a))
            runCurrent()
            advanceTimeBy(10_001)
            runCurrent()
            assertTrue(job.isCompleted)
            val end = events.last() as RingEvent.Done
            assertEquals(RingStatus.TIMEOUT, end.status)
            assertEquals(101L, end.nextSeq) // one whole record arrived
            assertEquals(listOf(0x11, 0x03), opcodes())
        }

    @Test
    fun `a lost link ends the window without a stop`() =
        runTest {
            supported()
            val (job, events) = collect()
            link.notify(readBegin(100, 5))
            runCurrent()
            storage.linkLost()
            runCurrent()
            assertTrue(job.isCompleted)
            assertEquals(RingStatus.LINK_LOST, (events.last() as RingEvent.Done).status)
            assertEquals(listOf(0x11), opcodes())
        }

    // --- advance

    @Test
    fun `advance returns the ACK status`() =
        runTest {
            supported()
            link.onWrite = { link.notify(ack(if (it[8].toInt() == 5) 0 else 10)) }
            assertEquals(0, storage.advance(5))
            assertArrayEquals(bytes(0x12, 0, 0, 0, 0, 0, 0, 0, 5), link.written.last())
            assertEquals(10, storage.advance(6))
        }

    @Test
    fun `advance without an answer reports a timeout`() =
        runTest {
            supported()
            val result = backgroundScope.launch { assertEquals(RingStatus.TIMEOUT, storage.advance(5)) }
            runCurrent()
            advanceTimeBy(7_001)
            runCurrent()
            assertTrue(result.isCompleted)
        }
}
