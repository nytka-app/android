package io.github.nytka_app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.core.api.MemoriesApi
import io.github.nytka_app.core.api.MemoriesClient
import io.github.nytka_app.core.api.NytkaApi

@Module
@InstallIn(SingletonComponent::class)
object MemoriesModule {
    @Provides
    fun memoriesClient(api: NytkaApi): MemoriesClient = MemoriesApi(api)
}
