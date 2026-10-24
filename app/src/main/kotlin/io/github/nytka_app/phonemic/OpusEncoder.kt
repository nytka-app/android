package io.github.nytka_app.phonemic

import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat

/**
 * The platform Opus encoder (`audio/opus`, Android 10 and later), set to what the chunk format's codec 21 holds:
 * 16 kHz mono, one packet per 20 ms frame. Used from one thread.
 */
class OpusEncoder {
    private val codec: MediaCodec = MediaCodec.createByCodecName(requireNotNull(encoderName()) { NO_ENCODER })
    private var ptsUs = 0L

    init {
        codec.configure(format(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    /** Feeds one 20 ms frame of 16-bit PCM and returns the packets that are ready, in order. */
    fun encode(pcm: ByteArray): List<ByteArray> {
        val input = codec.dequeueInputBuffer(TIMEOUT_US)
        if (input >= 0) {
            codec.getInputBuffer(input)!!.apply {
                clear()
                put(pcm)
            }
            codec.queueInputBuffer(input, 0, pcm.size, ptsUs, 0)
            ptsUs += FRAME_US
        }
        val packets = mutableListOf<ByteArray>()
        val info = MediaCodec.BufferInfo()
        while (true) {
            val output = codec.dequeueOutputBuffer(info, 0)
            if (output < 0) break
            // The first buffer is the Opus header, not audio.
            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                val buffer = codec.getOutputBuffer(output)!!
                buffer.position(info.offset)
                packets += ByteArray(info.size).also { buffer.get(it) }
            }
            codec.releaseOutputBuffer(output, false)
        }
        return packets
    }

    fun release() {
        codec.stop()
        codec.release()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_SAMPLES = 320
        const val FRAME_BYTES = FRAME_SAMPLES * 2
        private const val FRAME_US = 20_000L
        private const val BIT_RATE = 32_000
        private const val TIMEOUT_US = 10_000L
        private const val NO_ENCODER = "This phone has no Opus encoder."

        private fun format() =
            MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, SAMPLE_RATE, 1).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, FRAME_BYTES)
            }

        private fun encoderName(): String? =
            MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format())

        /** Whether this phone can encode what the chunk format needs; without it the switch stays off. */
        fun available(): Boolean = encoderName() != null
    }
}
