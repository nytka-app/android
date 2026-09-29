package io.github.nytka_app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.capture.MuteLogRecorder
import io.github.nytka_app.core.diagnostics.EventLog
import io.github.nytka_app.core.queue.FrameQueue
import io.github.nytka_app.core.ring.CaptureTimes
import io.github.nytka_app.core.ring.RingCaptureTimes
import javax.inject.Singleton

/** The offline sync's seams: its own module, so `AppModule` stays as it is. */
@Module
@InstallIn(SingletonComponent::class)
object StorageSyncModule {
    @Provides
    fun captureTimes(): CaptureTimes = RingCaptureTimes()

    /** One recorder, so the service and the hub agree on the last mute state they logged. */
    @Provides
    @Singleton
    fun muteLogRecorder(
        queue: FrameQueue,
        log: EventLog,
    ): MuteLogRecorder = MuteLogRecorder(queue, log = log)
}
