package io.github.nytka_app.transcribe

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.nytka_app.capture.AndroidPhoneBattery
import io.github.nytka_app.core.diagnostics.EventLog
import io.github.nytka_app.core.settings.SettingsSource
import io.github.nytka_app.core.transcribe.DraftTranscription
import io.github.nytka_app.core.transcribe.PowerGate
import io.github.nytka_app.core.transcribe.TranscriberEngine
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Turns the audio copies waiting in Room into draft text, every 15 minutes, while [PowerGate] allows it. It reads
 * only `draft_audio`; the upload queue is never touched. A run stops before [BUDGET_MS], inside WorkManager's ten
 * minutes, and the next one carries on. The log gets counts and timings, never text.
 */
@HiltWorker
class DraftTranscriptionWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val settings: SettingsSource,
        private val drafts: DraftTranscription,
        private val models: ModelLibrary,
        private val engine: TranscriberEngine,
        private val log: EventLog,
    ) : CoroutineWorker(context, params) {
        private val battery = AndroidPhoneBattery(context)

        @Suppress("TooGenericExceptionCaught") // a model that will not load is reported, not retried every 15 minutes
        override suspend fun doWork(): Result {
            drafts.prune()
            val current = settings.current()
            if (!PowerGate.allows(current, battery.level, battery.charging) ||
                !engine.available
            ) {
                return Result.success()
            }
            val model = models.current() ?: return Result.success()
            val deadline = System.currentTimeMillis() + BUDGET_MS
            try {
                val started = System.currentTimeMillis()
                engine.open(model).use { transcriber ->
                    log.i(TAG, "model loaded in ${System.currentTimeMillis() - started} ms")
                    MediaCodecOpusDecoder().use { decoder ->
                        val outcome =
                            drafts.run(transcriber, decoder, model.name) {
                                val now = settings.current()
                                !isStopped &&
                                    System.currentTimeMillis() < deadline &&
                                    PowerGate.allows(now, battery.level, battery.charging)
                            }
                        log.i(
                            TAG,
                            "${outcome.chunks} chunks, ${outcome.segments} segments, " +
                                "${outcome.failedSegments} failed, ${outcome.speechMs / 1000} s speech " +
                                "in ${outcome.workMs / 1000} s",
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The class name only: an engine's message may quote a path or, in principle, its input.
                log.w(TAG, "run failed: ${e.javaClass.simpleName}")
            }
            return Result.success()
        }

        companion object {
            private const val TAG = "NytkaDraft"
            private const val NAME = "draft-transcription"
            const val BUDGET_MS = 8L * 60 * 1000

            fun schedule(context: Context) {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<DraftTranscriptionWorker>(15, TimeUnit.MINUTES).build(),
                )
            }

            fun cancel(context: Context) {
                WorkManager.getInstance(context).cancelUniqueWork(NAME)
            }
        }
    }
