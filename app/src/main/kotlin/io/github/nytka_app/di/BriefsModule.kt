package io.github.nytka_app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.core.api.BriefsApi
import io.github.nytka_app.core.api.BriefsClient
import io.github.nytka_app.core.api.NytkaApi

@Module
@InstallIn(SingletonComponent::class)
object BriefsModule {
    @Provides
    fun briefsClient(api: NytkaApi): BriefsClient = BriefsApi(api)
}
