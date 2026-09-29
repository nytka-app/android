package io.github.nytka_app.pendant

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** What [OmiStorage] needs from the Bluetooth link; [OmiPendant] and [FakeRing] implement it. */
interface StorageLink {
    /** Enables notifications on the storage control characteristic. */
    suspend fun subscribeControl(): Boolean

    /** Writes to the storage control characteristic, with response. */
    suspend fun writeControl(value: ByteArray): Boolean

    /** The pendant clock (`19b10032`), a u32 little-endian epoch in seconds. */
    suspend fun readClock(): Long?

    suspend fun writeClock(epochS: Long): Boolean

    /** The features characteristic (`19b10021`), a u32 little-endian; bit 6 is offline storage. */
    suspend fun readFeatures(): Long?
}

/**
 * The storage protocol over a [StorageLink]: the link hands every control notification to [onNotification] and
 * calls [linkLost] when the connection ends.
 */
class OmiStorage(
    private val link: StorageLink,
    private val log: (String) -> Unit = {},
) : PendantStorage {
    private val mutableSupport = MutableStateFlow<StorageSupport>(StorageSupport.Unknown)
    private val mutableSkew = MutableStateFlow<Long?>(null)
    private val mutableStatus = MutableStateFlow<Int?>(null)
    private val operations = Mutex()

    @Volatile private var inbox: Channel<ByteArray>? = null

    @Volatile private var subscribed = false
    private var reads = 0

    override val support: StateFlow<StorageSupport> = mutableSupport
    override val clockSkew: StateFlow<Long?> = mutableSkew
    override val lastStatus: StateFlow<Int?> = mutableStatus

    /** A notification on the control characteristic; it is dropped when no operation is waiting. */
    fun onNotification(value: ByteArray) {
        inbox?.trySend(value)
    }

    /** The connection ended: its subscriptions are gone, and an operation waiting on it stops. */
    fun linkLost() {
        subscribed = false
        reads = 0
        mutableSupport.value = StorageSupport.Unknown
        inbox?.close()
    }

    /** Decides from the firmware revision and the features value whether storage may be used. */
    fun evaluate(
        firmware: String?,
        features: Long?,
    ) {
        mutableSupport.value = supportFor(firmware, features)
    }

    /** Reads the features characteristic and [evaluate]s; the firmware revision comes from the device info. */
    suspend fun evaluate(firmware: String?) = evaluate(firmware, link.readFeatures())

    /**
     * Reads the pendant clock, records the skew, then writes the phone's time. [nowS] is the phone's epoch in
     * seconds; it is read again for the write. False when the write failed.
     */
    suspend fun syncClock(nowS: () -> Long): Boolean {
        mutableSkew.value = link.readClock()?.let { it - nowS() }
        return setTime(nowS())
    }

    override suspend fun setTime(epochS: Long): Boolean = link.writeClock(epochS)

    override suspend fun info(): RingInfo? =
        operations.withLock {
            val box = open() ?: return@withLock fail(RingStatus.UNAVAILABLE)
            try {
                if (!link.writeControl(RingProtocol.stop())) return@withLock fail(RingStatus.TIMEOUT)
                val stopped = box.await(ACK_TIMEOUT_MS) { it is RingNotification.Ack }
                if (stopped !is RingNotification.Ack) return@withLock fail(statusOf(stopped))
                if (!link.writeControl(RingProtocol.info())) return@withLock fail(RingStatus.TIMEOUT)
                val answer =
                    box.await(ACK_TIMEOUT_MS) {
                        it is RingNotification.Info || (it is RingNotification.Ack && it.status != RingStatus.OK)
                    }
                if (answer is RingNotification.Info) {
                    mutableStatus.value = null
                    answer.info
                } else {
                    fail(statusOf(answer))
                }
            } finally {
                inbox = null
            }
        }

    override fun read(
        fromSeq: Long,
        count: Int,
    ): Flow<RingEvent> =
        flow {
            operations.withLock {
                val box = open()
                if (box == null) {
                    emit(RingEvent.Done(RingStatus.UNAVAILABLE, fromSeq))
                    return@withLock
                }
                var stopNeeded = true
                try {
                    if (link.writeControl(RingProtocol.read(fromSeq, count))) {
                        stopNeeded = transfer(box, fromSeq)
                    } else {
                        emit(RingEvent.Done(RingStatus.TIMEOUT, fromSeq))
                    }
                } finally {
                    if (stopNeeded) stop(box)
                    inbox = null
                }
            }
        }

    /**
     * Emits one window's events; false when no stop is needed: the firmware ended it, the link is gone, or it went
     * quiet. A STOP after silence would let firmware 3.0.21 free everything it sent, though the phone read only
     * part of it; the caller reads again from what it holds, and that READ replaces the transfer.
     */
    private suspend fun FlowCollector<RingEvent>.transfer(
        box: Channel<ByteArray>,
        fromSeq: Long,
    ): Boolean {
        val reassembler = RingRecordReassembler()
        var begun = false
        var nextSeq = fromSeq
        var waitMs = if (reads++ == 0) FIRST_BEGIN_TIMEOUT_MS else BEGIN_TIMEOUT_MS
        while (true) {
            when (val n = box.receiveWithin(waitMs)) {
                RingNotification.Timeout -> {
                    emit(RingEvent.Done(RingStatus.TIMEOUT, nextSeq))
                    return false
                }
                RingNotification.Closed -> {
                    emit(RingEvent.Done(RingStatus.LINK_LOST, nextSeq))
                    return false
                }
                is RingNotification.ReadBegin -> {
                    reassembler.restart(n.startSeq)
                    nextSeq = n.startSeq
                    begun = true
                    waitMs = DATA_TIMEOUT_MS
                    emit(RingEvent.Begin(n.startSeq, n.count))
                }
                is RingNotification.Data ->
                    if (begun) {
                        val records = reassembler.append(n.bytes)
                        if (records.isNotEmpty()) {
                            nextSeq = records.last().seq + 1
                            emit(RingEvent.Records(records))
                        }
                    }
                is RingNotification.Done -> {
                    emit(RingEvent.Done(n.status, n.nextSeq))
                    return false
                }
                is RingNotification.Ack ->
                    if (refused(n, begun)) {
                        emit(RingEvent.Done(n.status, fromSeq)) // a refused READ answers ACK, never DONE
                        return false
                    }
                is RingNotification.Info -> Unit
            }
        }
    }

    /** An ACK with a status before READ_BEGIN refuses the READ; after it, it is logged and ignored. */
    private fun refused(
        ack: RingNotification.Ack,
        begun: Boolean,
    ): Boolean {
        if (ack.status == RingStatus.OK) return false
        if (!begun) return true
        log("storage: ACK status ${ack.status} during a transfer, ignored")
        return false
    }

    override suspend fun advance(seq: Long): Int =
        operations.withLock {
            val box = open() ?: return@withLock RingStatus.UNAVAILABLE
            try {
                if (!link.writeControl(RingProtocol.advance(seq))) return@withLock RingStatus.TIMEOUT
                statusOf(box.await(ACK_TIMEOUT_MS) { it is RingNotification.Ack })
            } finally {
                inbox = null
            }
        }

    /** Subscribes once per connection and opens the inbox; null when storage is unsupported or the subscribe failed. */
    private suspend fun open(): Channel<ByteArray>? {
        if (mutableSupport.value != StorageSupport.Supported) return null
        if (!subscribed) subscribed = link.subscribeControl()
        if (!subscribed) return null
        return Channel<ByteArray>(Channel.UNLIMITED).also { inbox = it }
    }

    /** Sends stop and waits for its ACK, so DATA still in flight is consumed here and not by the next window. */
    private suspend fun stop(box: Channel<ByteArray>) {
        withContext(NonCancellable) {
            if (link.writeControl(RingProtocol.stop())) box.await(STOP_TIMEOUT_MS) { it is RingNotification.Ack }
        }
    }

    private fun fail(status: Int): RingInfo? {
        mutableStatus.value = status
        return null
    }

    private fun statusOf(notification: RingNotification): Int =
        when (notification) {
            is RingNotification.Ack -> notification.status
            RingNotification.Closed -> RingStatus.LINK_LOST
            else -> RingStatus.TIMEOUT
        }

    /** The next notification that parses, [RingNotification.Timeout] after [timeoutMs], or Closed. */
    private suspend fun Channel<ByteArray>.receiveWithin(timeoutMs: Long): RingNotification =
        withTimeoutOrNull(timeoutMs) {
            var parsed: RingNotification? = null
            while (parsed == null) {
                val raw = receiveCatching().getOrNull() ?: return@withTimeoutOrNull RingNotification.Closed
                parsed = RingProtocol.parse(raw)
            }
            parsed
        } ?: RingNotification.Timeout

    /** Skips notifications until [accept] takes one; the result may also be a Timeout or Closed. */
    private suspend fun Channel<ByteArray>.await(
        timeoutMs: Long,
        accept: (RingNotification) -> Boolean,
    ): RingNotification =
        withTimeoutOrNull(timeoutMs) {
            var found: RingNotification
            do {
                found = receiveWithin(Long.MAX_VALUE)
            } while (found != RingNotification.Closed && !accept(found))
            found
        } ?: RingNotification.Timeout

    companion object {
        /**
         * READ_BEGIN must follow the first READ of a connection within 10 s, later ones within 7 s. Answers get 7 s
         * too: the firmware waits up to 5 s for its SD card before answering status 9, which must not read as silence.
         */
        const val FIRST_BEGIN_TIMEOUT_MS = 10_000L
        const val BEGIN_TIMEOUT_MS = 7_000L

        /** Once a window has begun, data must arrive at least this often. */
        const val DATA_TIMEOUT_MS = 10_000L

        private const val ACK_TIMEOUT_MS = 7_000L
        private const val STOP_TIMEOUT_MS = 2_000L
        private const val STORAGE_FEATURE_BIT = 6

        fun supportFor(
            firmware: String?,
            features: Long?,
        ): StorageSupport {
            val version = FirmwareVersion.parse(firmware)
            return when {
                version == null ->
                    StorageSupport.Unsupported(
                        "Offline sync needs firmware ${FirmwareVersion.RING_STORAGE} or later; " +
                            "this pendant did not report its firmware.",
                    )
                version < FirmwareVersion.RING_STORAGE ->
                    StorageSupport.Unsupported(
                        "Offline sync needs firmware ${FirmwareVersion.RING_STORAGE} or later; " +
                            "this pendant has $version. Update it with the official Omi app.",
                    )
                features == null -> StorageSupport.Unsupported("The pendant did not report its features.")
                (features shr STORAGE_FEATURE_BIT) and 1L == 0L ->
                    StorageSupport.Unsupported("This pendant does not offer offline storage.")
                else -> StorageSupport.Supported
            }
        }
    }
}
