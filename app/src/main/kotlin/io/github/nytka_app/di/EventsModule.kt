package io.github.nytka_app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.core.api.EventsApi
import io.github.nytka_app.core.api.EventsClient
import io.github.nytka_app.core.api.NytkaApi

@Module
@InstallIn(SingletonComponent::class)
object EventsModule {
    @Provides
    fun eventsClient(api: NytkaApi): EventsClient = EventsApi(api)
}
