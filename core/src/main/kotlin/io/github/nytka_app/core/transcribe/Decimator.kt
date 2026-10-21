package io.github.nytka_app.core.transcribe

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Brings decoded audio down to 16 kHz by a whole [factor] (3 for Android's Opus decoder, which always answers at
 * 48 kHz), behind a windowed-sinc low-pass so nothing above 8 kHz folds back into speech. Keeps its history between
 * calls, so 20 ms frames join without clicks; [reset] before audio that does not follow.
 */
class Decimator(
    private val factor: Int,
) {
    private val taps = lowPass(factor)
    private val history = FloatArray(taps.size - 1)
    private var phase = 0

    init {
        require(factor >= 1) { "factor must be at least 1" }
    }

    fun process(input: FloatArray): FloatArray {
        if (factor == 1) return input
        val joined = history + input
        val first = (factor - phase) % factor
        val out = FloatArray(if (first >= input.size) 0 else (input.size - 1 - first) / factor + 1)
        var n = 0
        var i = first
        while (i < input.size) {
            var sum = 0f
            val end = i + taps.size - 1
            for (k in taps.indices) sum += taps[k] * joined[end - k]
            out[n++] = sum
            i += factor
        }
        phase = (phase + input.size) % factor
        joined.copyInto(history, 0, joined.size - history.size)
        return out
    }

    fun reset() {
        history.fill(0f)
        phase = 0
    }

    private companion object {
        const val TAPS_PER_FACTOR = 16

        fun lowPass(factor: Int): FloatArray {
            if (factor == 1) return floatArrayOf(1f)
            val size = TAPS_PER_FACTOR * factor + 1
            // Cut a little below the new Nyquist frequency, so the transition band ends before it.
            val cutoff = 0.45 / factor
            val middle = (size - 1) / 2.0
            val taps =
                FloatArray(size) { n ->
                    val x = n - middle
                    val sinc = if (x == 0.0) 2 * cutoff else sin(2 * PI * cutoff * x) / (PI * x)
                    val window = 0.54 - 0.46 * cos(2 * PI * n / (size - 1))
                    (sinc * window).toFloat()
                }
            val gain = taps.sum()
            return FloatArray(size) { taps[it] / gain }
        }
    }
}
