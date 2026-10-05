package io.github.nytka_app.briefs

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
import io.github.nytka_app.core.api.BriefsClient
import io.github.nytka_app.core.api.UpcomingBrief
import io.github.nytka_app.core.settings.SettingsSource
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/** Posts one brief; false when it could not (no notification permission), so it is tried again next run. */
fun interface BriefPoster {
    fun post(brief: UpcomingBrief): Boolean
}

/**
 * One run of the job. Posts a notification for each brief not posted before and not over, remembers the ids in
 * settings (only those still listed, at most [MAX_KEPT]), and does nothing on a failure: the next run is soon enough.
 */
class BriefPoll(
    private val client: BriefsClient,
    private val settings: SettingsSource,
    private val poster: BriefPoster,
    private val clock: Clock,
) {
    suspend fun run() {
        val before = settings.current()
        if (!before.briefNotifications) return
        val listed = (client.upcoming(WINDOW_MINUTES) as? ApiResult.Ok)?.value ?: return
        val made = listed.mapNotNull { event -> event.brief?.let { it.id to event } }
        val now = clock.instant()
        val posted =
            made
                .filter { (id, event) -> id !in before.notifiedBriefs && !event.isOver(now) }
                .filter { (_, event) -> poster.post(event) }
                .map { it.first }
        val listedIds = made.map { it.first }.toSet()
        val kept = (before.notifiedBriefs.filter { it in listedIds } + posted).takeLast(MAX_KEPT)
        settings.update { it.copy(notifiedBriefs = kept) }
    }

    private fun UpcomingBrief.isOver(now: Instant): Boolean = parseTime(endsAt)?.let { it <= now } ?: false

    /** The server writes UTC times, with `Z` or, from some stores, with no offset at all. */
    private fun parseTime(text: String): Instant? =
        runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(text).toInstant(ZoneOffset.UTC) }.getOrNull()

    companion object {
        /** The largest `calendar.briefMinutes` the server allows. */
        const val WINDOW_MINUTES = 240
        const val MAX_KEPT = 50
    }
}

/** Every 15 minutes while the setting is on. A failure ends the run quietly: a brief is not worth a retry storm. */
@HiltWorker
class BriefWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val client: BriefsClient,
        private val settings: SettingsSource,
        private val clock: Clock,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            BriefPoll(client, settings, { BriefNotifications.post(applicationContext, it, clock.zone) }, clock).run()
            return Result.success()
        }

        companion object {
            const val NAME = "briefs"

            fun schedule(context: Context) {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<BriefWorker>(15, TimeUnit.MINUTES)
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .build(),
                )
            }

            fun cancel(context: Context) {
                WorkManager.getInstance(context).cancelUniqueWork(NAME)
            }
        }
    }

/** What the Device screen asks of the job; a fake in tests. */
interface BriefScheduler {
    fun schedule()

    fun cancel()
}

class WorkBriefScheduler(
    private val context: Context,
) : BriefScheduler {
    override fun schedule() = BriefWorker.schedule(context)

    override fun cancel() = BriefWorker.cancel(context)
}
