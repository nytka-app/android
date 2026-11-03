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

/** What the pendant said that [LimitlessStorage] waits for. */
internal sealed interface LimitlessInbound {
    class Flash(
        val flash: LimitlessFlash,
    ) : LimitlessInbound

    /** The pages of one message; null stands for a page of audio that could not be read. */
    class Pages(
        val pages: List<LimitlessPage?>,
    ) : LimitlessInbound

    data object Timeout : LimitlessInbound

    data object Closed : LimitlessInbound
}

/**
 * The Limitless driver's [RecorderStorage]. UNTESTED on real hardware: it is built from `BasedHardware/omi` at
 * afad3b7 and exercised only against [FakeLimitless].
 *
 * A position is a flash page index and a block is one flash page: its stamp is the pendant's clock in milliseconds
 * ([StampUnit.Milliseconds]) and its frames the Opus packets in it, made 20 ms long by [OpusFrames]. [status] is
 * message 21; [read] puts the pendant in download mode (message 8, `{1, 0}`), takes the pages it pushes and puts it
 * back in [restingMode]; [advance] is message 7, which frees every page up to and including the index it names, so
 * `advance(through)` names `through - 1`. Nothing is freed until the caller says so, which is after the server
 * accepted the audio. The app never sends anything that erases the whole flash.
 *
 * What the upstream source does not say, and a real pendant must settle:
 * - how a page's size is, so [StoredRange.blockBytes] is an estimate used only for the transfer rate shown;
 * - whether the pendant frees a page for a message 7 sent outside download mode;
 * - whether the pendant keeps recording to flash while it streams live audio, which decides what a drain misses.
 *
 * Wire facts: [LimitlessProtocol].
 */
