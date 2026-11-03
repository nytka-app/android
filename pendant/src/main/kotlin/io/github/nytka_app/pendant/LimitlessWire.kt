package io.github.nytka_app.pendant

/**
 * The protobuf basics the Limitless encoders and parsers share (see [LimitlessProtocol] for where the format comes
 * from). UNTESTED on real hardware.
 */
internal object LimitlessWire {
    const val VARINT = 0
    const val FIXED64 = 1
    const val LENGTH_DELIMITED = 2
    const val FIXED32 = 5

    /** Field 1 as a varint: a page's clock, and a button event's code. */
    const val TAG_FIELD_1 = 0x08

    fun varint(value: Long): ByteArray {
        var rest = value
        val out = ArrayList<Byte>()
        while (rest > 0x7f || rest < 0) {
            out += ((rest and 0x7f) or 0x80).toByte()
            rest = rest ushr 7
        }
        out += rest.toByte()
        return out.toByteArray()
    }

    /** The value at [at] and the position after it; a varint cut short by the end of [data] ends there. */
    fun readVarint(
        data: ByteArray,
        at: Int,
    ): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var pos = at
        while (pos < data.size && shift < Long.SIZE_BITS) {
            val byte = data[pos++].toInt() and 0xFF
            result = result or ((byte.toLong() and 0x7f) shl shift)
            if (byte and 0x80 == 0) break
            shift += 7
        }
        return result to pos
    }

    private fun field(
        number: Int,
        wireType: Int,
        value: ByteArray,
    ) = varint(((number shl 3) or wireType).toLong()) + value

    fun intField(
        number: Int,
        value: Long,
    ) = field(number, VARINT, varint(value))

    fun message(
        number: Int,
        body: ByteArray,
    ) = field(number, LENGTH_DELIMITED, varint(body.size.toLong()) + body)

    /** Whether a field of [length] bytes that starts at [from] ends inside the first [end] bytes. */
    fun fits(
        length: Long,
        from: Int,
        end: Int,
    ) = length in 0..(end - from).toLong()

    /** The position after the field at [at], or null for a wire type it cannot skip. */
    fun skipField(
        data: ByteArray,
        at: Int,
        end: Int,
    ): Int? =
        when (data[at].toInt() and 0x07) {
            VARINT -> readVarint(data, at + 1).second
            FIXED64 -> at + 1 + Long.SIZE_BYTES
            FIXED32 -> at + 1 + Int.SIZE_BYTES
            LENGTH_DELIMITED -> {
                val (length, next) = readVarint(data, at + 1)
                if (fits(length, next, end)) next + length.toInt() else end
            }

            else -> null
        }
}
