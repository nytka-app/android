package io.github.nytka_app.pendant

import io.github.nytka_app.pendant.LimitlessWire.TAG_FIELD_1
import io.github.nytka_app.pendant.LimitlessWire.VARINT
import io.github.nytka_app.pendant.LimitlessWire.readVarint

/**
 * What the Limitless pendant says on RX besides audio: the flash state (the answer to `GetDeviceStatus`) and a button
 * event. Ported from `BasedHardware/omi` at afad3b7 (MIT licensed, license in that repository):
 * `app/android/app/src/main/kotlin/com/friend/ios/limitless/LimitlessFlashDrainEngine.kt` for the status and
 * `app/lib/services/devices/limitless_connection.dart` for the button. Both are found by scanning for field `0x22`, as
 * that source does. UNTESTED on real hardware.
 */
object LimitlessStatus {
    private const val TAG_STATUS = 0x22
    private const val TAG_STATUS_INNER = 0x2a
    private const val TAG_BUTTON = 0x42

    private const val BUTTON_DOUBLE = 3L
    private const val MIN_STATUS = 20
    private const val MIN_STATUS_BODY = 10
    private const val MIN_INNER = 5
    private const val MAX_INNER = 500
    private const val MAX_FLASH = 200
    private const val COUNTERS = 5
    private const val MIN_BUTTON = 10
    private const val MAX_BUTTON = 50

    /** The flash state in a `GetDeviceStatus` answer (a notification, whole): field 0x22, then 0x2a, then 0x2a. */
    fun parseFlash(data: ByteArray): LimitlessFlash? {
        if (data.size < MIN_STATUS) return null
        var pos = 0
        while (pos < data.size - 5) {
            if (data[pos].toInt() == TAG_STATUS) return statusIn(data, pos + 1)
            pos++
        }
        return null
    }

    private fun statusIn(
        data: ByteArray,
        at: Int,
    ): LimitlessFlash? {
        val (length, next) = readVarint(data, at)
        if (length < MIN_STATUS_BODY || length > data.size - next) return null
        val end = next + length.toInt()
        var pos = next
        while (pos < end - 5) {
            if (data[pos].toInt() == TAG_STATUS_INNER) {
                val (size, after) = readVarint(data, pos + 1)
                if (size < MIN_INNER || size > MAX_INNER || after + size > data.size) return null
                return flashIn(data, after, after + size.toInt())
            }
            pos++
        }
        return null
    }

    private fun flashIn(
        data: ByteArray,
        start: Int,
        end: Int,
    ): LimitlessFlash? {
        var pos = start
        while (pos < end - 1) {
            if (data[pos].toInt() == TAG_STATUS_INNER) {
                val (size, after) = readVarint(data, pos + 1)
                if (size < 0 || size > MAX_FLASH || after + size > data.size) return null
                return counters(data, after, after + size.toInt())
            }
            pos++
        }
        return null
    }

    private fun counters(
        data: ByteArray,
        start: Int,
        end: Int,
    ): LimitlessFlash? {
        val values = LongArray(COUNTERS)
        var found = false
        var pos = start
        while (pos < end - 1) {
            val marker = data[pos++].toInt() and 0xFF
            val slot = (marker shr 3) - 1
            if (marker and 0x07 == VARINT && slot in 0 until COUNTERS) {
                val (value, next) = readVarint(data, pos)
                values[slot] = value
                pos = next
                found = true
            }
        }
        return if (found) LimitlessFlash(values[0], values[1], values[2], values[3], values[4]) else null
    }

    /**
     * The button in a notification: only a double press is told apart (field 0x22, then 0x42, then field 1 with the
     * event, 3 for a double press). A short press and a long press are the pendant's own business.
     */
    fun parseButton(data: ByteArray): ButtonEvent? {
        if (data.size < MIN_BUTTON) return null
        var pos = 0
        while (pos < data.size - 5) {
            if (data[pos].toInt() == TAG_STATUS) return buttonIn(data, pos + 1)
            pos++
        }
        return null
    }

    private fun buttonIn(
        data: ByteArray,
        at: Int,
    ): ButtonEvent? {
        val (length, next) = readVarint(data, at)
        if (length < 2 || length > data.size - next || next >= data.size) return null
        if ((data[next].toInt() and 0xFF) != TAG_BUTTON || next + 1 >= data.size) return null
        val (size, after) = readVarint(data, next + 1)
        if (size < 2 || size > MAX_BUTTON || after + size > data.size) return null
        val end = after + size.toInt()
        var pos = after
        while (pos < end - 1) {
            if (data[pos].toInt() == TAG_FIELD_1) {
                val event = readVarint(data, pos + 1).first
                return if (event == BUTTON_DOUBLE) ButtonEvent.DoubleTap else null
            }
            pos++
        }
        return null
    }
}
