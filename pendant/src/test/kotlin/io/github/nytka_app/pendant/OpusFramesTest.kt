package io.github.nytka_app.pendant

import io.github.nytka_app.pendant.LimitlessFixtures.TEN_MS
import io.github.nytka_app.pendant.LimitlessFixtures.TWENTY_MS
import io.github.nytka_app.pendant.LimitlessFixtures.opus
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class OpusFramesTest {
    @Test
    fun `a 20 ms packet passes through untouched`() {
        val frame = opus(TWENTY_MS)
        val joined = OpusFrames.toTwentyMs(listOf(frame))
        assertEquals(1, joined.frames.size)
        assertArrayEquals(frame, joined.frames.single())
        assertEquals(0, joined.dropped)
    }

    @Test
    fun `two 10 ms packets of one size become one packet of code 1`() {
        val a = opus(TEN_MS, size = 30, fill = 1)
        val b = opus(TEN_MS, size = 30, fill = 2)
        val joined = OpusFrames.toTwentyMs(listOf(a, b))
        val expected = byteArrayOf((TEN_MS or 1).toByte()) + a.copyOfRange(1, a.size) + b.copyOfRange(1, b.size)
        assertEquals(1, joined.frames.size)
        assertArrayEquals(expected, joined.frames.single())
        assertEquals(0, joined.dropped)
    }

    @Test
    fun `two 10 ms packets of different sizes become one packet of code 2 with the first length`() {
        val a = opus(TEN_MS, size = 25, fill = 1)
        val b = opus(TEN_MS, size = 31, fill = 2)
        val joined = OpusFrames.toTwentyMs(listOf(a, b))
        val expected = byteArrayOf((TEN_MS or 2).toByte(), 24) + a.copyOfRange(1, a.size) + b.copyOfRange(1, b.size)
        assertArrayEquals(expected, joined.frames.single())
    }

    @Test
    fun `a 10 ms packet left over is dropped and counted`() {
        val joined = OpusFrames.toTwentyMs(listOf(opus(TEN_MS), opus(TEN_MS), opus(TEN_MS)))
        assertEquals(1, joined.frames.size)
        assertEquals(1, joined.dropped)
    }

    @Test
    fun `a 10 ms packet followed by a 20 ms one is dropped and the 20 ms one is kept`() {
        val twenty = opus(TWENTY_MS)
        val joined = OpusFrames.toTwentyMs(listOf(opus(TEN_MS), twenty))
        assertEquals(1, joined.frames.size)
        assertArrayEquals(twenty, joined.frames.single())
        assertEquals(1, joined.dropped)
    }

    @Test
    fun `10 ms packets of another kind are not paired`() {
        val joined = OpusFrames.toTwentyMs(listOf(opus(TEN_MS), opus(0x70)))
        assertEquals(0, joined.frames.size)
        assertEquals(2, joined.dropped)
    }

    @Test
    fun `an empty packet is dropped`() {
        val joined = OpusFrames.toTwentyMs(listOf(ByteArray(0), opus(TWENTY_MS)))
        assertEquals(1, joined.frames.size)
        assertEquals(1, joined.dropped)
    }

    @Test
    fun `a stream joiner pairs packets across calls and flush gives up on one waiting`() {
        val joiner = OpusJoiner()
        val out = ArrayList<ByteArray>()
        joiner.add(opus(TEN_MS), out)
        assertEquals(0, out.size)
        joiner.add(opus(TEN_MS), out)
        assertEquals(1, out.size)
        joiner.add(opus(TEN_MS), out)
        joiner.flush()
        assertEquals(1, out.size)
        assertEquals(1, joiner.dropped)
    }
}
