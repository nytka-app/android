package io.github.nytka_app.ui.device

import io.github.nytka_app.FakeDeviceActions
import io.github.nytka_app.FakeSettings
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.capture.PairedPendant
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.settings.Settings
import io.github.nytka_app.ui.PermissionAnswer
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

        viewModel.localNetworkAnswered(PermissionAnswer.Granted)

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
    fun `a refusal leaves a note beside a server that is out of reach`() {
        saveServerOnTheLocalNetwork()
        actions.localNetwork = false
        info = ApiResult.Failure(FailureKind.Network, "failed to connect")
        val viewModel = viewModel()

        viewModel.localNetworkAnswered(PermissionAnswer.Denied)

        assertEquals(PermissionAnswer.Denied, viewModel.state.value.localNetwork)
        assertEquals("failed to connect", viewModel.state.value.serverState)
    }

    @Test
    fun `a server that answers needs no note, over a VPN say`() {
        saveServerOnTheLocalNetwork()
        actions.localNetwork = false
        val viewModel = viewModel()

        viewModel.localNetworkAnswered(PermissionAnswer.Denied)

        assertNull(viewModel.state.value.localNetwork)
        assertEquals("Connected to Nytka server 0.1.0", viewModel.state.value.serverState)
    }

    @Test
    fun `a refusal for good is still asked about, since Android decides whether to show the prompt`() {
        saveServerOnTheLocalNetwork()
        actions.localNetwork = false
        info = ApiResult.Failure(FailureKind.Network, "failed to connect")
        val viewModel = viewModel()
        viewModel.localNetworkAnswered(PermissionAnswer.Blocked)
        val checksBefore = infoCalls

        viewModel.checkServer()

        assertTrue(viewModel.state.value.askLocalNetwork)
        assertEquals(checksBefore, infoCalls)
        viewModel.localNetworkAnswered(PermissionAnswer.Denied)
        assertEquals(PermissionAnswer.Blocked, viewModel.state.value.localNetwork)
        viewModel.openSettings()
        assertEquals(listOf("open settings"), actions.calls)
    }

    @Test
    fun `a refusal that repeats leads to the system settings`() {
        saveServerOnTheLocalNetwork()
        actions.localNetwork = false
        info = ApiResult.Failure(FailureKind.Network, "failed to connect")
        val viewModel = viewModel()
        viewModel.localNetworkAnswered(PermissionAnswer.Denied)
        assertEquals(PermissionAnswer.Denied, viewModel.state.value.localNetwork)

        viewModel.checkServer()
        viewModel.localNetworkAnswered(PermissionAnswer.Denied)

        assertEquals(PermissionAnswer.Blocked, viewModel.state.value.localNetwork)
    }

    @Test
    fun `allowing it in the system settings clears the note on the next check`() {
        saveServerOnTheLocalNetwork()
        actions.localNetwork = false
        info = ApiResult.Failure(FailureKind.Network, "failed to connect")
        val viewModel = viewModel()
        viewModel.localNetworkAnswered(PermissionAnswer.Blocked)

        actions.localNetwork = true
        info = ApiResult.Ok(ServerInfo("0.1.0", 1))
        viewModel.checkServer()

        assertNull(viewModel.state.value.localNetwork)
        assertEquals("Connected to Nytka server 0.1.0", viewModel.state.value.serverState)
    }
}
