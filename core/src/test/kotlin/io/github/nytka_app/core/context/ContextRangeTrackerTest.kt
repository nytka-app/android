package io.github.nytka_app.core.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextRangeTrackerTest {
    private var now = 1_000_000L
    private val ranges = mutableListOf<ContextRange>()
    private val tracker = ContextRangeTracker({ now }, { ranges += it })

    private val media = setOf(Usage.MEDIA)

    private fun advance(seconds: Long) {
        now += seconds * 1_000
        // The recorder's one-shot timer: fires at the deadline the tracker asked for.
        tracker.pendingCloseAtMs?.takeIf { it <= now }?.let { tracker.onTimer() }
    }

    private fun play(
        usages: Set<Usage> = media,
        onSpeaker: Boolean = true,
        volume: Int = 7,
    ) = tracker.onPlayback(usages, onSpeaker, volume)

    @Test
    fun `a 10 s video on the speaker gives one media range`() {
        val start = now
        play()
        advance(10)
        play(emptySet())
        advance(3)

        assertEquals(listOf(ContextRange(RangeKind.MEDIA, Route.SPEAKER, start, start + 10_000)), ranges)
    }

    @Test
    fun `the same on headphones gives none`() {
        play(onSpeaker = false)
        advance(10)
        play(emptySet(), onSpeaker = false)
        advance(3)

        assertTrue(ranges.isEmpty())
    }

    @Test
    fun `volume 0 gives none`() {
        play(volume = 0)
        advance(10)
        play(emptySet(), volume = 0)
        advance(3)

        assertTrue(ranges.isEmpty())
    }

    @Test
    fun `a call-only usage is not media`() {
        play(setOf(Usage.OTHER))
        advance(10)
        play(emptySet())
        advance(3)

        assertTrue(ranges.isEmpty())
    }

    @Test
    fun `game and unknown usages count as media`() {
        val start = now
        play(setOf(Usage.GAME))
        advance(5)
        play(setOf(Usage.UNKNOWN))
        advance(5)
        play(emptySet())
        advance(3)

        assertEquals(listOf(ContextRange(RangeKind.MEDIA, Route.SPEAKER, start, start + 10_000)), ranges)
    }

    @Test
    fun `a pause of 1 s keeps one range`() {
        val start = now
        play()
        advance(10)
        play(emptySet())
        advance(1)
        play()
        advance(10)
        play(emptySet())
        advance(3)

        assertEquals(listOf(ContextRange(RangeKind.MEDIA, Route.SPEAKER, start, start + 21_000)), ranges)
    }

    @Test
    fun `a pause of 3 s gives two ranges`() {
        val start = now
        play()
        advance(10)
        play(emptySet())
        advance(3)
        play()
        advance(10)
        play(emptySet())
        advance(3)

        assertEquals(
            listOf(
                ContextRange(RangeKind.MEDIA, Route.SPEAKER, start, start + 10_000),
                ContextRange(RangeKind.MEDIA, Route.SPEAKER, start + 13_000, start + 23_000),
            ),
            ranges,
        )
    }

    @Test
    fun `a route change to headphones closes at once`() {
        val start = now
        play()
        advance(10)
        play(onSpeaker = false)

        assertEquals(listOf(ContextRange(RangeKind.MEDIA, Route.SPEAKER, start, start + 10_000)), ranges)
        assertNull(tracker.pendingCloseAtMs)
    }

    @Test
    fun `a pending close asks for the timer at the end of the grace`() {
        play()
        advance(10)
        play(emptySet())

        assertEquals(now + 2_000, tracker.pendingCloseAtMs)
    }

    @Test
    fun `a 2 s blip is dropped`() {
        play()
        advance(2)
        play(emptySet())
        advance(3)

        assertTrue(ranges.isEmpty())
    }

    @Test
    fun `a call that switches to the speaker after 5 s is a speaker call when the speaker is held longest`() {
        val start = now
        tracker.onCommunicationDevice(Route.EARPIECE)
        tracker.onMode(Mode.IN_COMMUNICATION)
        advance(5)
        tracker.onCommunicationDevice(Route.SPEAKER)
        advance(60)
        tracker.onMode(Mode.NORMAL)

        assertEquals(listOf(ContextRange(RangeKind.CALL, Route.SPEAKER, start, start + 65_000)), ranges)
    }

    @Test
    fun `a call that mostly stays on the earpiece is an earpiece call`() {
        val start = now
        tracker.onCommunicationDevice(Route.EARPIECE)
        tracker.onMode(Mode.IN_CALL)
        advance(60)
        tracker.onCommunicationDevice(Route.SPEAKER)
        advance(5)
        tracker.onMode(Mode.NORMAL)

        assertEquals(listOf(ContextRange(RangeKind.CALL, Route.EARPIECE, start, start + 65_000)), ranges)
    }

    @Test
    fun `every call mode opens a range`() {
        listOf(Mode.IN_CALL, Mode.IN_COMMUNICATION, Mode.CALL_REDIRECT, Mode.COMMUNICATION_REDIRECT).forEach {
            ranges.clear()
            tracker.onMode(it)
            advance(10)
            tracker.onMode(Mode.NORMAL)
            assertEquals(it.name, 1, ranges.size)
        }
    }

    @Test
    fun `a ringtone mode is not a call`() {
        tracker.onMode(Mode.OTHER)
        advance(10)
        tracker.onMode(Mode.NORMAL)

        assertTrue(ranges.isEmpty())
    }

    @Test
    fun `stop closes an open media range and an open call`() {
        val start = now
        play()
        tracker.onMode(Mode.IN_CALL)
        advance(10)
        tracker.onStop()

        assertEquals(
            setOf(
                ContextRange(RangeKind.MEDIA, Route.SPEAKER, start, start + 10_000),
                ContextRange(RangeKind.CALL, Route.OTHER, start, start + 10_000),
            ),
            ranges.toSet(),
        )
    }

    @Test
    fun `stop in the grace ends the range where the sound stopped`() {
        val start = now
        play()
        advance(10)
        play(emptySet())
        advance(1)
        tracker.onStop()

        assertEquals(listOf(ContextRange(RangeKind.MEDIA, Route.SPEAKER, start, start + 10_000)), ranges)
    }

    @Test
    fun `a range over 12 h is cut at 12 h`() {
        val start = now
        play()
        advance(13 * 3_600)
        tracker.onStop()

        assertEquals(listOf(ContextRange(RangeKind.MEDIA, Route.SPEAKER, start, start + 12 * 3_600_000)), ranges)
    }
}
