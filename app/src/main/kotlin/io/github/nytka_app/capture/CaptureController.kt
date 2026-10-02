package io.github.nytka_app.capture

import android.database.SQLException
import io.github.nytka_app.core.diagnostics.EventLog
import io.github.nytka_app.core.queue.BookmarkOutbox
import io.github.nytka_app.core.queue.BookmarkSink
import io.github.nytka_app.core.queue.FrameSink
import io.github.nytka_app.core.settings.MuteSchedule
import io.github.nytka_app.core.settings.SettingsStore
import io.github.nytka_app.pendant.ButtonEvent
import io.github.nytka_app.pendant.Haptic
import io.github.nytka_app.pendant.LinkStats
import io.github.nytka_app.pendant.Pendant
import io.github.nytka_app.pendant.PendantConnection
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

interface CaptureSettings {
    val muted: Flow<Boolean>

    /** The weekly windows that mute like [muted]; none unless a setting provides them. */
    val schedule: Flow<MuteSchedule> get() = flowOf(MuteSchedule())

    suspend fun setMuted(muted: Boolean)
}

class StoreCaptureSettings(
    private val store: SettingsStore,
) : CaptureSettings {
    override val muted: Flow<Boolean> = store.settings.map { it.muted }.distinctUntilChanged()
    override val schedule: Flow<MuteSchedule> = store.settings.map { it.muteSchedule }.distinctUntilChanged()

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
    private val muteLog: MuteLogRecorder? = null,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val bookmarks: BookmarkSink? = null,
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
    @Volatile
    private var muted = true

    /** What the pendant was last told about audio; null before the first call of a start. */
    private var intent: Boolean? = null

    /**
     * The manual mute or a schedule window: what decides the pendant's audio. The schedule is checked against [now]
     * in the phone's current zone and again at least every minute, so a change of zone or clock is picked up.
     */
    private val effectiveMuted: Flow<Boolean> =
        combine(settings.muted, scheduleMuted()) { manual, scheduled -> manual || scheduled }.distinctUntilChanged()

    private fun scheduleMuted(): Flow<Boolean> =
        settings.schedule.flatMapLatest { schedule ->
            flow {
                while (true) {
                    val at = Instant.ofEpochMilli(now()).atZone(zone())
                    emit(schedule.mutedAt(at))
                    val wait = schedule.untilChange(at)?.toMillis() ?: SCHEDULE_RECHECK_MS
                    delay(minOf(wait, SCHEDULE_RECHECK_MS))
                }
            }
        }

    private suspend fun setIntent(audio: Boolean) {
        intent = audio
        pendant.setAudio(audio)
    }

    fun start(address: String) {
        if (jobs != null) return
        session = UUID.randomUUID()
        nextSeq = 0
        intent = null
        val job = SupervisorJob(scope.coroutineContext[Job])
        jobs = job
        val inner = CoroutineScope(scope.coroutineContext + job)
        mutableStatus.value = CaptureStatus(running = true, session = session.toString(), disconnectedSinceMs = now())

        inner.launch {
            val inputs = combine(pendant.connection, effectiveMuted) { connection, isMuted -> connection to isMuted }
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
                // The pendant keeps the intent across a lost link, so only a change needs telling.
                if (connection is PendantConnection.Connected && intent != !isMuted) setIntent(!isMuted)
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
        muteLog?.let { recorder -> inner.launch { effectiveMuted.collectLatest { recorder.record(it) } } }
        inner.launch { sortTaps(inner) }
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
                stored { sink.seal(includePartialStored = false) }
            }
        }
        inner.launch { connectWithIntent(address) }
    }

    /**
     * Sorts the pendant's taps. The firmware sends a single tap about 320 ms after the press, so a slow double tap can
     * arrive as two single taps. A single tap opens a [TAP_WAIT_MS] window; a double tap or a second single tap inside
     * it is the mute gesture and toggles mute once, keeping no bookmark. A lone single tap becomes a bookmark at the
     * phone's clock when the tap arrived, kept when the window closes, muted or not. A single tap right after a double
     * tap is dropped too, for a firmware that sends the pair in the other order.
     */
    private suspend fun sortTaps(inner: CoroutineScope) {
        var window: TapWindow? = null
        var lastDoubleTapMs: Long? = null
        var lastPairMs: Long? = null
        pendant.buttons.collect { event ->
            val open = window?.open == true
            when (event) {
                ButtonEvent.DoubleTap -> {
                    window?.cancel()
                    // The same gesture reported as two single taps and then as a double tap toggles once.
                    if (lastPairMs.within(now())) return@collect
                    lastDoubleTapMs = now()
                    toggleMute(inner)
                }

                ButtonEvent.SingleTap ->
                    when {
                        open -> {
                            window?.cancel()
                            lastPairMs = now()
                            toggleMute(inner)
                        }

                        !lastDoubleTapMs.within(now()) -> {
                            val opened = TapWindow()
                            window = opened
                            opened.job = inner.launch { keep(now(), opened) }
                        }
                    }

                ButtonEvent.Release -> Unit
            }
        }
    }

    private fun Long?.within(nowMs: Long) = this != null && nowMs - this < TAP_WAIT_MS

    /** The wait after a single tap. It is [open] until a second tap cancels it or the bookmark starts to be saved. */
    private class TapWindow {
        @Volatile
        var open = true
        lateinit var job: Job

        fun cancel() {
            if (!open) return
            open = false
            job.cancel()
        }
    }

    /** Started at once, so the next tap sees the new state; the buzz waits inside its own coroutine. */
    private fun toggleMute(inner: CoroutineScope) {
        val wanted = !muted
        inner.launch(start = CoroutineStart.UNDISPATCHED) { setMuted(wanted, MuteSource.PendantDoubleTap) }
    }

    /** Once the wait is over the window closes, so a tap during the write starts a gesture of its own. */
    private suspend fun keep(
        atMs: Long,
        window: TapWindow,
    ) {
        delay(TAP_WAIT_MS)
        window.open = false
        val outbox = bookmarks ?: return
        val saved =
            withContext(NonCancellable) {
                stored { outbox.add(UUID.randomUUID().toString(), atMs, BookmarkOutbox.SOURCE_PENDANT) }
            }
        if (!saved) return
        log.i(TAG, "bookmark kept")
        if (pendant.connection.value is PendantConnection.Connected) pendant.buzz(Haptic.Short)
    }

    /**
     * The audio intent comes from the persisted setting before the link exists, so a service that starts as the
     * phone comes back subscribes inside setUp instead of after Connected; until then the pendant drops frames.
     */
    private suspend fun connectWithIntent(address: String) {
        val isMuted = effectiveMuted.first()
        muted = isMuted
        setIntent(!isMuted)
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
        const val TAP_WAIT_MS = 700L
        const val SCHEDULE_RECHECK_MS = 60_000L
    }
}
