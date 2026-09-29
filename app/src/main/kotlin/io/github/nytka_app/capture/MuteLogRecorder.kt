package io.github.nytka_app.capture

import android.database.SQLException
import io.github.nytka_app.core.diagnostics.EventLog
import io.github.nytka_app.core.ring.StoredSink
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the mute log the sync filters stored audio with: the time of every change of the mute setting, from the
 * button, the notification and the Device tab. A value equal to the last one logged is not a change, so it is safe
 * to call from every place that sees the setting. A mute stays open until the next change, so it covers a whole
 * absence of the phone.
 */
class MuteLogRecorder(
    private val sink: StoredSink,
    private val now: () -> Long = System::currentTimeMillis,
    private val log: EventLog = EventLog.Logcat,
) {
    private val lock = Mutex()
    private var last: Boolean? = null

    suspend fun record(muted: Boolean) =
        lock.withLock {
            try {
                val known = last ?: (sink.muteChanges().lastOrNull()?.muted ?: false)
                last = known
                if (known == muted) return@withLock
                sink.recordMute(now(), muted)
                last = muted
            } catch (e: SQLException) {
                log.w(TAG, "The mute log refused a write: ${e.javaClass.simpleName}")
            }
        }

    private companion object {
        const val TAG = "MuteLogRecorder"
    }
}
