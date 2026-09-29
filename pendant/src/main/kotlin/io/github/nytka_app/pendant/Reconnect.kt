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

/** What the watchdog wants done about silence; see [SilenceWatchdog]. */
internal sealed interface WatchdogAction {
    data object None : WatchdogAction

    /** Toggle the audio subscription; [nextMs] is how long to wait before the resubscribe after this one. */
    data class Resubscribe(
        val silentMs: Long,
        val nextMs: Long,
    ) : WatchdogAction

    data class Escalate(
        val silentMs: Long,
    ) : WatchdogAction
}

/**
 * Decides what to do while audio is wanted but absent. Resubscribing every 4 s forever churned the
 * CCCD of a link that was slow to recover, so the wait doubles (4, 8, 16 s, at most 30 s) and starts
 * over as soon as audio arrives. After [escalateAfterMs] of continuous silence it asks for a
 * reconnect, at most once per [escalateCooldownMs]. Call [check] only while audio is wanted: a muted
 * pendant is silent by intent and must never reach it. The watch loop owns it; only [audioArrived] may be
 * called from another thread.
 */
internal class SilenceWatchdog(
    private val firstMs: Long = 4_000,
    private val maxMs: Long = 30_000,
    private val escalateAfterMs: Long = 120_000,
    private val escalateCooldownMs: Long = 300_000,
) {
    private var seenAudioAtMs = NEVER
    private var anchorMs = 0L
    private var waitMs = firstMs
    private var escalatedAtMs = NEVER

    @Volatile private var audioSinceCheck = false

    /** Called from the notification thread: real audio, unlike a moved baseline, lifts the escalation cap. */
    fun audioArrived() {
        audioSinceCheck = true
    }

    /** [lastAudioAtMs] moves on every notification and when audio is switched on, which restarts the backoff. */
    fun check(
        nowMs: Long,
        lastAudioAtMs: Long,
    ): WatchdogAction {
        if (audioSinceCheck) {
            audioSinceCheck = false
            escalatedAtMs = NEVER // the reconnect worked; the cap is for escalations that brought no audio back
        }
        if (lastAudioAtMs != seenAudioAtMs) {
            seenAudioAtMs = lastAudioAtMs
            anchorMs = lastAudioAtMs
            waitMs = firstMs
        }
        val silentMs = nowMs - lastAudioAtMs
        if (silentMs >= escalateAfterMs && (escalatedAtMs == NEVER || nowMs - escalatedAtMs >= escalateCooldownMs)) {
            escalatedAtMs = nowMs
            return WatchdogAction.Escalate(silentMs)
        }
        if (nowMs - anchorMs < waitMs) return WatchdogAction.None
        anchorMs = nowMs
        waitMs = (waitMs * 2).coerceAtMost(maxMs)
        return WatchdogAction.Resubscribe(silentMs, waitMs)
    }

    private companion object {
        const val NEVER = -1L
    }
}

/**
 * Says how long a silence was when audio comes back after at least [thresholdMs] of it, so a stall that ends by
 * itself still leaves a line. The clock starts when audio is switched on ([switchedOn]) and restarts with every
 * notification ([arrived]); a mute is no silence, because unmuting switches audio on again. It is armed before the
 * subscription is written, where [SilenceWatchdog]'s baseline moves after it: a notification can beat the caller
 * back from the write, and would be measured from before the mute. Callable from any thread.
 */
internal class ResumeDetector(
    private val thresholdMs: Long = 2_000,
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
