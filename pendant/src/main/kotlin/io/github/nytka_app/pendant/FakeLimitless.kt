package io.github.nytka_app.pendant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * A Limitless pendant as bytes: it answers the commands [LimitlessCommands] writes the way
 * `BasedHardware/omi` at afad3b7 reads (status on message 21, a push of every held page after message 8 `{1, 0}`,
 * pages freed by message 7). It is the only Limitless device Nytka has been tested against; no real pendant has
 * been. Wire it with [listener] =
 * [LimitlessStorage.onNotification] (or the pendant's RX handler) and give it to the class under test as its
 * [LimitlessLink]. Time stands in the test's hands: a download sends one message per scheduler turn.
 */
class FakeLimitless(
    private val scope: CoroutineScope,
    val capacityPages: Long = 1_000,
    /** A message with a longer payload arrives in fragments of this many bytes. */
    var fragmentBytes: Int = 180,
    /** How many pages one download message holds. */
    var pagesPerMessage: Int = 1,
) : LimitlessLink {
    var listener: ((ByteArray) -> Unit)? = null

    var connected = true
        private set

    /** Every command written to TX, in order. */
    val commands = mutableListOf<ByteArray>()
    val modes = mutableListOf<LimitlessMode>()
    val clockWrites = mutableListOf<Long>()
    val ledWrites = mutableListOf<Long>()

    /** The `upToIndex` of every message 7. */
    val acknowledged = mutableListOf<Long>()

    /** Writes fail, like a link that dropped under the app. */
    var failWrites = false

    /** Message 21 gets no answer. */
    var silent = false

    /** A download goes quiet after this many pages, like a stalled link; null means never. */
    var stallAfterPages: Int? = null

    private class Held(
        val index: Long,
        val page: ByteArray,
    )

    private val held = ArrayDeque<Held>()
    private var nextIndex = 0L
    private var messages = 0
    private var transfer: Job? = null

    /** The first page index the pendant still holds, or the next one to be written when it holds none. */
    val first: Long get() = held.firstOrNull()?.index ?: nextIndex

    val pageCount: Int get() = held.size

    val mode: LimitlessMode? get() = modes.lastOrNull()

    /** Writes one flash page of [frames] stamped [timestampMs]; the oldest goes when the flash is full. */
    fun store(
        timestampMs: Long,
        frames: List<ByteArray>,
    ) = storeRaw(LimitlessMessages.page(timestampMs, frames))

    /** Writes [page] as it is, for pages the driver must not be able to read. */
    fun storeRaw(page: ByteArray) {
        held.addLast(Held(nextIndex++, page))
        while (held.size > capacityPages) held.removeFirst()
    }

    fun disconnect() {
        connected = false
        transfer?.cancel()
    }

    /** Sends live audio, as the pendant does in real-time mode. */
    fun live(frames: List<ByteArray>) = deliver(LimitlessMessages.live(frames))

    fun doublePress() = deliver(LimitlessMessages.doublePress())

    override suspend fun write(value: ByteArray): Boolean {
        if (!connected || failWrites) return false
        commands += value
        val (number, fields) = LimitlessMessages.command(value) ?: return true
        when (number) {
            MSG_SET_CURRENT_TIME -> fields[1]?.let { clockWrites += it }
            MSG_ACKNOWLEDGE -> fields[1]?.let { upTo -> acknowledge(upTo) }
            MSG_MODE -> setMode(fields[1] == 1L, fields[2] == 1L)
            MSG_GET_STATUS -> if (!silent) deliver(LimitlessMessages.flash(flash()))
            MSG_SET_LED -> fields[1]?.let { ledWrites += it }
        }
        return true
    }

    private fun acknowledge(upTo: Long) {
        acknowledged += upTo
        held.removeAll { it.index <= upTo }
    }

    private fun setMode(
        batch: Boolean,
        realTime: Boolean,
    ) {
        modes += LimitlessMode.entries.first { it.batch == batch && it.realTime == realTime }
        transfer?.cancel()
        if (batch) {
            transfer =
                scope.launch {
                    val all = held.toList()
                    val stall = stallAfterPages
                    for (group in (if (stall == null) all else all.take(stall)).chunked(pagesPerMessage)) {
                        deliver(LimitlessMessages.pages(SESSION, group.map { it.index to it.page }))
                        yield()
                    }
                    if (stall != null && stall < all.size) awaitCancellation()
                }
        }
    }

    private fun flash() =
        LimitlessFlash(
            oldest = first,
            newest = held.lastOrNull()?.index ?: nextIndex,
            session = SESSION,
            free = capacityPages - held.size,
            total = capacityPages,
        )

    private fun deliver(payload: ByteArray) {
        val index = messages++ % INDEX_SPAN
        for (notification in LimitlessMessages.notifications(
            index,
            payload,
            fragmentBytes,
        )) {
            listener?.invoke(notification)
        }
    }

    private companion object {
        const val MSG_SET_CURRENT_TIME = 6
        const val MSG_ACKNOWLEDGE = 7
        const val MSG_MODE = 8
        const val MSG_GET_STATUS = 21
        const val MSG_SET_LED = 26
        const val SESSION = 1L

        /** Keeps the message index a one byte varint, never 0x22, which the button and status readers scan for. */
        const val INDEX_SPAN = 32
    }
}
