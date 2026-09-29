package io.github.nytka_app.core.upload

/** 5 s after the first failure, doubling, never more than 5 min. */
class Backoff(
    private val firstMs: Long = 5_000,
    private val maxMs: Long = 300_000,
) {
    fun delayMs(failures: Int): Long = (firstMs shl (failures - 1).coerceIn(0, 30)).coerceAtMost(maxMs)
}
