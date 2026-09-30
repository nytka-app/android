package io.github.nytka_app.pendant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The storage service of a pendant, as bytes: it answers the commands of [RingProtocol] the way firmware 3.0.20
 * (`autoAdvance` false) or 3.0.21 (true) does, and writes records with the firmware's packing rule. Wire it with
 * [listener] = [OmiStorage.onNotification]; [FakePendant] does. Time stands in the test's hands: a transfer sends
 * one notification per scheduler turn.
 */
@Suppress("TooManyFunctions") // one function per firmware behaviour
class FakeRing(
    private val scope: CoroutineScope,
    var firmware: String = "3.0.21",
    var features: Long = (1L shl 6) or (1L shl 7) or (1L shl 8),
    /** 3.0.21's PR #9954: the firmware moves `readSeq` to what it sent, at DONE, at stop and at disconnect. */
    var autoAdvance: Boolean = true,
    val capacityPackets: Long = 1_000,
    var mtu: Int = 247,
) : StorageLink,
    SettingsLink {
    var listener: ((ByteArray) -> Unit)? = null

    var readSeq = 0L
        private set
    var writeSeq = 0L
        private set
    var droppedPackets = 0L
        private set

    /** The pendant clock in seconds, what `19b10032` reads; nothing is stored under 1700000000. */
    var clockS: Long? = 1_800_000_000L

    /** Every command written to the control characteristic, in order. */
    val commands = mutableListOf<ByteArray>()

    /** INFO and READ answer ACK 9 this many more times (the SD card is still mounting). */
    var notReadyAnswers = 0

    /** A transfer goes quiet after this many bytes of a window, like a stalled link; null means never. */
    var stallAfterBytes: Long? = null

    /** At a stall the firmware has already sent this many more bytes than the phone received. */
    var unseenBytesAtStall: Long = 0

    /** DATA notifications sent after the STOP ACK, as the firmware can. */
    var strayDataAfterStop = 0

    /** Whether the control notifications are enabled; the firmware answers nothing otherwise. */
    var subscribed = false
        private set

    val clockWrites = mutableListOf<Long>()
    val unread: Long get() = writeSeq - readSeq

    private val records = HashMap<Long, ByteArray>()
    private var stale = ByteArray(RingProtocol.AUDIO_BYTES)
    private var transfer: Job? = null
    private var sentBytes = 0L
    private var transferStart = 0L

    /** Writes [frames] under one [stampS], starting a new record whenever the next frame does not fit. */
    fun store(
        stampS: Long,
        frames: List<ByteArray>,
    ) {
        require(frames.all { it.size + 1 < RingProtocol.AUDIO_BYTES }) { "a frame must fit a record" }
        val queue = ArrayDeque(frames)
        while (queue.isNotEmpty()) append(stampS, queue)
    }

    /** Puts a raw 444-byte record in the ring, for byte-exact fixtures. */
    fun storeRaw(record: ByteArray) {
        require(record.size == RingProtocol.RECORD_BYTES)
        if (unread >= capacityPackets) {
            records.remove(readSeq)
            readSeq++
            droppedPackets++
        }
        records[writeSeq++] = record
    }

    /** `sd_ring_clear`: sequence numbers restart at 0 and unread audio is gone. */
    fun clear() {
        records.clear()
        readSeq = 0
        writeSeq = 0
        droppedPackets = 0
    }

    /** The link dropped: a running transfer ends and 3.0.21 saves how far it got. */
    fun disconnect() {
        endTransfer(saveProgress = true)
        subscribed = false
    }

    override suspend fun subscribeControl(): Boolean {
        subscribed = true
        return true
    }

    override suspend fun writeControl(value: ByteArray): Boolean {
        commands += value
        if (value.isEmpty()) return ack(RingStatus.INVALID_COMMAND)
        val buffer = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN)
        when (value[0].toInt() and 0xFF) {
            CMD_STOP -> stop()
            CMD_INFO -> if (!notReady()) info()
            CMD_READ ->
                if (value.size != 9 && value.size != 13) {
                    ack(RingStatus.INVALID_COMMAND)
                } else if (!notReady()) {
                    read(buffer.getLong(1), if (value.size == 13) buffer.getInt(9).toLong() and 0xFFFFFFFFL else 0)
                }
            CMD_ADVANCE ->
                if (value.size != 9) ack(RingStatus.INVALID_COMMAND) else advance(buffer.getLong(1))
            CMD_CLEAR -> {
                clear()
                ack(RingStatus.OK)
            }
            else -> ack(RingStatus.INVALID_COMMAND)
        }
        return true
    }

    override suspend fun readClock(): Long? = clockS

    override suspend fun writeClock(epochS: Long): Boolean {
        clockWrites += epochS
        clockS = epochS
        return true
    }

    override suspend fun readFeatures(): Long? = features

    /** The settings characteristics, at the firmware's defaults (`src/settings.c`); writes are recorded. */
    var led: Int = 50
    var gain: Int = 6
    val ledWrites = mutableListOf<Int>()
    val gainWrites = mutableListOf<Int>()

    /** Makes the next settings reads and writes fail, like a link that drops them. */
    var settingsFail = false

    /** Every settings write takes this long. */
    var settingsDelayMs = 0L

    /** The settings reads never answer, like a link that drops them without an error. */
    var settingsHang = false

    override suspend fun readLed(): Int? {
        if (settingsHang) awaitCancellation()
        return if (settingsFail) null else led
    }

    override suspend fun readGain(): Int? {
        if (settingsHang) awaitCancellation()
        return if (settingsFail) null else gain
    }

    override suspend fun writeLed(value: Int): Boolean {
        if (settingsDelayMs > 0) delay(settingsDelayMs)
        if (settingsFail) return false
        ledWrites += value
        led = value.coerceAtMost(LedDim.MAX)
        return true
    }

    override suspend fun writeGain(value: Int): Boolean {
        if (settingsDelayMs > 0) delay(settingsDelayMs)
        if (settingsFail) return false
        gainWrites += value
        gain = value.coerceAtMost(MicGain.MAX)
        return true
    }

    private fun append(
        stampS: Long,
        queue: ArrayDeque<ByteArray>,
    ) {
        val record = ByteArray(RingProtocol.RECORD_BYTES)
        ByteBuffer.wrap(record).order(ByteOrder.BIG_ENDIAN).putInt(stampS.toInt())
        // The block starts as the previous block's bytes: the firmware never zeroes it.
        stale.copyInto(record, RingProtocol.STAMP_BYTES)
        var offset = 0
        while (queue.isNotEmpty()) {
            val frame = queue.first()
            // `write_to_storage`: a frame that would reach byte 440 leaves only its length byte behind and flushes.
            if (offset + 1 + frame.size >= RingProtocol.AUDIO_BYTES) {
                if (offset < RingProtocol.AUDIO_BYTES) record[RingProtocol.STAMP_BYTES + offset] = frame.size.toByte()
                break
            }
            record[RingProtocol.STAMP_BYTES + offset] = frame.size.toByte()
            frame.copyInto(record, RingProtocol.STAMP_BYTES + offset + 1)
            offset += 1 + frame.size
            queue.removeFirst()
        }
        // A block still filling when the store call ends is padded with zeros here; the firmware keeps it in RAM.
        if (queue.isEmpty()) record.fill(0, RingProtocol.STAMP_BYTES + offset, RingProtocol.RECORD_BYTES)
        // A block still filling when the store call ends is padded with zeros here; the firmware keeps it in RAM.
        if (queue.isEmpty()) record.fill(0, RingProtocol.STAMP_BYTES + offset, RingProtocol.RECORD_BYTES)
        stale = record.copyOfRange(RingProtocol.STAMP_BYTES, RingProtocol.RECORD_BYTES)
        storeRaw(record)
    }

    private fun notReady(): Boolean {
        if (notReadyAnswers <= 0) return false
        notReadyAnswers--
        ack(RingStatus.NOT_READY)
        return true
    }

    private fun ack(status: Int): Boolean = notify(byteArrayOf(NOTIFY_ACK.toByte(), status.toByte()))

    private fun notify(value: ByteArray): Boolean {
        if (subscribed) listener?.invoke(value)
        return true
    }

    private fun info() {
        val out = ByteBuffer.allocate(31).order(ByteOrder.BIG_ENDIAN)
        out.put(NOTIFY_INFO.toByte()).putLong(readSeq).putLong(writeSeq)
        out.putInt(capacityPackets.toInt()).putLong(droppedPackets).putShort(RingProtocol.RECORD_BYTES.toShort())
        notify(out.array())
    }

    private fun advance(seq: Long) {
        if (seq < readSeq || seq > writeSeq) {
            ack(RingStatus.OUT_OF_RANGE)
            return
        }
        moveReadTo(seq)
        ack(RingStatus.OK)
    }

    private fun moveReadTo(seq: Long) {
        for (s in readSeq until seq) records.remove(s)
        readSeq = seq
    }

    private fun stop() {
        endTransfer(saveProgress = true)
        ack(RingStatus.OK)
        repeat(strayDataAfterStop) { notify(byteArrayOf(NOTIFY_DATA.toByte(), 9, 9, 9)) }
    }

    private fun read(
        startSeq: Long,
        count: Long,
    ) {
        if (startSeq < readSeq || startSeq > writeSeq) {
            ack(RingStatus.OUT_OF_RANGE)
            return
        }
        val available = writeSeq - startSeq
        val packets = if (count == 0L || count > available) available else count
        endTransfer(saveProgress = false)
        transferStart = startSeq
        sentBytes = 0
        transfer = scope.launch { send(startSeq, packets) }
    }

    private suspend fun send(
        startSeq: Long,
        packets: Long,
    ) {
        notify(
            ByteBuffer
                .allocate(13)
                .order(ByteOrder.BIG_ENDIAN)
                .put(NOTIFY_READ_BEGIN.toByte())
                .putLong(startSeq)
                .putInt(packets.toInt())
                .array(),
        )
        yield()
        val bytes = ByteArray((packets * RingProtocol.RECORD_BYTES).toInt())
        for (i in 0 until packets.toInt()) {
            (records[startSeq + i] ?: ByteArray(RingProtocol.RECORD_BYTES))
                .copyInto(bytes, i * RingProtocol.RECORD_BYTES)
        }
        val payload = mtu - 4 // ATT payload (MTU - 3) less the type byte
        var sent = 0
        while (sent < bytes.size) {
            val n = minOf(payload, bytes.size - sent)
            notify(byteArrayOf(NOTIFY_DATA.toByte()) + bytes.copyOfRange(sent, sent + n))
            sent += n
            sentBytes = sent.toLong()
            if (stallAfterBytes?.let { sent >= it } == true) {
                sentBytes += unseenBytesAtStall
                awaitCancellation()
            }
            yield()
        }
        if (autoAdvance) moveReadTo(startSeq + sentBytes / RingProtocol.RECORD_BYTES)
        transfer = null
        notify(
            ByteBuffer
                .allocate(10)
                .order(ByteOrder.BIG_ENDIAN)
                .put(NOTIFY_DONE.toByte())
                .put(RingStatus.OK.toByte())
                .putLong(startSeq + packets)
                .array(),
        )
    }

    private fun endTransfer(saveProgress: Boolean) {
        val running = transfer ?: return
        running.cancel()
        transfer = null
        if (saveProgress &&
            autoAdvance
        ) {
            moveReadTo(maxOf(readSeq, transferStart + sentBytes / RingProtocol.RECORD_BYTES))
        }
    }

    private companion object {
        const val CMD_STOP = 0x03
        const val CMD_INFO = 0x10
        const val CMD_READ = 0x11
        const val CMD_ADVANCE = 0x12
        const val CMD_CLEAR = 0x13
        const val NOTIFY_ACK = 0x01
        const val NOTIFY_INFO = 0x02
        const val NOTIFY_DATA = 0x03
        const val NOTIFY_DONE = 0x04
        const val NOTIFY_READ_BEGIN = 0x05
    }
}
