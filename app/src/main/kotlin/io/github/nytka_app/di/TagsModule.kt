package io.github.nytka_app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.core.api.NytkaApi
import io.github.nytka_app.core.api.TagsApi
import io.github.nytka_app.core.api.TagsClient

@Module
@InstallIn(SingletonComponent::class)
object TagsModule {
    @Provides
    fun tagsClient(api: NytkaApi): TagsClient = TagsApi(api)
}
