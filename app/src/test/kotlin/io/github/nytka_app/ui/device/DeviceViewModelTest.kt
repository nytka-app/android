package io.github.nytka_app.ui.device

import io.github.nytka_app.FakeDeviceActions
import io.github.nytka_app.FakeSettings
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.capture.PairedPendant
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DeviceViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val settings =
        FakeSettings(
            Settings(
                serverUrl = "https://old.example/",
                token = "t".repeat(40),
                pendantAddress = "AA:BB",
                pendantName = "Omi",
            ),
        )
    private val actions = FakeDeviceActions()
    private var info: ApiResult<ServerInfo> = ApiResult.Ok(ServerInfo("0.1.0", 1))

    private fun viewModel() = DeviceViewModel(settings, { info }, actions)

    @Test
    fun `seven taps on the version unlock developer mode`() {
        val viewModel = viewModel()

        repeat(6) { viewModel.tapVersion() }
        assertFalse(settings.state.value.developerMode)
        assertEquals(1, viewModel.state.value.tapsToDeveloper)

        viewModel.tapVersion()
        assertTrue(settings.state.value.developerMode)
    }

    @Test
    fun `saving refuses plain http without the switch`() {
        val viewModel = viewModel()

        viewModel.save("http://10.0.0.5:8080", "", privateNetwork = false)

        assertTrue(
            viewModel.state.value.saveError!!
                .contains("private network"),
        )
        assertEquals("https://old.example/", settings.state.value.serverUrl)
    }

    @Test
    fun `saving keeps the token when the field is left empty`() {
        val viewModel = viewModel()

        viewModel.save("https://new.example", "", privateNetwork = false)

        assertEquals("https://new.example/", settings.state.value.serverUrl)
        assertEquals("t".repeat(40), settings.state.value.token)
        assertNull(viewModel.state.value.saveError)
    }

    @Test
    fun `a server on another api version shows the banner`() {
        info = ApiResult.Ok(ServerInfo("1.0.0", 2))

        val state = viewModel().state.value

        assertTrue(state.apiMismatch)
        assertEquals("Connected to Nytka server 1.0.0", state.serverState)
    }

    @Test
    fun `pairing stores the pendant and restarts capture`() {
        val viewModel = viewModel()

        viewModel.paired(PairedPendant("CC:DD", "Omi 2"))

        assertEquals("CC:DD", settings.state.value.pendantAddress)
        assertEquals(listOf("restart"), actions.calls)
    }

    @Test
    fun `forget clears the pendant and stops capture`() {
        val viewModel = viewModel()

        viewModel.forgetPendant()

        assertNull(settings.state.value.pendantAddress)
        assertEquals(listOf("stop", "forget AA:BB"), actions.calls)
    }
}
