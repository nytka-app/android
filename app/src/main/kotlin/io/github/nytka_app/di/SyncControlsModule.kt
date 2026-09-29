package io.github.nytka_app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.capture.CaptureHub
import io.github.nytka_app.capture.HubSyncControls
import io.github.nytka_app.capture.SyncControls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/** What the screens use to reach the offline sync; its own module, so `AppModule` stays as it is. */
@Module
@InstallIn(SingletonComponent::class)
object SyncControlsModule {
    @Provides
    @Singleton
    fun syncControls(hub: CaptureHub): SyncControls =
        HubSyncControls(
            hub,
            CoroutineScope(
                SupervisorJob() + Dispatchers.Default,
            ),
        )
}
