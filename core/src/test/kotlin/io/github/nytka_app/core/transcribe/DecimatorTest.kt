package io.github.nytka_app.core.transcribe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class DecimatorTest {
    private fun tone(
        hz: Double,
        samples: Int,
        rate: Int = 48_000,
    ) = FloatArray(samples) { (0.5 * sin(2 * PI * hz * it / rate)).toFloat() }

    private fun rms(values: FloatArray) = sqrt(values.map { it * it }.average())

    @Test
    fun `a 20 ms frame at 48 kHz becomes 320 samples`() {
        val decimator = Decimator(3)

        repeat(5) { assertEquals(320, decimator.process(FloatArray(960)).size) }
    }

    @Test
    fun `odd block sizes add up to one sample in three`() {
        val decimator = Decimator(3)

        val total = listOf(7, 5, 11, 1, 2, 960).sumOf { decimator.process(FloatArray(it)).size }

        // One output per input index divisible by 3, counted from the first sample.
        assertEquals((7 + 5 + 11 + 1 + 2 + 960 + 2) / 3, total)
    }

    @Test
    fun `speech frequencies pass and frequencies above 8 kHz are filtered out`() {
        val speech = Decimator(3).process(tone(1_000.0, 9_600)).drop(100).toFloatArray()
        val high = Decimator(3).process(tone(12_000.0, 9_600)).drop(100).toFloatArray()

        assertEquals(0.5 / sqrt(2.0), rms(speech), 0.02)
        assertTrue("high band leaked: ${rms(high)}", rms(high) < 0.02)
    }

    @Test
    fun `frames joined one by one match one long block`() {
        val signal = tone(440.0, 960 * 4)
        val whole = Decimator(3).process(signal)
        val pieces = Decimator(3)
        val joined = (0 until 4).flatMap { pieces.process(signal.copyOfRange(it * 960, (it + 1) * 960)).toList() }

        assertEquals(whole.size, joined.size)
        whole.indices.forEach { assertTrue(abs(whole[it] - joined[it]) < 1e-5f) }
    }

    @Test
    fun `factor one passes audio through`() {
        val input = floatArrayOf(0.1f, 0.2f, 0.3f)

        assertEquals(input.toList(), Decimator(1).process(input).toList())
    }
}
