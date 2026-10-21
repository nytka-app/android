package io.github.nytka_app.transcribe

import android.content.Context
import android.os.BatteryManager
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.nytka_app.capture.PendantFactory
import io.github.nytka_app.core.chunks.ChunkFormat
import io.github.nytka_app.core.chunks.ChunkReader
import io.github.nytka_app.core.transcribe.DraftTranscription
import io.github.nytka_app.core.transcribe.SpeechSegments
import io.github.nytka_app.core.transcribe.TranscriberEngine
import io.github.nytka_app.core.transcribe.WordErrors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.math.abs

/** One benchmark run over the fake pendant's text-to-speech recording. */
data class BenchmarkResult(
    val model: String,
    val loadMs: Long,
    val audioMs: Long,
    val workMs: Long,
    val idleRssMb: Long,
    val peakRssMb: Long,
    /** Battery spent per hour of recognising non-stop; null on a charger or when the phone reports no current. */
    val batteryPercentPerHour: Double?,
    val wordErrorRate: Double,
    /** What the model heard in the synthetic recording; never a person's voice. */
    val text: String,
) {
    val realTimeFactor: Double get() = workMs.toDouble() / audioMs

    /** Battery for one hour of speech: recognition runs only over speech, at [realTimeFactor]. */
    val batteryPercentPerSpeechHour: Double? get() = batteryPercentPerHour?.let { it * realTimeFactor }
}

/** Developer mode: times the installed model on this phone. */
interface TranscriptionBenchmark {
    suspend fun run(): BenchmarkResult
}

class AndroidTranscriptionBenchmark
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val models: ModelLibrary,
        private val engine: TranscriberEngine,
    ) : TranscriptionBenchmark {
        override suspend fun run(): BenchmarkResult =
            withContext(Dispatchers.Default) {
                val model = checkNotNull(models.current()) { "Import a model first." }
                val frames =
                    ChunkReader
                        .readAll(context.assets.open(PendantFactory.FAKE_ASSET).use { it.readBytes() })
                        .flatMap { it.frames }
                val segments = SpeechSegments.find(frames)
                val idle = rssKb()
                Sampler(context).use { sampler ->
                    val loadStart = SystemClock.elapsedRealtime()
                    engine.open(model).use { transcriber ->
                        val loadMs = SystemClock.elapsedRealtime() - loadStart
                        var audioMs = 0L
                        var workMs = 0L
                        var text = ""
                        MediaCodecOpusDecoder().use { decoder ->
                            var pass = 0
                            while (pass < MIN_PASSES || audioMs < MIN_AUDIO_MS) {
                                val heard = StringBuilder()
                                for (range in segments) {
                                    val start = SystemClock.elapsedRealtime()
                                    val samples = DraftTranscription.decode(frames, range, decoder)
                                    heard.append(transcriber.transcribe(samples).trim()).append(' ')
                                    workMs += SystemClock.elapsedRealtime() - start
                                    audioMs += range.count() * ChunkFormat.FRAME_MS.toLong()
                                }
                                text = heard.toString().trim()
                                pass++
                            }
                        }
                        BenchmarkResult(
                            model = model.name,
                            loadMs = loadMs,
                            audioMs = audioMs,
                            workMs = workMs,
                            idleRssMb = idle / KB_PER_MB,
                            peakRssMb = sampler.peakRssKb / KB_PER_MB,
                            batteryPercentPerHour = sampler.percentPerHour(),
                            wordErrorRate = WordErrors.rate(REFERENCE, text),
                            text = text,
                        )
                    }
                }
            }

        /** Reads resident memory and battery current every [PERIOD_MS] on its own thread while a run lasts. */
        private class Sampler(
            context: Context,
        ) : AutoCloseable {
            private val battery = context.getSystemService(BatteryManager::class.java)
            private val currents = mutableListOf<Long>()

            @Volatile private var running = true

            @Volatile var peakRssKb = rssKb()
                private set

            private val thread =
                Thread {
                    while (running) {
                        peakRssKb = maxOf(peakRssKb, rssKb())
                        battery?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)?.let {
                            if (it != Long.MIN_VALUE && it != 0L) synchronized(currents) { currents += it }
                        }
                        Thread.sleep(PERIOD_MS)
                    }
                }.apply {
                    isDaemon = true
                    start()
                }

            fun percentPerHour(): Double? {
                if (battery == null || battery.isCharging) return null
                val microAmps = synchronized(currents) { currents.map(::abs).average() }
                val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val counter = battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                if (microAmps.isNaN() || level <= 0 || counter <= 0) return null
                // The charge counter is what is left, in µAh, so the full capacity is that over the level.
                val capacityMicroAh = counter * 100.0 / level
                return microAmps / capacityMicroAh * 100
            }

            override fun close() {
                running = false
                thread.join()
            }
        }

        private companion object {
            const val MIN_PASSES = 3
            const val MIN_AUDIO_MS = 60_000L
            const val PERIOD_MS = 50L
            const val KB_PER_MB = 1024

            /** What `fake_pendant.nytk` was synthesised from (docs/plans/2026-09-29-v0.1-android.md, Task 8). */
            const val REFERENCE =
                "This is the Nytka fake pendant. It replays a short recording, so you can test the app " +
                    "without an Omi necklace. Every word you read in the transcript came from this sentence."

            fun rssKb(): Long =
                File("/proc/self/status")
                    .useLines { lines -> lines.firstOrNull { it.startsWith("VmRSS:") } }
                    ?.split(Regex("\\s+"))
                    ?.getOrNull(1)
                    ?.toLongOrNull() ?: 0
        }
    }
