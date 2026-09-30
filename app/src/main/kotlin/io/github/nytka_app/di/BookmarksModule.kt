package io.github.nytka_app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.core.api.BookmarksApi
import io.github.nytka_app.core.api.BookmarksClient
import io.github.nytka_app.core.api.NytkaApi
import io.github.nytka_app.core.queue.BookmarkOutbox
import io.github.nytka_app.core.queue.QueueDatabase
import io.github.nytka_app.core.settings.SettingsStore
import io.github.nytka_app.core.upload.BookmarkUploader
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BookmarksModule {
    @Provides
    fun bookmarksClient(api: NytkaApi): BookmarksClient = BookmarksApi(api)

    @Provides
    @Singleton
    fun bookmarkOutbox(database: QueueDatabase): BookmarkOutbox = BookmarkOutbox(database.bookmarks())

    @Provides
    @Singleton
    fun bookmarkUploader(
        outbox: BookmarkOutbox,
        client: BookmarksClient,
        settings: SettingsStore,
    ): BookmarkUploader = BookmarkUploader(outbox, client, settings.settings)
}
