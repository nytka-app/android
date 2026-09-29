package io.github.nytka_app.ui

import io.github.nytka_app.FakeDeviceActions
import io.github.nytka_app.FakeSettings
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.core.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val actions = FakeDeviceActions()

    @Test
    fun `starts capture at launch once first run is done and a pendant is paired`() {
        val viewModel = AppViewModel(FakeSettings(Settings(onboarded = true, pendantAddress = "AA:BB")), actions)

        assertEquals(true, viewModel.onboarded.value)
        assertEquals(listOf("start"), actions.calls)
    }

    @Test
    fun `waits for first run otherwise`() {
        val viewModel = AppViewModel(FakeSettings(), actions)

        assertEquals(false, viewModel.onboarded.value)
        assertEquals(emptyList<String>(), actions.calls)
    }
}
