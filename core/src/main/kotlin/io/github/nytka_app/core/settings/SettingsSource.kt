package io.github.nytka_app.core.settings

import kotlinx.coroutines.flow.Flow

/** Read and change settings; [SettingsStore] in the app, an in-memory fake in tests. */
interface SettingsSource {
    val settings: Flow<Settings>

    suspend fun current(): Settings

    suspend fun update(transform: (Settings) -> Settings)
}
