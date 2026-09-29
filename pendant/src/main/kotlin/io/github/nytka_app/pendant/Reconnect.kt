package io.github.nytka_app.pendant

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

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
