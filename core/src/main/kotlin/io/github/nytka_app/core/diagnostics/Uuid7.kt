package io.github.nytka_app.core.diagnostics

import java.security.SecureRandom
import java.util.UUID

/** UUIDv7 (RFC 9562): 48 bits of Unix milliseconds, so ids sort by time, then random bits. */
object Uuid7 {
    private val random = SecureRandom()

    fun next(
        nowMs: Long,
        randomBits: () -> Long = random::nextLong,
    ): UUID {
        val high = (nowMs shl 16) or 0x7000L or (randomBits() and 0x0FFFL)
        val low = (randomBits() and 0x3FFF_FFFF_FFFF_FFFFL) or Long.MIN_VALUE
        return UUID(high, low)
    }
}
