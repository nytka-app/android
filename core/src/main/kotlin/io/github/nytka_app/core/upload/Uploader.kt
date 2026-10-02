package io.github.nytka_app.core.upload

import io.github.nytka_app.core.api.UploadClient
import io.github.nytka_app.core.api.UploadResult
import io.github.nytka_app.core.settings.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

data class UploadState(
    val lastUploadAtMs: Long? = null,
    val lastResult: String? = null,
    val paused: String? = null,
    val failures: Int = 0,
    val unreachableSinceMs: Long? = null,
    val uploadedChunks: Long = 0,
    val droppedChunks: Long = 0,
    val parkedChunks: Long = 0,
)

sealed interface DrainResult {
    data object Empty : DrainResult

    data class Paused(
        val reason: String,
    ) : DrainResult

    data class Failed(
        val retryAfterMs: Long,
    ) : DrainResult
}

class Uploader(
    private val chunks: ChunkSource,
    private val client: UploadClient,
    private val settings: Flow<Settings>,
    private val now: () -> Long = System::currentTimeMillis,
    private val backoff: Backoff = Backoff(),
) {
    private val lock = Mutex()
    private val mutableState = MutableStateFlow(UploadState())

    val state: StateFlow<UploadState> = mutableState.asStateFlow()

    /**
     * Uploads chunks in the order [ChunkSource.oldest] gives them (live before stored, each oldest first) until
     * the queue is empty or an upload does not go through. Only a 200 or 202 removes a stored chunk; one the
     * server refuses for good is parked.
     */
    suspend fun drain(): DrainResult = lock.withLock { drainLocked() }

    private suspend fun drainLocked(): DrainResult {
        while (true) {
            val chunk = chunks.oldest() ?: return DrainResult.Empty
            when (val result = client.upload(chunk.body)) {
                is UploadResult.Accepted -> {
                    chunks.remove(chunk.id)
                    val stored = "202: stored through ${result.throughSeq}"
                    mutableState.update {
                        it.copy(
                            lastUploadAtMs = now(),
                            lastResult = if (result.duplicate) "200: already stored" else stored,
                            paused = null,
                            failures = 0,
                            unreachableSinceMs = null,
                            uploadedChunks = it.uploadedChunks + 1,
                        )
                    }
                }

                is UploadResult.Dropped -> {
                    if (chunk.stored) chunks.park(chunk.id, result.code, result.reason) else chunks.remove(chunk.id)
                    mutableState.update {
                        it.copy(
                            lastResult = "${result.code}: dropped the chunk from frame ${chunk.firstSeq}",
                            paused = null,
                            failures = 0,
                            unreachableSinceMs = null,
                            droppedChunks = if (chunk.stored) it.droppedChunks else it.droppedChunks + 1,
                            parkedChunks = if (chunk.stored) it.parkedChunks + 1 else it.parkedChunks,
                        )
                    }
                }

                UploadResult.Unauthorized -> return pause("The server refused the token.")
                is UploadResult.NotConfigured -> return pause(result.reason)
                is UploadResult.Retry -> {
                    val failures = mutableState.value.failures + 1
                    mutableState.update {
                        it.copy(
                            lastResult = result.reason,
                            paused = null,
                            failures = failures,
                            unreachableSinceMs = it.unreachableSinceMs ?: now(),
                        )
                    }
                    return DrainResult.Failed(backoff.delayMs(failures))
                }
            }
        }
    }

    /** Uploads for as long as the caller's scope lives. */
    suspend fun run() {
        while (true) {
            when (val result = drainSafely()) {
                DrainResult.Empty -> {
                    // The usage count can lag the table (it refreshes on seal and remove): pause briefly so a
                    // stale non-zero count cannot spin the loop, and look again after a minute regardless.
                    delay(EMPTY_PAUSE_MS)
                    withTimeoutOrNull(EMPTY_RECHECK_MS) { chunks.usage.first { it.chunks > 0 } }
                }

                is DrainResult.Failed -> delay(result.retryAfterMs)
                is DrainResult.Paused -> {
                    val paused = settings.first().connectionKey
                    settings.first { it.connectionKey != paused }
                }
            }
        }
    }

    /** An unexpected failure (a malformed token header, a database error) retries later instead of ending the loop. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun drainSafely(): DrainResult =
        try {
            drain()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mutableState.update { it.copy(lastResult = "Upload failed: ${e.javaClass.simpleName}") }
            DrainResult.Failed(backoff.delayMs(mutableState.value.failures + 1))
        }

    private fun pause(reason: String): DrainResult {
        mutableState.update { it.copy(paused = reason, lastResult = reason) }
        return DrainResult.Paused(reason)
    }

    private companion object {
        const val EMPTY_PAUSE_MS = 1_000L
        const val EMPTY_RECHECK_MS = 60_000L
    }
}
