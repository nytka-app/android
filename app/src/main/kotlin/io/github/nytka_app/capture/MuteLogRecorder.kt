package io.github.nytka_app.capture

import android.database.SQLException
import io.github.nytka_app.core.diagnostics.EventLog
import io.github.nytka_app.core.ring.StoredSink
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the mute log the sync filters stored audio with: the time of every change of the mute setting, from the
 * button, the notification and the Device tab. A value equal to the last one logged is not a change, so it is safe
 * to call from every place that sees the setting. A mute stays open until the next change, so it covers a whole
 * absence of the phone.
 *
 * A write the database refuses is retried with backoff until it lands: the setting emits only on a change, so
 * giving up would leave the mute unrecorded and its audio uploaded. A newer call supersedes an older retry.
 */
class MuteLogRecorder(
    private val sink: StoredSink,
    private val now: () -> Long = System::currentTimeMillis,
    private val log: EventLog = EventLog.Logcat,
    private val retryMs: Long = RETRY_MS,
) {
    private val lock = Mutex()
    private var last: Boolean? = null

    @Volatile private var latest: Boolean? = null

    suspend fun record(muted: Boolean) {
        val at = now()
        latest = muted
        var wait = retryMs
        while (latest == muted && !tryRecord(at, muted)) {
            delay(wait)
            wait = minOf(wait * 2, MAX_RETRY_MS)
        }
    }

    private suspend fun tryRecord(
        at: Long,
        muted: Boolean,
    ): Boolean =
        lock.withLock {
            try {
                val known = last ?: (sink.muteChanges().lastOrNull()?.muted ?: false)
                last = known
                if (known != muted) {
                    sink.recordMute(at, muted)
                    last = muted
                }
                true
            } catch (e: SQLException) {
                log.w(TAG, "The mute log refused a write, trying again: ${e.javaClass.simpleName}")
                false
            }
        }

    private companion object {
        const val TAG = "MuteLogRecorder"
        const val RETRY_MS = 1_000L
        const val MAX_RETRY_MS = 60_000L
    }
}
