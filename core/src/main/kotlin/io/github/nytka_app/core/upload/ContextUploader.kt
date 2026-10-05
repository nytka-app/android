package io.github.nytka_app.core.upload

import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.ContextClient
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.queue.ContextSource
import io.github.nytka_app.core.settings.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Sends the phone-context outbox to the server in batches of up to 500, oldest first, every 60 s while the
 * "Phone context" switch is on and `/info` lists `context-ranges`. A range leaves the outbox only when the server
 * answers 200; a lost answer makes the retry count as skipped, since the server keeps the client's id.
 */
class ContextUploader(
    private val source: ContextSource,
    private val client: ContextClient,
    private val info: InfoClient,
    private val settings: Flow<Settings>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val backoff: Backoff = Backoff(),
) {
    private val lock = Mutex()
    private var failures = 0

    /**
     * Uploads until the outbox is empty or one batch does not go through. A 400 means the server can never take
     * the batch, so it is dropped; a 401, 403, 404, 405 or a missing setup pauses; anything else waits for the
     * backoff. With the switch off, or on a server without the feature, nothing is sent.
     */
    suspend fun drain(): DrainResult = lock.withLock { drainLocked() }

    private suspend fun drainLocked(): DrainResult {
        source.removeEndedBefore(clock() - KEEP_MS)
        if (!settings.first().phoneContext) return DrainResult.Empty
        var batch = source.oldest(BATCH)
        if (batch.isEmpty()) return DrainResult.Empty
        when (val result = info.info()) {
            is ApiResult.Ok -> if (!result.value.has(ServerInfo.FEATURE_CONTEXT_RANGES)) return DrainResult.Empty
            is ApiResult.Failure -> return failed(result)
        }
        while (batch.isNotEmpty()) {
            when (val result = client.sendRanges(batch)) {
                is ApiResult.Ok -> failures = 0
                is ApiResult.Failure ->
                    // A 400 is the server refusing these ranges for good; keeping them would block the rest.
                    if (result.kind != FailureKind.Invalid) return failed(result)
            }
            source.remove(batch.map { it.id })
            batch = source.oldest(BATCH)
        }
        return DrainResult.Empty
    }

    private fun failed(result: ApiResult.Failure): DrainResult =
        when (result.kind) {
            FailureKind.Unauthorized,
            FailureKind.Forbidden,
            FailureKind.NotConfigured,
            FailureKind.NotFound,
            FailureKind.Unsupported,
            -> DrainResult.Paused(result.message)

            else -> DrainResult.Failed(backoff.delayMs(++failures))
        }

    /** Uploads for as long as the caller's scope lives. */
    suspend fun run() {
        while (true) {
            when (val result = drainSafely()) {
                DrainResult.Empty -> delay(TICK_MS)
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

    companion object {
        const val BATCH = 500
        const val TICK_MS = 60_000L
        const val KEEP_MS = 7L * 24 * 60 * 60 * 1_000
    }
}
