package io.github.nytka_app.core.ring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.math.abs

class CaptureTimesTest {
    private val first = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val second = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val times = RingCaptureTimes(newSession = { second })
    private val nowMs = 2_000_000_000_000L

    private fun record(
        seq: Long,
        stampS: Long,
        frames: Int = 4,
    ) = StoredRecord(seq, stampS, List(frames) { byteArrayOf(seq.toByte(), it.toByte()) })

    private fun assign(
        records: List<StoredRecord>,
        state: TimeState = TimeState(first),
        clock: ClockPair? = null,
    ) = times.assign(records, state, clock, nowMs)

    private fun TimedFrames.times() = frames.map { it.capturedAtMs }

    @Test
    fun `the record that rolls the second over ends at its stamp and back-dates what came before`() {
        val timed = assign(listOf(record(0, 100), record(1, 100), record(2, 101), record(3, 101)))

        assertEquals(100_760L, timed.frames.first().capturedAtMs)
        assertEquals(100_980L, timed.frames[11].capturedAtMs) // the anchor record's last frame ends at 101_000
        assertEquals(listOf(101_000L, 101_020L, 101_040L, 101_060L), timed.times().drop(12))
    }

    @Test
    fun `a run with no rollover is left-aligned at its stamp`() {
        val timed = assign(listOf(record(0, 100), record(1, 100), record(2, 105)))

        assertEquals(listOf(100_000L, 100_020L, 100_040L, 100_060L, 100_080L), timed.times().take(5))
        assertEquals(105_000L, timed.frames[8].capturedAtMs)
    }

    @Test
    fun `numbers frames from the state without holes and keeps the session`() {
        val timed = assign(listOf(record(7, 100), record(8, 101)), TimeState(first, nextFrame = 40))

        assertEquals((40L..47L).toList(), timed.frames.map { it.seq })
        assertEquals(setOf(first), timed.frames.map { it.session }.toSet())
        assertEquals(48L, timed.state.nextFrame)
        assertEquals(listOf(7L, 7L, 7L, 7L, 8L, 8L, 8L, 8L), timed.frames.map { it.ringSeq })
    }

    @Test
    fun `frames from a synthetic ring land within 150 ms of the truth`() {
        // Audio from t0 in 20 ms frames, 4 to 6 frames a record, each record stamped with the second it was written in.
        val t0 = 1_800_000_000_000L + 137
        val records = mutableListOf<StoredRecord>()
        var frame = 0L
        var seq = 0L
        while (frame < 3_000) {
            val count = 4 + (seq % 3).toInt()
            frame += count
            val writtenMs = t0 + frame * 20
            records += record(seq++, writtenMs / 1_000, count)
        }

        val timed = assign(records)

        assertEquals(frame.toInt(), timed.frames.size)
        val worst = timed.frames.indices.maxOf { abs(timed.frames[it].capturedAtMs - (t0 + it * 20L)) }
        assertTrue("worst error $worst ms", worst <= 150)
    }

    @Test
    fun `a gap in the recording shows as a jump of the stamps`() {
        val timed = assign(listOf(record(0, 100), record(1, 100), record(2, 101), record(3, 160), record(4, 160)))

        assertEquals(160_000L, timed.frames[12].capturedAtMs)
        assertEquals(160_080L, timed.frames[16].capturedAtMs)
    }

    @Test
    fun `times never run backwards`() {
        // A run ends late (its records were long), and the next one back-dates to before that.
        val records =
            listOf(record(0, 100, 60), record(1, 101, 60), record(2, 102, 2), record(3, 103, 2), record(4, 103, 2))

        val times = assign(records).times()

        assertEquals(times.sorted(), times)
    }

    @Test
    fun `records without frames are counted but produce nothing`() {
        val timed = assign(listOf(record(0, 100, 0), record(1, 100), record(2, 101, 0)))

        assertEquals(4, timed.frames.size)
        assertEquals(100_920L, timed.frames.first().capturedAtMs) // the empty anchor record still marks the second
    }

    @Test
    fun `a stamp that steps back more than 2 seconds starts a new session`() {
        val timed = assign(listOf(record(0, 1_000), record(1, 1_001), record(2, 50), record(3, 50)))

        assertEquals(1, timed.segments)
        assertEquals(
            listOf(first, first, first, first, first, first, first, first),
            timed.frames.take(8).map { it.session },
        )
        assertEquals(
            listOf(second, second, second, second, second, second, second, second),
            timed.frames.drop(8).map {
                it.session
            },
        )
        assertEquals((0L..7L).toList(), timed.frames.drop(8).map { it.seq })
        assertEquals(second, timed.state.session)
        assertEquals(8L, timed.state.nextFrame)
        assertEquals(50_000L, timed.frames[8].capturedAtMs) // no cursor carried over from the old segment
    }

