package io.github.nytka_app.core.ring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class MuteFilterTest {
    private val session = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private fun frame(
        seq: Long,
        atMs: Long,
        ringSeq: Long = seq,
        session: UUID = this.session,
    ) = TimedFrame(session, seq, atMs, ringSeq, byteArrayOf(seq.toByte()))

    private fun timed(
        frames: List<TimedFrame>,
        nextFrame: Long = frames.size.toLong(),
    ) = TimedFrames(frames, TimeState(session, nextFrame = nextFrame))

    @Test
    fun `covers a muted stretch and 2 seconds either side`() {
        val filter = MuteFilter(listOf(MuteChange(10_000, true), MuteChange(20_000, false)))

        assertFalse(filter.covers(7_999))
        assertTrue(filter.covers(8_000))
        assertTrue(filter.covers(15_000))
        assertTrue(filter.covers(22_000))
        assertFalse(filter.covers(22_001))
    }

    @Test
    fun `a mute stays open until the next change`() {
        val filter = MuteFilter(listOf(MuteChange(10_000, true)))

        assertTrue(filter.covers(10_000_000_000))
    }

    @Test
    fun `nothing is muted before the first change or with no log`() {
        assertFalse(MuteFilter(emptyList()).covers(5))
        assertFalse(MuteFilter(listOf(MuteChange(10_000, false), MuteChange(20_000, true))).covers(15_000))
    }

    @Test
    fun `changes may arrive unordered and repeat`() {
        val filter =
            MuteFilter(listOf(MuteChange(20_000, false), MuteChange(10_000, true), MuteChange(12_000, true)))

        assertTrue(filter.covers(15_000))
        assertFalse(filter.covers(30_000))
    }

    @Test
    fun `two muted stretches leave the audio between them`() {
        val filter =
            MuteFilter(
                listOf(
                    MuteChange(10_000, true),
                    MuteChange(11_000, false),
                    MuteChange(60_000, true),
                    MuteChange(61_000, false),
                ),
            )

        assertTrue(filter.covers(12_500))
        assertFalse(filter.covers(30_000))
        assertTrue(filter.covers(59_000))
    }

    @Test
    fun `removes the covered frames and numbers the rest without holes`() {
        val filter = MuteFilter(listOf(MuteChange(1_000, true), MuteChange(2_000, false)), marginMs = 0)
        val times = listOf(0L, 900, 1_000, 1_500, 2_001, 2_200)
        val frames = times.mapIndexed { i, atMs -> frame(5L + i, atMs) }

        val result =
            filter.apply(
                timed(frames).copy(state = TimeState(session, nextFrame = 11)),
                TimeState(session, nextFrame = 5),
            )

        assertEquals(listOf(5L, 6L, 7L, 8L), result.frames.map { it.seq })
        assertEquals(listOf(5L, 6L, 9L, 10L), result.frames.map { it.ringSeq })
        assertEquals(9L, result.state.nextFrame)
        assertEquals(2, result.mutedFrames)
    }

    @Test
    fun `numbers each session from its own start`() {
        val other = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val filter = MuteFilter(listOf(MuteChange(1_000, true), MuteChange(2_000, false)), marginMs = 0)
        val frames =
            listOf(
                frame(3, 0),
                frame(4, 1_500),
                frame(0, 500, session = other),
                frame(1, 1_500, session = other),
                frame(2, 2_500, session = other),
            )
        val result =
            filter.apply(
                TimedFrames(frames, TimeState(other, nextFrame = 3)),
                TimeState(session, nextFrame = 3),
            )

        assertEquals(listOf(3L to session, 0L to other, 1L to other), result.frames.map { it.seq to it.session })
        assertEquals(2L, result.state.nextFrame)
    }

    @Test
    fun `keeps the payload and ring sequence of the frames it keeps`() {
        val filter = MuteFilter(listOf(MuteChange(1_000, true), MuteChange(2_000, false)), marginMs = 0)

        val result =
            filter.apply(
                timed(listOf(frame(0, 0, ringSeq = 40), frame(1, 1_500, ringSeq = 41))),
                TimeState(session),
            )

        assertEquals(listOf(40L), result.frames.map { it.ringSeq })
        assertEquals(
            listOf<Byte>(0),
            result.frames
                .single()
                .payload
                .toList(),
        )
    }

    @Test
    fun `with nothing muted the batch comes back as it was`() {
        val batch = timed(listOf(frame(0, 0)))

        assertSame(batch, MuteFilter(emptyList()).apply(batch, TimeState(session)))
    }
}
