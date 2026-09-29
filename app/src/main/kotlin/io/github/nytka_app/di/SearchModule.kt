package io.github.nytka_app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.core.api.NytkaApi
import io.github.nytka_app.core.api.SearchApi
import io.github.nytka_app.core.api.SearchClient

@Module
@InstallIn(SingletonComponent::class)
object SearchModule {
    @Provides
    fun searchClient(api: NytkaApi): SearchClient = SearchApi(api)
}
