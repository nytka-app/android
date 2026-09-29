package io.github.nytka_app.ui.device

import io.github.nytka_app.FakeDeviceActions
import io.github.nytka_app.FakeSettings
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.capture.PairedPendant
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
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
    private var infoCalls = 0
    private var info: ApiResult<ServerInfo> = ApiResult.Ok(ServerInfo("0.1.0", 1))

    private fun viewModel() =
        DeviceViewModel(settings, {
            infoCalls++
            info
        }, actions)

    private fun saveServerOnTheLocalNetwork() {
        settings.state.value = settings.state.value.copy(serverUrl = "http://192.168.1.10:8080/", privateNetwork = true)
    }

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

    @Test
    fun `saving a server on the local network asks for the permission, then checks it`() {
        actions.localNetwork = false
        val viewModel = viewModel()
        val checksBefore = infoCalls

        viewModel.save("http://192.168.1.10:8080", "", privateNetwork = true)

        assertTrue(viewModel.state.value.askLocalNetwork)
        assertEquals(checksBefore, infoCalls)
        assertEquals("http://192.168.1.10:8080/", settings.state.value.serverUrl)

        viewModel.localNetworkAnswered()

        assertFalse(viewModel.state.value.askLocalNetwork)
        assertEquals(checksBefore + 1, infoCalls)
        assertEquals("Connected to Nytka server 0.1.0", viewModel.state.value.serverState)
    }

    @Test
    fun `opening the tab asks when the saved server is on the local network`() {
        saveServerOnTheLocalNetwork()
        actions.localNetwork = false

        val viewModel = viewModel()

        assertTrue(viewModel.state.value.askLocalNetwork)
        assertEquals(0, infoCalls)
    }

    @Test
    fun `a public server is never asked about`() {
        actions.localNetwork = false

        val viewModel = viewModel()

        assertFalse(viewModel.state.value.askLocalNetwork)
        assertEquals(1, infoCalls)
    }

    @Test
    fun `nothing is asked once Nytka may use the local network`() {
        saveServerOnTheLocalNetwork()

        val viewModel = viewModel()

        assertFalse(viewModel.state.value.askLocalNetwork)
        assertEquals(1, infoCalls)
    }

    @Test
    fun `a refusal still checks the server, and one out of reach shows the hint`() {
        saveServerOnTheLocalNetwork()
        actions.localNetwork = false
        info = ApiResult.Failure(FailureKind.Network, "failed to connect")
        val viewModel = viewModel()

        viewModel.localNetworkAnswered()

        assertEquals("failed to connect", viewModel.state.value.serverState)
        assertTrue(viewModel.state.value.serverUnreachable)
    }

    @Test
    fun `a server that answers needs no hint, over a VPN say`() {
        saveServerOnTheLocalNetwork()
        actions.localNetwork = false
        val viewModel = viewModel()

        viewModel.localNetworkAnswered()

        assertFalse(viewModel.state.value.serverUnreachable)
        assertEquals("Connected to Nytka server 0.1.0", viewModel.state.value.serverState)
    }

    @Test
    fun `the prompt is asked for again on each check until the permission is granted`() {
        saveServerOnTheLocalNetwork()
        actions.localNetwork = false
        info = ApiResult.Failure(FailureKind.Network, "failed to connect")
        val viewModel = viewModel()
        viewModel.localNetworkAnswered()
        val checksBefore = infoCalls

        viewModel.checkServer()

        assertTrue(viewModel.state.value.askLocalNetwork)
        assertEquals(checksBefore, infoCalls)
    }

    @Test
    fun `a check that fails on the network marks the server unreachable, whatever its address`() {
        actions.localNetwork = false
        info = ApiResult.Failure(FailureKind.Network, "failed to connect")

        val viewModel = viewModel()

        assertFalse(viewModel.state.value.askLocalNetwork)
        assertTrue(viewModel.state.value.serverUnreachable)
        assertEquals("failed to connect", viewModel.state.value.serverState)
    }

    @Test
    fun `a server that answers with a refusal is reachable`() {
        info = ApiResult.Failure(FailureKind.Unauthorized, "The server refused the token.")

        assertFalse(viewModel().state.value.serverUnreachable)
    }

    @Test
    fun `the next check clears the mark`() {
        info = ApiResult.Failure(FailureKind.Network, "failed to connect")
        val viewModel = viewModel()
        assertTrue(viewModel.state.value.serverUnreachable)

        info = ApiResult.Ok(ServerInfo("0.1.0", 1))
        viewModel.checkServer()

        assertFalse(viewModel.state.value.serverUnreachable)
        assertEquals("Connected to Nytka server 0.1.0", viewModel.state.value.serverState)
    }
}
