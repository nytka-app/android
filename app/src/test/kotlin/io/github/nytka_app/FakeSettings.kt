package io.github.nytka_app

import io.github.nytka_app.core.settings.Settings
import io.github.nytka_app.core.settings.SettingsSource
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettings(
    initial: Settings = Settings(),
) : SettingsSource {
    val state = MutableStateFlow(initial)
    override val settings = state

    override suspend fun current() = state.value

    override suspend fun update(transform: (Settings) -> Settings) {
        state.value = transform(state.value)
    }
}
