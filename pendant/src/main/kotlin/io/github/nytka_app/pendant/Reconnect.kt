package io.github.nytka_app.pendant

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Whether the caller wants audio. It is the caller's intent, so a lost link does not clear it:
 * only [clear] (an explicit disconnect) and [set] change it. After a reconnect the pendant
 * subscribes again by itself when [wanted] is true.
 */
internal class AudioIntent {
    @Volatile var wanted = false
        private set

    fun set(enabled: Boolean) {
        wanted = enabled
    }

    fun clear() {
        wanted = false
    }
}

internal enum class OperationKind { Mtu, Services, Read, Write, DescriptorWrite }

/**
 * The GATT operation in flight. A callback completes it only when the kind and characteristic match,
 * so a late callback from a timed-out operation cannot finish the next one.
 */
internal class PendingOperation(
    private val kind: OperationKind,
    private val characteristic: UUID? = null,
) {
    val done = CompletableDeferred<ByteArray?>()

    fun complete(
        kind: OperationKind,
        characteristic: UUID?,
        value: ByteArray?,
    ): Boolean = (kind == this.kind && characteristic == this.characteristic) && done.complete(value)
}

/**
 * Runs one Bluetooth call and returns [fallback] if it throws. When the Bluetooth stack goes away
 * (Bluetooth switched off, its process killed) `BluetoothGatt` calls throw a `RuntimeException`
 * wrapping `DeadObjectException`, and `SecurityException` when a permission is revoked; both mean
 * the same as the call returning false, and the adapter-state path recovers the link.
 * Cancellation passes through: it is an `IllegalStateException`, so it must be rethrown.
 */
@Suppress("TooGenericExceptionCaught") // the framework wraps RemoteException in a bare RuntimeException
internal inline fun <T> gattCall(
    fallback: T,
    onError: (RuntimeException) -> Unit = {},
    call: () -> T,
): T =
    try {
        call()
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        onError(e)
        fallback
    }

/** Counts consecutive failures; [failed] is true once [limit] of them happened in a row. */
internal class FailureStreak(
    private val limit: Int,
) {
    private var count = 0

    fun failed(): Boolean = ++count >= limit

    fun succeeded() {
        count = 0
    }
}

/** Audio counts as absent after this long: the idle line and the "resumed" line share it. */
internal const val AUDIO_GAP_MS = 2_000L

/** What the watchdog wants done about a quiet link; see [LinkWatchdog]. */
internal sealed interface WatchdogAction {
    data object None : WatchdogAction

    /** Audio has been absent for [silentMs] with the link alive: say so, once per silence. */
    data class AudioIdle(
        val silentMs: Long,
    ) : WatchdogAction

    /** Toggle the audio subscription; [nextMs] is how long to wait before the resubscribe after this one. */
    data class Resubscribe(
        val silentMs: Long,
        val nextMs: Long,
    ) : WatchdogAction

    /** Nothing of any kind arrived for [quietMs]: the link is dead though Android still calls it connected. */
    data class Escalate(
        val quietMs: Long,
    ) : WatchdogAction
}

/**
 * Decides what to do about a quiet link while audio is wanted. Two silences mean different things. Audio can stop
 * on its own: in a quiet room the pendant puts its microphone to sleep and wakes it on sound, with the link up
 * (`mic.c`, `aad_track_silence`). Nothing at all can only be a dead link: the pendant notifies its battery every 5 s
 * while connected (`transport.c`, `broadcast_battery_level`; `BasedHardware/omi` at `2e34261`), so a notification of
 * any kind, audio or not, is proof of life.
 *
 * - Audio absent for [audioIdleMs]: [WatchdogAction.AudioIdle], once per silence. Nothing else happens.
 * - Audio absent for [firstResubscribeMs] with the link alive: a resubscribe, then another after twice as long, up
 *   to [maxResubscribeMs]; audio arriving starts over. It is the only cure for an audio subscription that never
 *   took (`setUp` and `setAudio` do not check), and the firmware only logs a CCC change, so it costs nothing.
 * - No notification of any kind for [deadAfterMs]: [WatchdogAction.Escalate], at most once per
 *   [escalateCooldownMs]; notifications resuming clear the cooldown.
 *
 * Call [check] only while audio is wanted: a muted pendant is silent by intent and must never reach it. The watch
 * loop owns it; only [notificationArrived] may be called from another thread.
 */
