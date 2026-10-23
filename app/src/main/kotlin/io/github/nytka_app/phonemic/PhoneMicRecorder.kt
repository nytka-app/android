package io.github.nytka_app.phonemic

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat

/**
 * Reads the phone's microphone in 20 ms frames and hands each Opus packet on with the phone's clock at the moment its
 * frame was read. Runs on its own thread between [start] and [stop]; [onPacket] is called on that thread.
 * `VOICE_RECOGNITION` asks Android for the microphone without gain control or noise suppression.
 */
class PhoneMicRecorder(
    private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onPacket: (packet: ByteArray, capturedAtMs: Long) -> Unit,
    private val onFailure: (String) -> Unit,
) {
    @Volatile private var running = false
    private var thread: Thread? = null

    /** False without the permission or the encoder; nothing is recorded then. */
    fun start(): Boolean {
        if (running) return true
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted || !OpusEncoder.available()) return false
        running = true
        thread = Thread(::record, "phone-mic").also { it.start() }
        return true
    }

    fun stop() {
        running = false
        thread?.join(JOIN_MS)
        thread = null
    }

    @SuppressLint("MissingPermission") // checked in start
    @Suppress("TooGenericExceptionCaught") // a codec or audio failure ends the recording, never the app
    private fun record() {
        val minimum =
            AudioRecord.getMinBufferSize(
                OpusEncoder.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        val audio =
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                OpusEncoder.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minimum, OpusEncoder.FRAME_BYTES * BUFFER_FRAMES),
            )
        var encoder: OpusEncoder? = null
        try {
            encoder = OpusEncoder()
            audio.startRecording()
            val frame = ByteArray(OpusEncoder.FRAME_BYTES)
            // The encoder may hold a frame back: each packet takes the time of the oldest frame not yet out.
            val readAt = ArrayDeque<Long>()
            while (running) {
                if (!readFrame(audio, frame)) continue
                readAt.addLast(clock())
                encoder.encode(frame).forEach { packet -> onPacket(packet, readAt.removeFirstOrNull() ?: clock()) }
            }
        } catch (e: Exception) {
            onFailure(e.message ?: e.javaClass.simpleName)
        } finally {
            runCatching { audio.stop() }
            audio.release()
            encoder?.release()
        }
    }

    private fun readFrame(
        audio: AudioRecord,
        frame: ByteArray,
    ): Boolean {
        var filled = 0
        while (running && filled < frame.size) {
            val read = audio.read(frame, filled, frame.size - filled)
            if (read < 0) error("The microphone stopped (code $read).")
            filled += read
        }
        return filled == frame.size
    }

    private companion object {
        const val BUFFER_FRAMES = 10
        const val JOIN_MS = 2_000L
    }
}
