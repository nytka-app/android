package io.github.nytka_app.pendant

import io.github.nytka_app.pendant.LimitlessWire.LENGTH_DELIMITED
import io.github.nytka_app.pendant.LimitlessWire.VARINT
import io.github.nytka_app.pendant.LimitlessWire.fits
import io.github.nytka_app.pendant.LimitlessWire.intField
import io.github.nytka_app.pendant.LimitlessWire.message
import io.github.nytka_app.pendant.LimitlessWire.readVarint
import java.util.UUID

/**
 * The Limitless pendant's Bluetooth identifiers. UNTESTED on real hardware: Nytka has no Limitless pendant.
 * They come from `BasedHardware/omi` at afad3b7, `app/lib/services/devices/models.dart`
 * (`limitlessServiceUuid`, `limitlessTxCharUuid`, `limitlessRxCharUuid`); battery is the standard service.
 */
object LimitlessUuids {
    val SERVICE: UUID = UUID.fromString("632de001-604c-446b-a80f-7963e950f3fb")

    /** Commands go here, written with response. */
    val TX: UUID = UUID.fromString("632de002-604c-446b-a80f-7963e950f3fb")

    /** Everything the pendant says, notified. */
    val RX: UUID = UUID.fromString("632de003-604c-446b-a80f-7963e950f3fb")
}

/** What the pendant does with its microphone, set by message 8: `{batch, realTime}`. */
enum class LimitlessMode(
    val batch: Boolean,
    val realTime: Boolean,
) {
    /** Push the pages in flash, oldest first. */
    Download(batch = true, realTime = false),

    /** Stream audio as it is heard. */
    Live(batch = false, realTime = true),

    /** Record to flash only. */
    RecordOnly(batch = false, realTime = false),
}

/** One notification: a fragment of the message with wrapper [index]; fragments are told apart by [seq]. */
class LimitlessPacket(
    val index: Int,
    val seq: Int,
    val numFrags: Int,
    val payload: ByteArray,
)

/**
 * One flash page: [index] is its position, [timestampMs] the pendant's clock when it was written (0 if the page
 * carries none), [frames] the Opus packets in it (none for a page with no audio, which the drain may pass).
 */
class LimitlessPage(
    val index: Long,
    val timestampMs: Long,
    val frames: List<ByteArray>,
)

/** The pendant's flash, from `GetDeviceStatus`: pages [oldest] to [newest] are held; [free] of [total] are empty. */
data class LimitlessFlash(
    val oldest: Long,
    val newest: Long,
    val session: Long,
    val free: Long,
    val total: Long,
)

/**
 * The Limitless wire format: protobuf messages inside a "wrapper" (1 index, 2 seq, 3 fragment count, 4 payload), with
 * the messages the pendant understands as numbered fields of the payload. Ported from `BasedHardware/omi` at afad3b7
 * (MIT licensed, license in that repository): `app/android/app/src/main/kotlin/com/friend/ios/limitless/
 * LimitlessProtocol.kt` and `LimitlessFlashDrainEngine.kt`, and `app/lib/services/devices/limitless_connection.dart`
 * for the button, the LED and live audio. UNTESTED on real hardware: its tests run on packets built here from that
 * source's reading, not on a pendant's.
 *
 * This object holds the commands and the wrapper; pages are [LimitlessPages], the flash state and the button
 * [LimitlessStatus].
 */
object LimitlessProtocol {
    private const val WRAPPER_INDEX = 1
    private const val WRAPPER_SEQ = 2
    private const val WRAPPER_FRAGMENTS = 3
    private const val WRAPPER_PAYLOAD = 4

    private const val MSG_SET_CURRENT_TIME = 6
    private const val MSG_ACKNOWLEDGE = 7
    private const val MSG_MODE = 8
    private const val MSG_GET_STATUS = 21
    private const val MSG_SET_LED = 26
    private const val MSG_REQUEST_DATA = 30

    private const val MAX_LED = 100

    /** One notification: fragment [seq] of [fragments] of the message [index] holds [payload]. */
    internal fun wrap(
        index: Int,
        seq: Int,
        fragments: Int,
        payload: ByteArray,
    ) = intField(WRAPPER_INDEX, index.toLong()) +
        intField(WRAPPER_SEQ, seq.toLong()) +
        intField(WRAPPER_FRAGMENTS, fragments.toLong()) +
        message(WRAPPER_PAYLOAD, payload)

    private fun requestData(requestId: Long) = message(MSG_REQUEST_DATA, intField(1, requestId) + intField(2, 0))