internal class LinkWatchdog(
    private val audioIdleMs: Long = AUDIO_GAP_MS,
    private val firstResubscribeMs: Long = 60_000,
    private val maxResubscribeMs: Long = 600_000,
    private val deadAfterMs: Long = 20_000,
    private val escalateCooldownMs: Long = 60_000,
) {
    private var seenAudioAtMs = NEVER
    private var anchorMs = 0L
    private var waitMs = firstResubscribeMs
    private var idleAnnounced = false
    private var escalatedAtMs = NEVER

    @Volatile private var notificationSinceCheck = false

    /** Called from the notification thread for every notification of any kind: real traffic lifts the cooldown. */
    fun notificationArrived() {
        notificationSinceCheck = true
    }

    /**
     * [lastAudioAtMs] moves on every audio notification and when audio is switched on, which starts the audio
     * clocks over. [lastAnyAtMs] moves on every notification of any kind and when the link comes up.
     */
    fun check(
        nowMs: Long,
        lastAudioAtMs: Long,
        lastAnyAtMs: Long,
    ): WatchdogAction {
        if (notificationSinceCheck) {
            notificationSinceCheck = false
            escalatedAtMs = NEVER // the reconnect worked; the cap is for escalations that brought nothing back
        }
        if (lastAudioAtMs != seenAudioAtMs) {
            seenAudioAtMs = lastAudioAtMs
            anchorMs = lastAudioAtMs
            waitMs = firstResubscribeMs
            idleAnnounced = false
        }
        val quietMs = nowMs - lastAnyAtMs
        if (quietMs >= deadAfterMs) return escalate(nowMs, quietMs)
        // From here the link is alive.
        val silentMs = nowMs - lastAudioAtMs
        if (nowMs - anchorMs >= waitMs) {
            anchorMs = nowMs
            waitMs = (waitMs * 2).coerceAtMost(maxResubscribeMs)
            return WatchdogAction.Resubscribe(silentMs, waitMs)
        }
        if (silentMs >= audioIdleMs && !idleAnnounced) {
            idleAnnounced = true
            return WatchdogAction.AudioIdle(silentMs)
        }
        return WatchdogAction.None
    }

    private fun escalate(
        nowMs: Long,
        quietMs: Long,
    ): WatchdogAction {
        if (escalatedAtMs != NEVER && nowMs - escalatedAtMs < escalateCooldownMs) return WatchdogAction.None
        escalatedAtMs = nowMs
        return WatchdogAction.Escalate(quietMs)
    }

    private companion object {
        const val NEVER = -1L
    }
}

/**
 * Says how long a silence was when audio comes back after at least [thresholdMs] of it, so a stall that ends by
 * itself still leaves a line. The clock starts when audio is switched on ([switchedOn]) and restarts with every
 * notification ([arrived]); a mute is no silence, because unmuting switches audio on again. It is armed before the
 * subscription is written, where the watchdog's `lastAudioAtMs` moves after it: a notification can beat the caller
 * back from the write, and would be measured from before the mute. Callable from any thread.
 */
internal class ResumeDetector(
    private val thresholdMs: Long = AUDIO_GAP_MS,
) {
    private val lastAtMs = AtomicLong(NEVER)

    fun switchedOn(nowMs: Long) = lastAtMs.set(nowMs)

    /** The silence this notification ended, or null when it was shorter than the threshold or nothing was armed. */
    fun arrived(nowMs: Long): Long? {
        val previousMs = lastAtMs.getAndSet(nowMs)
        return (nowMs - previousMs).takeIf { previousMs != NEVER && it >= thresholdMs }
    }

    private companion object {
        const val NEVER = -1L
    }
}

/**
 * Fires [retry] when a connection attempt is still [isConnecting] after [timeoutMs]. An unbonded
 * `connectGatt(autoConnect = true)` can wait forever without a callback; [retry] opens a new client,
 * which arms the next timeout.
 */
internal class ConnectTimeout(
    private val scope: CoroutineScope,
    private val timeoutMs: Long,
    private val isConnecting: () -> Boolean,
    private val retry: () -> Unit,
) {
    private var job: Job? = null

    @Synchronized
    fun arm() {
        job?.cancel()
        job =
            scope.launch {
                delay(timeoutMs)
                if (isConnecting()) retry()
            }
    }

    @Synchronized
    fun cancel() {
        job?.cancel()
        job = null
    }
}

/** Log form of an address: never the whole thing. */
internal fun redactAddress(address: String): String = "…" + address.takeLast(ADDRESS_TAIL)

private const val ADDRESS_TAIL = 5
