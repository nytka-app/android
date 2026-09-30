package io.github.nytka_app.pendant

import java.nio.ByteBuffer
import java.nio.ByteOrder

object OmiParsing {
    fun codec(value: ByteArray?): Int? = value?.firstOrNull()?.toInt()?.and(0xFF)

    /** The one byte of the LED and gain characteristics. */
    fun setting(value: ByteArray?): Int? = value?.firstOrNull()?.toInt()?.and(0xFF)

    fun battery(value: ByteArray?): Int? = value?.firstOrNull()?.toInt()?.and(0xFF)

    /** The first four bytes of the eight the firmware sends hold a little-endian event code. */
    fun button(value: ByteArray): ButtonEvent? =
        if (value.size <
            4
        ) {
            null
        } else {
            ButtonEvent.fromCode(ByteBuffer.wrap(value, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int)
        }

    fun text(value: ByteArray?): String? =
        value?.toString(Charsets.UTF_8)?.trimEnd('\u0000')?.takeIf { it.isNotBlank() }
}