    /** A wrapper holding one message and its request id; [messageIndex] and [requestId] are the caller's counters. */
    private fun command(
        messageIndex: Int,
        requestId: Long,
        number: Int,
        body: ByteArray,
    ): ByteArray = wrap(messageIndex, 0, 1, message(number, body) + requestData(requestId))

    /** Message 6: the pendant's clock, in milliseconds. The pendant only moves it forward. */
    fun setCurrentTime(
        messageIndex: Int,
        requestId: Long,
        timestampMs: Long,
    ) = command(messageIndex, requestId, MSG_SET_CURRENT_TIME, intField(1, timestampMs))

    /** Message 8. */
    fun setMode(
        messageIndex: Int,
        requestId: Long,
        mode: LimitlessMode,
    ) = command(
        messageIndex,
        requestId,
        MSG_MODE,
        intField(1, if (mode.batch) 1 else 0) + intField(2, if (mode.realTime) 1 else 0),
    )

    /** Message 7: the pendant frees every page up to and including [upToIndex]. */
    fun acknowledge(
        messageIndex: Int,
        requestId: Long,
        upToIndex: Long,
    ) = command(messageIndex, requestId, MSG_ACKNOWLEDGE, intField(1, upToIndex))

    /** Message 21: the answer comes on RX, see [LimitlessStatus.parseFlash]. */
    fun getStatus(
        messageIndex: Int,
        requestId: Long,
    ) = command(messageIndex, requestId, MSG_GET_STATUS, ByteArray(0))

    /** Message 26: LED brightness, 0 to 100. The pendant never reports it back. */
    fun setLed(
        messageIndex: Int,
        requestId: Long,
        percent: Int,
    ) = command(messageIndex, requestId, MSG_SET_LED, intField(1, percent.coerceIn(0, MAX_LED).toLong()))

    /** The wrapper of one notification, or null when it is not one. */
    fun parsePacket(data: ByteArray): LimitlessPacket? {
        val fields = PacketFields()
        var pos = 0
        while (pos < data.size) pos = fields.read(data, pos) ?: break
        return fields.packet()
    }

    private class PacketFields {
        var index: Int? = null
        var seq = 0
        var fragments: Int? = null
        var payload: ByteArray? = null
        var broken = false

        /** Reads the field at [at]; the position after it, or null when the rest cannot be read. */
        fun read(
            data: ByteArray,
            at: Int,
        ): Int? {
            val tag = data[at].toInt() and 0xFF
            val number = tag shr 3
            return when (tag and 0x07) {
                VARINT -> {
                    val (value, next) = readVarint(data, at + 1)
                    store(number, value)
                    next
                }

                LENGTH_DELIMITED -> {
                    val (length, next) = readVarint(data, at + 1)
                    if (!fits(length, next, data.size)) {
                        broken = number == WRAPPER_PAYLOAD
                        return null
                    }
                    if (number == WRAPPER_PAYLOAD) payload = data.copyOfRange(next, next + length.toInt())
                    next + length.toInt()
                }

                else -> null
            }
        }

        private fun store(
            number: Int,
            value: Long,
        ) {
            when (number) {
                WRAPPER_INDEX -> index = value.toInt()
                WRAPPER_SEQ -> seq = value.toInt()
                WRAPPER_FRAGMENTS -> fragments = value.toInt()
            }
        }

        fun packet(): LimitlessPacket? {
            if (broken) return null
            val i = index ?: return null
            val f = fragments ?: return null
            val p = payload ?: return null
            return LimitlessPacket(i, seq, f, p)
        }
    }
}

/**
 * Joins the fragments of messages. A message may span several notifications; [add] gives it back when its last
 * fragment is in. Fragments of a message that never completes are dropped by [reset] (a new connection) or by
 * [limit] more arriving messages.
 */
class LimitlessReassembler(
    private val limit: Int = MAX_PENDING,
) {
    private val pending = LinkedHashMap<Int, MutableMap<Int, ByteArray>>()

    fun add(packet: LimitlessPacket): ByteArray? {
        val parts = pending.getOrPut(packet.index) { HashMap() }
        parts[packet.seq] = packet.payload
        while (pending.size > limit) pending.remove(pending.keys.first())
        if (parts.size != packet.numFrags) return null
        pending.remove(packet.index)
        val whole = java.io.ByteArrayOutputStream()
        for (n in 0 until packet.numFrags) parts[n]?.let(whole::write)
        return whole.toByteArray()
    }

    fun reset() = pending.clear()

    private companion object {
        const val MAX_PENDING = 64
    }
}
