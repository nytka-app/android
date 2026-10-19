package io.github.nytka_app.ui.conversations

import io.github.nytka_app.core.api.ServerStatus
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** The server waits up to 10 minutes for a missing chunk before it goes on, so audio older than this is stuck. */
private val BEHIND_AFTER = Duration.ofMinutes(15)

/** How far behind the server is: [chunks] waiting since the clock time [since]; the card words it. */
data class TranscriptionLag(
    val chunks: Long,
    val since: String,
)

/**
 * What the status card says about transcription, from `GET /api/v1/status`, or null when the server reports nothing
 * wrong: audio the server has held since [ServerStatus.oldestPendingAt]. With the transcription endpoint down or slow
 * it piles up behind the batches waiting to be sent.
 *
 * There is no branch for `lastError` yet. It is the error of the newest failed batch whatever came after, and nothing
 * clears it, so one failure would leave a red line on the card for good. The branch returns once `/status` has
 * `lastErrorAt` (the server's v0.2 spec, docs/specs/v0.2.md), where `lastError` is current trouble only.
 */
fun transcriptionNotice(
    status: ServerStatus,
    now: Instant,
    zone: ZoneId,
): TranscriptionLag? {
    val since = status.oldestPendingAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
    if (Duration.between(since, now) < BEHIND_AFTER) return null
    return TranscriptionLag(status.pendingChunks, Formatting.clock(since, zone))
}
