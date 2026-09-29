package io.github.nytka_app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.capture.IdleSyncControls
import io.github.nytka_app.capture.SyncControls
import javax.inject.Singleton

/** What the screens use to reach the offline sync; its own module, so `AppModule` stays as it is. */
@Module
@InstallIn(SingletonComponent::class)
object SyncControlsModule {
    @Provides
    @Singleton
    fun syncControls(): SyncControls = IdleSyncControls()
}
