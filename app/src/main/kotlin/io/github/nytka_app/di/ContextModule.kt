package io.github.nytka_app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.core.api.ContextApi
import io.github.nytka_app.core.api.ContextClient
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.NytkaApi
import io.github.nytka_app.core.queue.ContextOutbox
import io.github.nytka_app.core.queue.QueueDatabase
import io.github.nytka_app.core.settings.SettingsStore
import io.github.nytka_app.core.upload.ContextUploader
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ContextModule {
    @Provides
    fun contextClient(api: NytkaApi): ContextClient = ContextApi(api)

    @Provides
    @Singleton
    fun contextOutbox(database: QueueDatabase): ContextOutbox = ContextOutbox(database.contextOutbox())

    @Provides
    @Singleton
    fun contextUploader(
        outbox: ContextOutbox,
        client: ContextClient,
        info: InfoClient,
        settings: SettingsStore,
    ): ContextUploader = ContextUploader(outbox, client, info, settings.settings)
}
