package io.github.nytka_app.capture

import android.database.SQLException
import io.github.nytka_app.core.diagnostics.EventLog
import io.github.nytka_app.core.queue.FrameSink
import io.github.nytka_app.core.settings.SettingsStore
import io.github.nytka_app.pendant.ButtonEvent
import io.github.nytka_app.pendant.Haptic
import io.github.nytka_app.pendant.LinkStats
import io.github.nytka_app.pendant.Pendant
import io.github.nytka_app.pendant.PendantConnection
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

interface CaptureSettings {
    val muted: Flow<Boolean>

    suspend fun setMuted(muted: Boolean)
}

class StoreCaptureSettings(
    private val store: SettingsStore,
) : CaptureSettings {
    override val muted: Flow<Boolean> = store.settings.map { it.muted }.distinctUntilChanged()

    override suspend fun setMuted(muted: Boolean) = store.update { it.copy(muted = muted) }
}

data class CaptureStatus(
    val running: Boolean = false,
    val connection: PendantConnection = PendantConnection.Disconnected,
    val muted: Boolean = false,
    val battery: Int? = null,
    val stats: LinkStats = LinkStats(),
    val session: String? = null,
    val framesQueued: Long = 0,
    val disconnectedSinceMs: Long? = null,
) {
    val recording: Boolean get() = running && !muted && connection is PendantConnection.Connected
}

/** A frame as it entered the queue, for developer-mode fixtures. */
class CapturedFrame(
    val session: UUID,
    val seq: Long,
    val capturedAtMs: Long,
    val payload: ByteArray,
)

/** Who asked for a mute change. It goes to the log with the change, so a stretch without audio can be traced. */
enum class MuteSource(
    val label: String,
) {
    PendantDoubleTap("pendant double-tap"),
    Notification("notification action"),
    App("app UI"),
}

/**
 * One capture session: pendant frames into the queue, mute from the button or the app, sealing
 * every 30 s. The only place that turns the pendant's audio on or off.
 */
class CaptureController(
    private val pendant: Pendant,
    private val sink: FrameSink,
    private val settings: CaptureSettings,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val sealEveryMs: Long = 30_000,
    private val log: EventLog = EventLog.Logcat,
) {
    private val mutableStatus = MutableStateFlow(CaptureStatus())
    val status: StateFlow<CaptureStatus> = mutableStatus.asStateFlow()

    private val mutableCaptured = MutableSharedFlow<CapturedFrame>(extraBufferCapacity = 256)

    /** Every frame that entered the queue; nobody listens except a fixture recording. */
    val captured: SharedFlow<CapturedFrame> = mutableCaptured

    private var jobs: CompletableJob? = null
    private var session = UUID.randomUUID()
    private var nextSeq = 0L

    /** True until the settings say otherwise: nothing is recorded before the mute state is known. */
    @Volatile private var muted = true

    fun start(address: String) {
        if (jobs != null) return
        session = UUID.randomUUID()
        nextSeq = 0
        val job = SupervisorJob(scope.coroutineContext[Job])
        jobs = job
        val inner = CoroutineScope(scope.coroutineContext + job)
        mutableStatus.value = CaptureStatus(running = true, session = session.toString(), disconnectedSinceMs = now())

        inner.launch {
            val inputs = combine(pendant.connection, settings.muted) { connection, isMuted -> connection to isMuted }
            inputs.collect { (connection, isMuted) ->
                muted = isMuted
                mutableStatus.update {
                    it.copy(
                        connection = connection,
                        muted = isMuted,
                        disconnectedSinceMs =
                            when {
                                connection is PendantConnection.Connected -> null
                                else -> it.disconnectedSinceMs ?: now()
                            },
                    )
                }
                if (connection is PendantConnection.Connected) pendant.setAudio(!isMuted)
            }
        }
        inner.launch {
            pendant.frames.collect { frame ->
                // A full disk must not crash the service; the sequence advances only for stored frames.
                if (!muted && stored { sink.add(session, nextSeq, frame.capturedAtMs, frame.payload) }) {
                    nextSeq++
                    mutableCaptured.tryEmit(CapturedFrame(session, nextSeq - 1, frame.capturedAtMs, frame.payload))
                    mutableStatus.update { it.copy(framesQueued = it.framesQueued + 1) }
                }
            }
        }
        inner.launch {
            pendant.buttons
                .filter { it == ButtonEvent.DoubleTap }
                .collect { setMuted(!muted, MuteSource.PendantDoubleTap) }
        }
        inner.launch {
            pendant.battery.collect { battery ->
                mutableStatus.update { it.copy(battery = battery) }
                battery?.let { log.i(TAG, "pendant battery $it%") } // a StateFlow: one line per change
            }
        }
        inner.launch { pendant.stats.collect { stats -> mutableStatus.update { it.copy(stats = stats) } } }
        inner.launch {
            while (true) {
                delay(sealEveryMs)
                stored { sink.seal() }
            }
        }
        pendant.connect(address)
    }

    private inline fun stored(write: () -> Unit): Boolean =
        try {
            write()
            true
        } catch (e: SQLException) {
            log.w(TAG, "The frame queue refused a write: ${e.javaClass.simpleName}")
            false
        }

    suspend fun setMuted(
        muted: Boolean,
        source: MuteSource,
    ) {
        log.i(TAG, "${if (muted) "muted" else "unmuted"} by ${source.label}")
        this.muted = muted
        settings.setMuted(muted)
        if (pendant.connection.value !is PendantConnection.Connected) return
        if (muted) {
            pendant.buzz(Haptic.Long)
        } else {
            pendant.buzz(Haptic.Short)
            delay(Haptic.Short.durationMs + LIVE_BUZZ_GAP_MS)
            pendant.buzz(Haptic.Short)
        }
    }

    suspend fun stop() {
        jobs?.cancelAndJoin()
        jobs = null
        pendant.disconnect()
        sink.seal()
        mutableStatus.value = CaptureStatus()
    }

    private companion object {
        const val TAG = "CaptureController"
        const val LIVE_BUZZ_GAP_MS = 150L
    }
}
