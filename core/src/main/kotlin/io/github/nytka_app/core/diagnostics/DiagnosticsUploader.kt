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
 * Sends samples to the user's own server, and only while the "Send diagnostics to my server" switch is
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

    /** Uploads pages of 500, oldest first, until none are left or one does not go through; returns how many went. */
    suspend fun flush(): Int =
        lock.withLock {
            var sent = 0
            while (settings.current().diagnosticsUpload) {
                val page = fit(source.pending(PAGE_SIZE))
                if (page.isEmpty() || !send(page)) break
                sent += page.size
            }
            sent
        }

    /** True when the server took the page; a page that did not go through stays for the next round. */
    private suspend fun send(page: List<DiagnosticRow>): Boolean =
        when (client.uploadDiagnostics(page.joinToString(",", "[", "]") { it.json })) {
            is DiagnosticsResult.Accepted -> {
                source.markUploaded(page.map { it.id })
                true
            }
            DiagnosticsResult.NotSupported -> {
                settings.update { it.copy(diagnosticsUpload = false) }
                mutableNote.value = NOT_SUPPORTED_NOTE
                false
            }
            // Unauthorized, not configured or a failure: the same samples go again next round.
            else -> false
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
        const val INTERVAL_MS = 5 * 60 * 1000L
        const val MAX_BODY_BYTES = 256 * 1024
        const val NOT_SUPPORTED_NOTE = "Your server does not accept diagnostics (needs server 0.2.0)"
    }
}
