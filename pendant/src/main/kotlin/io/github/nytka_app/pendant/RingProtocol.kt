package io.github.nytka_app.pendant

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The ring's answer to INFO (`storage.c` `send_ring_info_response`). */
data class RingInfo(
    val readSeq: Long,
    val writeSeq: Long,
    val capacityPackets: Long,
    val droppedPackets: Long,
    val packetBytes: Int,
)

/** What one [PendantStorage.read] window yields; [Done] also reports a READ the firmware refused. */
sealed interface RingEvent {
    class Begin(
        val startSeq: Long,
        val count: Long,
    ) : RingEvent

    class Records(
        val records: List<RingRecord>,
    ) : RingEvent

    class Done(
        val status: Int,
        val nextSeq: Long,
    ) : RingEvent
}

/** Statuses the firmware sends (`storage.c`), plus the ones only this app produces (negative). */
object RingStatus {
    const val OK = 0
    const val INVALID_COMMAND = 6
    const val NOT_READY = 9
    const val OUT_OF_RANGE = 10

    /** Nothing answered in time. */
    const val TIMEOUT = -1

    /** The link dropped while waiting. */
    const val LINK_LOST = -2

    /** Nothing was sent: the firmware is unsupported or the control notifications could not be enabled. */
    const val UNAVAILABLE = -3
}

/** A notification on the control characteristic, parsed by [RingProtocol.parse]. */
sealed interface RingNotification {
    class Ack(
        val status: Int,
    ) : RingNotification

    class Info(
        val info: RingInfo,
    ) : RingNotification

    class ReadBegin(
        val startSeq: Long,
        val count: Long,
    ) : RingNotification

    /** Raw ring bytes, the `0x03` type byte already removed; not aligned to records. */
    class Data(
        val bytes: ByteArray,
    ) : RingNotification

    class Done(
        val status: Int,
        val nextSeq: Long,
    ) : RingNotification

    /** Not on the wire: what waiting for a notification can end with. */
    data object Timeout : RingNotification

    data object Closed : RingNotification
}

/** The 16-byte read of the status characteristic: four u32 little-endian (`storage_read_characteristic`). */
data class RingStatusRead(
    val usedBytes: Long,
    val unreadPackets: Long,
    val freeBytes: Long,
    val rtcValid: Boolean,
)

/**
 * The storage service's wire format (`firmware/omi/src/lib/core/storage.c` and
 * `app/lib/services/devices/ring_protocol.dart` in `BasedHardware/omi` at `a2d37dea`). Commands and notifications
 * are big-endian; the status read, the clock and the features are little-endian. A READ the firmware refuses
 * (status 9 or 10) answers with an ACK carrying the status, never with DONE.
 */
object RingProtocol {
    const val RECORD_BYTES = 444
    const val STAMP_BYTES = 4
    const val AUDIO_BYTES = RECORD_BYTES - STAMP_BYTES

    private const val CMD_STOP = 0x03
    private const val CMD_INFO = 0x10
    private const val CMD_READ = 0x11
    private const val CMD_ADVANCE = 0x12

    private const val NOTIFY_ACK = 0x01
    private const val NOTIFY_INFO = 0x02
    private const val NOTIFY_DATA = 0x03
    private const val NOTIFY_DONE = 0x04
    private const val NOTIFY_READ_BEGIN = 0x05

    private const val INFO_SIZE = 31
    private const val DONE_SIZE = 10
    private const val READ_BEGIN_SIZE = 13
    private const val STATUS_SIZE = 16

    fun stop(): ByteArray = byteArrayOf(CMD_STOP.toByte())

    fun info(): ByteArray = byteArrayOf(CMD_INFO.toByte())

    /** `[0x11][startSeq u64]`, plus `[count u32]` when [count] is positive; else the firmware reads to the end. */
    fun read(
        startSeq: Long,
        count: Int = 0,
    ): ByteArray {
        val buffer = ByteBuffer.allocate(if (count > 0) 13 else 9).order(ByteOrder.BIG_ENDIAN)
        buffer.put(CMD_READ.toByte()).putLong(startSeq)
        if (count > 0) buffer.putInt(count)
        return buffer.array()
    }

    /** `[0x12][newReadSeq u64]`. */
    fun advance(newReadSeq: Long): ByteArray =
        ByteBuffer
            .allocate(9)
            .order(ByteOrder.BIG_ENDIAN)
            .put(CMD_ADVANCE.toByte())
            .putLong(newReadSeq)
            .array()

    /** The pendant clock write: exactly four bytes, a u32 little-endian epoch in seconds. */
    fun clock(epochS: Long): ByteArray =
        ByteBuffer
            .allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(epochS.toInt())
            .array()

    /** A little-endian u32 read (the clock, the features), or null when it is not four bytes. */
    fun u32le(value: ByteArray?): Long? =
        if (value == null || value.size < 4) {
            null
        } else {
            ByteBuffer
                .wrap(value, 0, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .int
                .toLong() and 0xFFFFFFFFL
        }

    fun status(value: ByteArray?): RingStatusRead? {
        if (value == null || value.size < STATUS_SIZE) return null
        val buffer = ByteBuffer.wrap(value, 0, STATUS_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        return RingStatusRead(
            usedBytes = buffer.int.toLong() and 0xFFFFFFFFL,
            unreadPackets = buffer.int.toLong() and 0xFFFFFFFFL,
            freeBytes = buffer.int.toLong() and 0xFFFFFFFFL,
            rtcValid = buffer.int != 0,
        )
    }

    /** A notification on the control characteristic; null when the type is unknown or the value is too short. */
    fun parse(value: ByteArray): RingNotification? {
        if (value.isEmpty()) return null
        val buffer = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN)
        return when (value[0].toInt() and 0xFF) {
            NOTIFY_ACK -> if (value.size >= 2) RingNotification.Ack(value[1].toInt() and 0xFF) else null
            NOTIFY_INFO -> if (value.size >= INFO_SIZE) RingNotification.Info(parseInfo(buffer)) else null
            NOTIFY_DATA -> RingNotification.Data(value.copyOfRange(1, value.size))
            NOTIFY_DONE ->
                if (value.size >= DONE_SIZE) {
                    RingNotification.Done(value[1].toInt() and 0xFF, buffer.getLong(2))
                } else {
                    null
                }
            NOTIFY_READ_BEGIN ->
                if (value.size >= READ_BEGIN_SIZE) {
                    RingNotification.ReadBegin(buffer.getLong(1), buffer.getInt(9).toLong() and 0xFFFFFFFFL)
                } else {
                    null
                }
            else -> null
        }
    }

    private fun parseInfo(buffer: ByteBuffer) =
        RingInfo(
            readSeq = buffer.getLong(1),
            writeSeq = buffer.getLong(9),
            capacityPackets = buffer.getInt(17).toLong() and 0xFFFFFFFFL,
            droppedPackets = buffer.getLong(21),
            packetBytes = buffer.getShort(29).toInt() and 0xFFFF,
        )
}
