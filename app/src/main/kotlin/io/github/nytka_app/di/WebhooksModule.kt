package io.github.nytka_app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.core.api.NytkaApi
import io.github.nytka_app.core.api.WebhooksApi
import io.github.nytka_app.core.api.WebhooksClient

@Module
@InstallIn(SingletonComponent::class)
object WebhooksModule {
    @Provides
    fun webhooksClient(api: NytkaApi): WebhooksClient = WebhooksApi(api)
}
