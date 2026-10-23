package io.github.nytka_app.core.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GainRangesTest {
    private var now = 1_000_000L
    private val ranges = mutableListOf<ContextRange>()
    private val gain = GainRanges({ now }, { ranges += it })

    private fun advance(seconds: Long) {
        now += seconds * 1_000
    }

    @Test
    fun `a change of level closes one range and opens the next`() {
        val start = now
        gain.onGain(3)
        advance(60)
        gain.onGain(3)
        gain.onGain(5)
        advance(30)
        gain.onStop()

        assertEquals(
            listOf(
                ContextRange(RangeKind.GAIN, Route.OTHER, start, start + 60_000, "3"),
                ContextRange(RangeKind.GAIN, Route.OTHER, start + 60_000, start + 90_000, "5"),
            ),
            ranges,
        )
    }

    @Test
    fun `the pendant going away ends the range and an unknown level opens none`() {
        gain.onGain(null)
        advance(10)
        val start = now
        gain.onGain(4)
        advance(10)
        gain.onGain(null)
        advance(10)
        gain.onStop()

        assertEquals(listOf(ContextRange(RangeKind.GAIN, Route.OTHER, start, start + 10_000, "4")), ranges)
    }

    @Test
    fun `a day-long stretch is sent as 12 h pieces`() {
        val start = now
        gain.onGain(3)
        advance(25 * 3_600)
        gain.onStop()

        val hours = 3_600_000L
        assertEquals(
            listOf(
                ContextRange(RangeKind.GAIN, Route.OTHER, start, start + 12 * hours, "3"),
                ContextRange(RangeKind.GAIN, Route.OTHER, start + 12 * hours, start + 24 * hours, "3"),
                ContextRange(RangeKind.GAIN, Route.OTHER, start + 24 * hours, start + 25 * hours, "3"),
            ),
            ranges,
        )
    }

    @Test
    fun `a level held under 3 s is dropped`() {
        gain.onGain(3)
        advance(2)
        gain.onGain(4)
        advance(2)
        gain.onStop()

        assertTrue(ranges.isEmpty())
    }
}
