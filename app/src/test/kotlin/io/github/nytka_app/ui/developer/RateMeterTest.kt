package io.github.nytka_app.ui.developer

import org.junit.Assert.assertEquals
import org.junit.Test

class RateMeterTest {
    @Test
    fun `counts per second over a one second window`() {
        val meter = RateMeter()

        assertEquals(
            listOf(0.0, 0.0, 50.0, 50.0, 0.0),
            listOf(0L to 0L, 25L to 500L, 50L to 1_000L, 100L to 2_000L, 3L to 2_500L).map { (count, at) ->
                meter.sample(count, at)
            },
        )
    }
}
