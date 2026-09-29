package io.github.nytka_app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.core.api.NytkaApi
import io.github.nytka_app.core.api.TokensApi
import io.github.nytka_app.core.api.TokensClient

@Module
@InstallIn(SingletonComponent::class)
object TokensModule {
    @Provides
    fun tokensClient(api: NytkaApi): TokensClient = TokensApi(api)
}
