package io.github.nytka_app.quiet

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.AudioLevelClient
import io.github.nytka_app.core.api.DayLevel
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.settings.SettingsSource
import java.time.Clock
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Posts the notice; false when it could not (no notification permission), so the next run tries again. */
fun interface QuietPoster {
    fun post(level: DayLevel): Boolean
}

/**
 * One run of the quiet-day check. From [FROM] in the evening, on a server that lists `audio-level`, asks how loud
 * today's speech was and posts one notice for a day the server calls quiet. It tells, and never changes the gain.
 * A failure or a day without the wearer's speech does nothing: the next run is an hour later.
 */
class QuietDayCheck(
    private val info: InfoClient,
    private val client: AudioLevelClient,
    private val settings: SettingsSource,
    private val poster: QuietPoster,
    private val clock: Clock,
) {
    suspend fun run() {
        val now = clock.instant().atZone(clock.zone)
        if (now.toLocalTime() < FROM) return
        val today = now.toLocalDate()
        if (settings.current().quietNoticeDay == today.toString()) return
        val server = (info.info() as? ApiResult.Ok)?.value ?: return
        if (!server.has(ServerInfo.FEATURE_AUDIO_LEVEL)) return
        val level = (client.level(today) as? ApiResult.Ok)?.value ?: return
        if (!level.quiet || !poster.post(level)) return
        settings.update { it.copy(quietNoticeDay = today.toString()) }
    }

    companion object {
        /** Late enough that most of the day's speech is on the server. */
        val FROM: LocalTime = LocalTime.of(20, 0)
    }
}

/** Every hour; a run before [QuietDayCheck.FROM] or after today's notice ends at once, without the network. */
@HiltWorker
class QuietDayWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val info: InfoClient,
        private val client: AudioLevelClient,
        private val settings: SettingsSource,
        private val clock: Clock,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            QuietDayCheck(info, client, settings, { QuietNotifications.post(applicationContext, it) }, clock).run()
            return Result.success()
        }

        companion object {
            const val NAME = "quiet-day"

            fun schedule(context: Context) {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<QuietDayWorker>(1, TimeUnit.HOURS)
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .build(),
                )
            }
        }
    }
