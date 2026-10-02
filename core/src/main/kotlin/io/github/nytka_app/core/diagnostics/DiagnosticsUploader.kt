package io.github.nytka_app.core.diagnostics

import io.github.nytka_app.core.api.DiagnosticsClient
import io.github.nytka_app.core.api.DiagnosticsResult
import io.github.nytka_app.core.settings.SettingsSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Sends samples and log events to the user's own server, and only while the "Send diagnostics to my server" switch is
 * on: the switch is read again before every page, so turning it off stops the next request.
 */
class DiagnosticsUploader(
    private val source: DiagnosticsSource,
    private val client: DiagnosticsClient,
    private val settings: SettingsSource,
    private val intervalMs: Long = INTERVAL_MS,
) {
    private val lock = Mutex()
    private val mutableNote = MutableStateFlow<String?>(null)

    /** Why the switch turned itself off, until the user turns it on again. */
    val note: StateFlow<String?> = mutableNote.asStateFlow()

    fun clearNote() {
        mutableNote.value = null
    }

    /**
     * Uploads pages of 500, oldest first, until none are left or one does not go through; returns how many went.
     * A page the server rejects as too large is halved; one it cannot read is halved down to the sample at fault,
     * which is dropped. Only network errors and 5xx keep the same samples for the next round.
     */
    suspend fun flush(): Int =
        lock.withLock {
            var sent = 0
            var limit = PAGE_SIZE
            while (settings.current().diagnosticsUpload) {
                when (val step = step(limit)) {
                    is Step.Sent -> {
                        sent += step.count
                        limit = PAGE_SIZE
                    }

                    is Step.Shrink -> limit = step.limit
                    Step.Dropped -> Unit
                    Step.Stop -> break
                }
            }
            sent
        }

    private sealed interface Step {
        data class Sent(
            val count: Int,
        ) : Step

        data class Shrink(
            val limit: Int,
        ) : Step

        data object Dropped : Step

        data object Stop : Step
    }

    private suspend fun step(limit: Int): Step {
        val rows = source.pending(limit)
        if (rows.isEmpty()) return Step.Stop
        val page = fit(rows)
        // Even alone it does not fit the server's limit: nothing can ever send it.
        if (page.isEmpty()) return drop(rows.first())
        return when (client.uploadDiagnostics(page.joinToString(",", "[", "]") { it.json })) {
            is DiagnosticsResult.Accepted -> {
                source.markUploaded(page.map { it.id })
                Step.Sent(page.size)
            }

            DiagnosticsResult.BadRequest, DiagnosticsResult.TooLarge ->
                if (page.size == 1) drop(page.single()) else Step.Shrink(page.size / 2)

            DiagnosticsResult.NotSupported -> {
                settings.update { it.copy(diagnosticsUpload = false) }
                mutableNote.value = NOT_SUPPORTED_NOTE
                Step.Stop
            }
            // Unauthorized, not configured or a failure: the same samples go again next round.
            else -> Step.Stop
        }
    }

    /** Marks a sample nobody can send as uploaded, so it stops blocking the ones behind it. */
    private suspend fun drop(row: DiagnosticRow): Step {
        source.markUploaded(listOf(row.id))
        return Step.Dropped
    }

    /** Every [intervalMs] for as long as the caller's scope lives. */
    suspend fun run() {
        while (true) {
            flushSafely()
            delay(intervalMs)
        }
    }

    /** A database error must not end the loop, or the capture service's shutdown. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend fun flushSafely() {
        try {
            flush()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Retried next round; the samples stay in Room.
        }
    }

    /** Keeps the body under the server's limit even if long device names make a page of 500 too big. */
    private fun fit(rows: List<DiagnosticRow>): List<DiagnosticRow> {
        var bytes = 2
        return rows.takeWhile {
            bytes += it.json.toByteArray().size + 1
            bytes <= MAX_BODY_BYTES
        }
    }

    companion object {
        const val PAGE_SIZE = 500
        const val INTERVAL_MS = 60 * 1000L
        const val MAX_BODY_BYTES = 256 * 1024
        const val NOT_SUPPORTED_NOTE = "Your server does not accept diagnostics (needs server 0.2.0)"
    }
}
