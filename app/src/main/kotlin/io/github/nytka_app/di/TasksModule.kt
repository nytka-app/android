package io.github.nytka_app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.core.api.NytkaApi
import io.github.nytka_app.core.api.TasksApi
import io.github.nytka_app.core.api.TasksClient

@Module
@InstallIn(SingletonComponent::class)
object TasksModule {
    @Provides
    fun tasksClient(api: NytkaApi): TasksClient = TasksApi(api)
}