internal class LimitlessStorage(
    private val link: LimitlessLink,
    private val commands: LimitlessCommands,
    private val restingMode: () -> LimitlessMode,
    private val log: (String) -> Unit = {},
) : RecorderStorage {
    private val mutableSupport = MutableStateFlow<StorageSupport>(StorageSupport.Unknown)
    private val mutableStatus = MutableStateFlow<Int?>(null)
    private val operations = Mutex()
    private val reassembler = LimitlessReassembler()

    @Volatile
    private var inbox: Channel<LimitlessInbound>? = null

    @Volatile
    private var expecting = Expect.Nothing

    override val support: StateFlow<StorageSupport> = mutableSupport

    /** Always null: the pendant's clock is set to the phone's, and its own clock is not read (see the README). */
    override val clockSkew: StateFlow<Long?> = MutableStateFlow(null)
    override val lastStatus: StateFlow<Int?> = mutableStatus

    private enum class Expect { Nothing, Flash, Pages }

    /** Storage has no feature bit on the Limitless pendant: it is used once connected, and fails if nothing answers. */
    fun evaluate() {
        mutableSupport.value = StorageSupport.Supported
    }

    /** The connection ended: an operation waiting on it stops. */
    fun linkLost() {
        mutableSupport.value = StorageSupport.Unknown
        inbox?.close()
    }

    /**
     * True while a drain is on: the pendant is in download mode, so a mode write from [LimitlessPendant.setAudio]
     * would cut it short. The drain writes the resting mode itself when it ends.
     */
    @Volatile
    var downloading = false
        private set

    /**
     * A notification on RX. True when it is not live audio: an operation waits for it, or a drain is on, in which case
     * what arrives late (pages past the window) is dropped rather than taken for live audio.
     */
    @Synchronized
    fun onNotification(raw: ByteArray): Boolean {
        val box = inbox
        if (box != null) {
            when (expecting) {
                Expect.Nothing -> Unit
                Expect.Flash -> LimitlessStatus.parseFlash(raw)?.let { box.trySend(LimitlessInbound.Flash(it)) }
                Expect.Pages -> pagesIn(raw)?.let { box.trySend(LimitlessInbound.Pages(it)) }
            }
        }
        return downloading || (box != null && expecting != Expect.Nothing)
    }

    private fun pagesIn(raw: ByteArray): List<LimitlessPage?>? {
        val packet = LimitlessProtocol.parsePacket(raw) ?: return null
        val message = reassembler.add(packet) ?: return null
        return LimitlessPages.parsePages(message).ifEmpty { null }
    }

    override suspend fun status(): StoredRange? = operations.withLock { flash()?.let(::rangeOf) }

    /** Message 21 and its answer; null when it failed, with [lastStatus] telling why. */
    private suspend fun flash(): LimitlessFlash? {
        val box = open(Expect.Flash) ?: return fail(StorageStatus.UNAVAILABLE)
        try {
            if (!link.write(commands.getStatus())) return fail(StorageStatus.TIMEOUT)
            return when (val answer = box.receiveWithin(STATUS_TIMEOUT_MS)) {
                is LimitlessInbound.Flash -> answer.flash.also { mutableStatus.value = null }
                LimitlessInbound.Closed -> fail(StorageStatus.LINK_LOST)
                else -> fail(StorageStatus.TIMEOUT)
            }
        } finally {
            close()
        }
    }

    /** `end` is the page after the newest; with nothing held, it is the oldest index, so the range is empty. */
    private fun rangeOf(flash: LimitlessFlash): StoredRange {
        val held = flash.total > 0 && flash.free < flash.total
        val end = if (held) maxOf(flash.newest + 1, flash.oldest) else flash.oldest
        return StoredRange(flash.oldest, end, flash.total, dropped = 0, blockBytes = ESTIMATED_PAGE_BYTES)
    }

    override fun read(
        from: Long,
        count: Int,
    ): Flow<StoredEvent> =
        flow {
            operations.withLock {
                val range = flash()?.let(::rangeOf)
                if (range == null) {
                    emit(StoredEvent.Done(mutableStatus.value ?: StorageStatus.UNAVAILABLE, from))
                    return@withLock
                }
                val start = maxOf(from, range.first)
                val last = minOf(range.end, from + count)
                emit(StoredEvent.Begin(start, maxOf(0L, last - start)))
                if (start >= last) {
                    emit(StoredEvent.Done(StorageStatus.OK, from))
                } else {
                    download(from, start, last)
                }
            }
        }

    private suspend fun FlowCollector<StoredEvent>.download(
        from: Long,
        start: Long,
        last: Long,
    ) {
        val box = open(Expect.Pages)
        if (box == null) {
            emit(StoredEvent.Done(StorageStatus.UNAVAILABLE, from))
            return
        }
        synchronized(this@LimitlessStorage) { reassembler.reset() }
        var restore = true
        downloading = true
        try {
            if (link.write(commands.setMode(LimitlessMode.Download))) {
                restore = transfer(box, start, last)
            } else {
                emit(StoredEvent.Done(StorageStatus.TIMEOUT, from))
            }
        } finally {
            close()
            withContext(NonCancellable) {
                if (restore) link.write(commands.setMode(restingMode()))
                downloading = false
            }
        }
    }

    /** Emits one window's blocks and its [StoredEvent.Done]; false when the link is gone and nothing can be sent. */
    private suspend fun FlowCollector<StoredEvent>.transfer(
        box: Channel<LimitlessInbound>,
        start: Long,
        last: Long,
    ): Boolean {
        var next = start
        while (true) {
            when (val inbound = box.receiveWithin(STALL_TIMEOUT_MS)) {
                LimitlessInbound.Timeout -> {
                    emit(StoredEvent.Done(StorageStatus.TIMEOUT, next))
                    return true
                }

                LimitlessInbound.Closed -> {
                    emit(StoredEvent.Done(StorageStatus.LINK_LOST, next))
                    return false
                }

                is LimitlessInbound.Flash -> Unit
                is LimitlessInbound.Pages -> {
                    val batch = take(inbound.pages, next, last)
                    if (batch.blocks.isNotEmpty()) emit(StoredEvent.Blocks(batch.blocks))
                    next = batch.next
                    if (batch.unreadable) {
                        log("storage: a page of audio could not be read, the window ends before it")
                        emit(StoredEvent.Done(StorageStatus.UNAVAILABLE, next))
                        return true
                    }
                    if (next >= last) {
                        emit(StoredEvent.Done(StorageStatus.OK, next))
                        return true
                    }
                }
            }
        }
    }

    private class Batch(
        val blocks: List<StoredBlock>,
        val next: Long,
        val unreadable: Boolean,
    )

    /** The blocks of [pages] that belong to the window [next] until [last]; stops at the first page it cannot read. */
    private fun take(
        pages: List<LimitlessPage?>,
        next: Long,
        last: Long,
    ): Batch {
        val blocks = ArrayList<StoredBlock>()
        var following = next
        for (page in pages) {
            if (page == null) return Batch(blocks, following, unreadable = true)
            if (page.index < following || page.index >= last) continue
            val joined = OpusFrames.toTwentyMs(page.frames)
            if (joined.dropped > 0) log("storage: page ${page.index} left out ${joined.dropped} unpaired 10 ms frames")
            blocks += StoredBlock(page.index, page.timestampMs, StampUnit.Milliseconds, joined.frames)
            following = page.index + 1
        }
        return Batch(blocks, following, unreadable = false)
    }

    override suspend fun advance(through: Long): Int =
        operations.withLock {
            val range = flash()?.let(::rangeOf) ?: return@withLock mutableStatus.value ?: StorageStatus.UNAVAILABLE
            if (through <= range.first || through > range.end) return@withLock StorageStatus.OUT_OF_RANGE
            if (link.write(commands.acknowledge(through - 1))) StorageStatus.OK else StorageStatus.TIMEOUT
        }

    /** Opens the inbox for one operation; null when storage is not in use. */
    private fun open(expect: Expect): Channel<LimitlessInbound>? {
        if (mutableSupport.value != StorageSupport.Supported) return null
        expecting = expect
        return Channel<LimitlessInbound>(Channel.UNLIMITED).also { inbox = it }
    }

    private fun close() {
        expecting = Expect.Nothing
        inbox = null
    }

    private fun fail(status: Int): LimitlessFlash? {
        mutableStatus.value = status
        return null
    }

    private suspend fun Channel<LimitlessInbound>.receiveWithin(timeoutMs: Long): LimitlessInbound =
        withTimeoutOrNull(timeoutMs) { receiveCatching().getOrNull() ?: LimitlessInbound.Closed }
            ?: LimitlessInbound.Timeout

    companion object {
        /** A window with nothing from the pendant for this long is over; upstream's drain gives up after 30 s too. */
        const val STALL_TIMEOUT_MS = 30_000L
        private const val STATUS_TIMEOUT_MS = 7_000L

        /** Upstream names no flash page size; this only scales the transfer rate the app shows. */
        const val ESTIMATED_PAGE_BYTES = 1024
    }
}
