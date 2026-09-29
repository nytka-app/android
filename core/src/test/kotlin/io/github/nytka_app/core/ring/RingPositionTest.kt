package io.github.nytka_app.core.ring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class RingPositionTest {
    private val session = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val position =
        RingPosition(
            pendant = "AA:BB",
            committedNext = 500,
            epoch = 2,
            advanced = 400,
            session = session,
            nextFrame = 90,
            lastDropped = 7,
            lastStampS = 1_800_000_000,
            clock = ClockPair(480, -30),
            staleClock = true,
        )

    @Test
    fun `a new run gets a new session numbered from 0 and keeps the stamp`() {
        val state = position.newRun(UUID.fromString("00000000-0000-0000-0000-000000000009"))

        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000009"), state.session)
        assertEquals(0L, state.nextFrame)
        assertEquals(1_800_000_000L, state.lastStampS)
        assertTrue(state.stale)
    }

    @Test
    fun `a continued run keeps the session and the next frame number`() {
        val state = position.continueRun()

        assertEquals(session, state.session)
        assertEquals(90L, state.nextFrame)
    }

    @Test
    fun `continuing without a session numbers from 0`() {
        val state = position.copy(session = null, nextFrame = 90).continueRun()

        assertEquals(0L, state.nextFrame)
    }

    @Test
    fun `after a commit the position follows the time state`() {
        val timed =
            TimedFrames(emptyList(), TimeState(session, nextFrame = 130, lastStampS = 1_800_000_050, stale = false))

        val next = position.after(timed, next = 540)

        assertEquals(540L, next.committedNext)
        assertEquals(130L, next.nextFrame)
        assertEquals(1_800_000_050L, next.lastStampS)
        assertFalse(next.staleClock)
        assertEquals(position.epoch, next.epoch)
        assertEquals(position.clock, next.clock)
    }

    @Test
    fun `a ring that ends before what was read, or drops less than before, was cleared`() {
        assertTrue(position.wasCleared(writeSeq = 499, droppedPackets = 7))
        assertTrue(position.wasCleared(writeSeq = 900, droppedPackets = 6))
        assertFalse(position.wasCleared(writeSeq = 500, droppedPackets = 7))
        assertFalse(position.wasCleared(writeSeq = 900, droppedPackets = 100))
    }

    @Test
    fun `a first record over 2 seconds before the last committed stamp is a refill`() {
        assertTrue(position.wasRefilled(1_800_000_000 - 3))
        assertFalse(position.wasRefilled(1_800_000_000 - 2))
        assertFalse(position.wasRefilled(1_800_000_100))
        assertFalse(position.copy(lastStampS = null).wasRefilled(5))
    }

    @Test
    fun `a new epoch restarts at the read sequence with nothing carried over`() {
        val next = position.newEpoch(readSeq = 12, droppedPackets = 0)

        assertEquals(3L, next.epoch)
        assertEquals(12L, next.committedNext)
        assertEquals(12L, next.advanced)
        assertNull(next.session)
        assertEquals(0L, next.nextFrame)
        assertEquals(0L, next.lastDropped)
        assertNull(next.lastStampS)
        assertNull(next.clock)
        assertFalse(next.staleClock)
        assertEquals(position.pendant, next.pendant)
        assertNotEquals(position, next)
    }
}