    @Test
    fun `a step back of 2 seconds or less is not a restart`() {
        val timed = assign(listOf(record(0, 100), record(1, 98)))

        assertEquals(0, timed.segments)
        assertEquals(setOf(first), timed.frames.map { it.session }.toSet())
    }

    @Test
    fun `a restart segment is corrected by the connection skew`() {
        // The pendant ran 360 s behind the phone after it restarted.
        val clock = ClockPair(writeSeq = 10, skewS = -360)

        val timed = assign(listOf(record(0, 1_000), record(1, 1_000), record(2, 50), record(3, 50)), clock = clock)

        assertEquals(-360L, timed.skewCorrectedS)
        assertEquals(410_000L, timed.frames[8].capturedAtMs)
        assertEquals(1_000_000L, timed.frames[0].capturedAtMs) // before the restart: as stamped
    }

    @Test
    fun `records at or after the clock write are not corrected`() {
        val clock = ClockPair(writeSeq = 3, skewS = -360)

        val timed = assign(listOf(record(0, 1_000), record(1, 50), record(2, 50), record(3, 500)), clock = clock)

        assertEquals(410_000L, timed.frames[4].capturedAtMs)
        assertEquals(500_000L, timed.frames[12].capturedAtMs) // record 3, written after the phone set the clock
    }

    @Test
    fun `a skew of 5 seconds or less is left alone`() {
        val clock = ClockPair(writeSeq = 10, skewS = 5)

        val timed = assign(listOf(record(0, 1_000), record(1, 50)), clock = clock)

        assertNull(timed.skewCorrectedS)
        assertEquals(50_000L, timed.frames[4].capturedAtMs)
    }

    @Test
    fun `without a restart the stamps are trusted whatever the skew`() {
        val timed = assign(listOf(record(0, 100), record(1, 100)), clock = ClockPair(10, -360))

        assertNull(timed.skewCorrectedS)
        assertEquals(100_000L, timed.frames.first().capturedAtMs)
    }

    @Test
    fun `a stale segment from an earlier batch is still corrected`() {
        val clock = ClockPair(writeSeq = 10, skewS = -360)
        val firstBatch = assign(listOf(record(0, 1_000), record(1, 50)), clock = clock)

        val timed = assign(listOf(record(2, 50), record(3, 51)), firstBatch.state, clock)

        assertTrue(firstBatch.state.stale)
        assertEquals(-360L, timed.skewCorrectedS)
        assertEquals(410_840L, timed.frames.first().capturedAtMs) // ends at 411_000, the second record's stamp
    }

    @Test
    fun `drops and counts records stamped over 60 seconds ahead`() {
        val soon = nowMs / 1_000 + 59
        val far = nowMs / 1_000 + 61

        val timed = assign(listOf(record(0, soon), record(1, far), record(2, soon)))

        assertEquals(1, timed.futureRecords)
        assertEquals(setOf(0L, 2L), timed.frames.map { it.ringSeq }.toSet())
    }

    @Test
    fun `a future stamp does not read as a clock restart for the record after it`() {
        val far = nowMs / 1_000 + 3_600

        val timed = assign(listOf(record(0, 100), record(1, far), record(2, 101)))

        assertEquals(0, timed.segments)
        assertEquals(100L, assign(listOf(record(0, 100), record(1, far))).state.lastStampS)
    }

    @Test
    fun `the state carries a run across batches`() {
        val records = listOf(record(0, 100), record(1, 100), record(2, 101), record(3, 101), record(4, 101))
        val whole = assign(records)

        val head = assign(records.take(3))
        val tail = assign(records.drop(3), head.state)

        assertEquals(whole.times(), head.times() + tail.times())
        assertEquals(whole.frames.map { it.seq }, (head.frames + tail.frames).map { it.seq })
        assertEquals(whole.state, tail.state)
    }

    @Test
    fun `an empty batch keeps the state`() {
        val state = TimeState(first, nextFrame = 9, lastStampS = 77, stale = true)

        val timed = assign(emptyList(), state)

        assertTrue(timed.frames.isEmpty())
        assertEquals(state, timed.state)
    }
}
