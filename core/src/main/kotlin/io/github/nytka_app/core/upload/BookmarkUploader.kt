package io.github.nytka_app.core.upload

import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.BookmarksClient
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.queue.BookmarkSource
import io.github.nytka_app.core.settings.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sends the bookmark outbox to the server, oldest first, with the chunk backoff. Only an answer of 200 or 201
 * removes a bookmark: a lost answer makes the retry a 200, since the server keeps the client's id.
 */
class BookmarkUploader(
    private val source: BookmarkSource,
    private val client: BookmarksClient,
    private val settings: Flow<Settings>,
    private val backoff: Backoff = Backoff(),
) {
    private val lock = Mutex()
    private var failures = 0

    /**
     * Uploads until the outbox is empty or one does not go through. A 400 means the server can never take the
     * bookmark, so it is dropped; a 401, 403 or a missing setup pauses; anything else waits for the backoff.
     */
    suspend fun drain(): DrainResult = lock.withLock { drainLocked() }

    private suspend fun drainLocked(): DrainResult {
        while (true) {
            val row = source.oldest() ?: return DrainResult.Empty
            when (val result = client.createBookmark(row.id, row.atMs, row.source)) {
                is ApiResult.Ok -> {
                    source.remove(row.id)
                    failures = 0
                }
                is ApiResult.Failure ->
                    when (result.kind) {
                        FailureKind.Invalid -> source.remove(row.id)
                        FailureKind.Unauthorized, FailureKind.Forbidden, FailureKind.NotConfigured ->
                            return DrainResult.Paused(result.message)
                        else -> return DrainResult.Failed(backoff.delayMs(++failures))
                    }
            }
        }
    }

    /** Uploads for as long as the caller's scope lives. */
    suspend fun run() {
        while (true) {
            when (val result = drainSafely()) {
                DrainResult.Empty -> withTimeoutOrNull(RECHECK_MS) { source.count.first { it > 0 } }
                is DrainResult.Failed -> delay(result.retryAfterMs)
                is DrainResult.Paused -> {
                    val paused = settings.first().connectionKey
                    settings.first { it.connectionKey != paused }
                }
            }
        }
    }

    /** A database error retries later instead of ending the loop. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun drainSafely(): DrainResult =
        try {
            drain()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            DrainResult.Failed(backoff.delayMs(++failures))
        }

    private companion object {
        const val RECHECK_MS = 60_000L
    }
}
