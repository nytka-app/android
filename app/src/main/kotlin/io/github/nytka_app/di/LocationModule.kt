package io.github.nytka_app.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.capture.AndroidCoarseLocationSource
import io.github.nytka_app.capture.CoarseLocationSource
import io.github.nytka_app.core.api.ConversationsClient
import io.github.nytka_app.core.api.TagsClient
import io.github.nytka_app.core.location.LocationTagSync
import io.github.nytka_app.core.settings.SettingsSource

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {
    @Binds
    abstract fun coarseLocationSource(impl: AndroidCoarseLocationSource): CoarseLocationSource

    companion object {
        @Provides
        fun locationTagSync(
            settings: SettingsSource,
            conversations: ConversationsClient,
            tags: TagsClient,
        ): LocationTagSync = LocationTagSync(settings, conversations, tags)
    }
}
