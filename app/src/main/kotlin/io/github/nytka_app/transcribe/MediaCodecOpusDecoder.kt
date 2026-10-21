package io.github.nytka_app.transcribe

import android.media.MediaCodec
import android.media.MediaFormat
import io.github.nytka_app.core.transcribe.Decimator
import io.github.nytka_app.core.transcribe.OpusDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes the pendant's Opus (16 kHz mono, 20 ms frames) with Android's own decoder, which every phone since
 * Android 10 has, so the app ships no codec of its own. That decoder answers at 48 kHz; [Decimator] brings it to
 * 16 kHz. One frame in, its samples out, synchronously.
 */
class MediaCodecOpusDecoder : OpusDecoder {
    private val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
    private val info = MediaCodec.BufferInfo()
    private var decimator: Decimator? = null
    private var timeUs = 0L

    init {
        val format =
            MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, DECODER_RATE, 1).apply {
                setByteBuffer("csd-0", opusHead())
                setByteBuffer("csd-1", nanos(0))
                setByteBuffer("csd-2", nanos(SEEK_PREROLL_NS))
            }
        codec.configure(format, null, null, 0)
        codec.start()
    }

    override fun decode(frame: ByteArray): FloatArray {
        val input = codec.dequeueInputBuffer(TIMEOUT_US)
        if (input < 0) return FloatArray(0)
        codec.getInputBuffer(input)!!.apply {
            clear()
            put(frame)
        }
        codec.queueInputBuffer(input, 0, frame.size, timeUs, 0)
        timeUs += FRAME_US
        return output()
    }

    /** Waits for the frame just queued; the decoder answers each frame with one buffer, after any format change. */
    private fun output(): FloatArray {
        repeat(MAX_WAITS) {
            val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> decimator = null
                index >= 0 -> return take(index)
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
            }
        }
        return FloatArray(0)
    }

    private fun take(index: Int): FloatArray {
        val buffer = codec.getOutputBuffer(index)!!
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        val pcm = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val samples = FloatArray(pcm.remaining()) { pcm.get().toFloat() / SHORT_SCALE }
        codec.releaseOutputBuffer(index, false)
        val rate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val decimator = decimator ?: Decimator(rate / TARGET_RATE).also { decimator = it }
        return decimator.process(samples)
    }

    override fun reset() {
        codec.flush()
        decimator?.reset()
    }

    override fun close() {
        runCatching { codec.stop() }
        codec.release()
    }

    private companion object {
        const val DECODER_RATE = 48_000
        const val TARGET_RATE = 16_000
        const val PENDANT_RATE = 16_000
        const val FRAME_US = 20_000L
        const val SEEK_PREROLL_NS = 80_000_000L
        const val TIMEOUT_US = 10_000L
        const val MAX_WAITS = 20
        const val SHORT_SCALE = 32_768f

        /** RFC 7845 identification header: one channel, no pre-skip, the pendant's 16 kHz, mapping family 0. */
        fun opusHead(): ByteBuffer =
            ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("OpusHead".toByteArray(Charsets.US_ASCII))
                put(1)
                put(1)
                putShort(0)
                putInt(PENDANT_RATE)
                putShort(0)
                put(0)
                flip()
            }

        fun nanos(value: Long): ByteBuffer =
            ByteBuffer
                .allocate(8)
                .order(ByteOrder.nativeOrder())
                .putLong(value)
                .apply { flip() }
    }
}
