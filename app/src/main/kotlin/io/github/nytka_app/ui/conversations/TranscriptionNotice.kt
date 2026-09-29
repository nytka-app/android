package io.github.nytka_app.ui.conversations

import io.github.nytka_app.core.api.ServerStatus
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

private const val FAILED = "Some speech could not be transcribed."
private const val MAX_ERROR_LENGTH = 120

/** The server waits up to 10 minutes for a missing chunk before it goes on, so audio older than this is stuck. */
private val BEHIND_AFTER = Duration.ofMinutes(15)

/**
 * What the status card says about transcription, from `GET /api/v1/status`, or null when the server reports nothing
 * wrong. `lastError` is the error of the server's latest failed batch, and the server keeps it until that batch's
 * conversation is deleted, so the text says "could not", not "cannot". Failed batches outrank a backlog, which is
 * audio the server has held since [ServerStatus.oldestPendingAt]: with the transcription endpoint down or slow it
 * piles up behind the batches waiting to be sent.
 */
fun transcriptionNotice(
    status: ServerStatus,
    now: Instant,
    zone: ZoneId,
): String? {
    status.lastError?.trim()?.let { error ->
        return if (error.isEmpty()) FAILED else "$FAILED ${error.shortened()}"
    }
    val since = status.oldestPendingAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
    if (Duration.between(since, now) < BEHIND_AFTER) return null
    val chunks = if (status.pendingChunks == 1L) "1 chunk has" else "${status.pendingChunks} chunks have"
    return "The server is behind: $chunks waited since ${Formatting.clock(since, zone)}."
}

private fun String.shortened() = if (length <= MAX_ERROR_LENGTH) this else take(MAX_ERROR_LENGTH - 1) + "…"
