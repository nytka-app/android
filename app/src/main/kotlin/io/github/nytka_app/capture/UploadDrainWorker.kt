package io.github.nytka_app.capture

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.nytka_app.core.queue.FrameQueue
import io.github.nytka_app.core.upload.BookmarkUploader
import io.github.nytka_app.core.upload.DrainResult
import io.github.nytka_app.core.upload.Uploader
import java.util.concurrent.TimeUnit

/**
 * Uploads what the capture service left behind: after it stopped, after the app was killed, or
 * after a reboot before the pendant came back. Seals leftover frames first.
 */
@HiltWorker
class UploadDrainWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val queue: FrameQueue,
        private val uploader: Uploader,
        private val bookmarkUploader: BookmarkUploader,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            queue.seal()
            val bookmarks = bookmarkUploader.drain()
            return when (uploader.drain()) {
                DrainResult.Empty, is DrainResult.Paused ->
                    if (bookmarks is DrainResult.Failed) Result.retry() else Result.success()
                is DrainResult.Failed -> Result.retry()
            }
        }

        companion object {
            private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

            fun schedule(context: Context) {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "drain",
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<UploadDrainWorker>(15, TimeUnit.MINUTES)
                        .setConstraints(online)
                        .build(),
                )
            }

            fun drainNow(context: Context) {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "drain-now",
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<UploadDrainWorker>()
                        .setConstraints(online)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                        .build(),
                )
            }
        }
    }
