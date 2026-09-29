package io.github.nytka_app.ui.firstrun

import io.github.nytka_app.FakeDeviceActions
import io.github.nytka_app.FakeSettings
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.capture.PairedPendant
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.settings.FirstRunStep
import io.github.nytka_app.core.settings.Settings
import io.github.nytka_app.ui.PermissionAnswer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FirstRunViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val settings = FakeSettings()
    private val actions = FakeDeviceActions()
    private var infoCalls = 0
    private var info: ApiResult<ServerInfo> = ApiResult.Ok(ServerInfo("0.1.0", 1))

    private fun newViewModel() =
        FirstRunViewModel(settings, {
            infoCalls++
            info
        }, actions)

    // Built inside the test: the view model launches in its constructor, and Main is only set once the rule runs.
    private val viewModel by lazy { newViewModel() }

    private val token = "t".repeat(48)

    @Test
    fun `a fresh install starts at the server step`() {
        val state = viewModel.state.value

        assertTrue(state.loaded)
        assertEquals(FirstRunStep.Server, state.step)
        assertEquals("", state.url)
        assertEquals("", state.token)
    }

    @Test
    fun `a good connection test saves the server and moves on`() {
        viewModel.edit(" https://nytka.example ", token, privateNetwork = false)
        viewModel.testConnection()

        assertEquals(FirstRunStep.Permissions, viewModel.state.value.step)
        assertEquals("0.1.0", viewModel.state.value.serverVersion)
        assertEquals("https://nytka.example/", settings.state.value.serverUrl)
        assertEquals(token, settings.state.value.token)
    }

    @Test
    fun `a read token is refused and not kept`() {
        info = ApiResult.Ok(ServerInfo("0.2.0", 1, scope = "read"))
        viewModel.edit("https://nytka.example", token, privateNetwork = false)

        viewModel.testConnection()

        assertEquals(FirstRunStep.Server, viewModel.state.value.step)
        assertEquals("The app needs an admin token.", viewModel.state.value.serverError)
        assertEquals("", settings.state.value.token)
    }

    @Test
    fun `a server without scope counts as admin`() {
        info = ApiResult.Ok(ServerInfo("0.1.0", 1))
        viewModel.edit("https://nytka.example", token, privateNetwork = false)

        viewModel.testConnection()

        assertEquals(FirstRunStep.Permissions, viewModel.state.value.step)
    }

    @Test
    fun `a refused token stays on the server step with the reason`() {
        info = ApiResult.Failure(FailureKind.Unauthorized, "The server refused the token.")
        viewModel.edit("https://nytka.example", token, privateNetwork = false)

        viewModel.testConnection()

        assertEquals(FirstRunStep.Server, viewModel.state.value.step)
        assertEquals("The server refused the token.", viewModel.state.value.serverError)
    }

    @Test
    fun `plain http without the switch never reaches the network`() {
        viewModel.edit("http://10.0.0.5:8080", token, privateNetwork = false)

        viewModel.testConnection()

        assertTrue(
            viewModel.state.value.serverError!!
                .contains("private network"),
        )
        assertEquals(0, infoCalls)
    }

    @Test
    fun `a server on the local network asks for the permission before the test`() {
        actions.localNetwork = false
        viewModel.edit("http://192.168.1.10:8080", token, privateNetwork = true)

        viewModel.testConnection()

        assertTrue(viewModel.state.value.askLocalNetwork)
        assertEquals(0, infoCalls)
        assertEquals(FirstRunStep.Server, viewModel.state.value.step)
    }

    @Test
    fun `a public server never asks`() {
        actions.localNetwork = false
        viewModel.edit("https://nytka.example", token, privateNetwork = false)

        viewModel.testConnection()

        assertFalse(viewModel.state.value.askLocalNetwork)
        assertEquals(1, infoCalls)
    }

    @Test
    fun `nothing is asked once Nytka may use the local network`() {
        viewModel.edit("http://192.168.1.10:8080", token, privateNetwork = true)

        viewModel.testConnection()

        assertFalse(viewModel.state.value.askLocalNetwork)
        assertEquals(FirstRunStep.Permissions, viewModel.state.value.step)
    }

    @Test
    fun `allowing it runs the test`() {
        actions.localNetwork = false
        viewModel.edit("http://192.168.1.10:8080", token, privateNetwork = true)
        viewModel.testConnection()
        actions.localNetwork = true

        viewModel.localNetworkAnswered()

        assertFalse(viewModel.state.value.askLocalNetwork)
        assertEquals(1, infoCalls)
        assertEquals(FirstRunStep.Permissions, viewModel.state.value.step)
    }

    @Test
    fun `a refusal still runs the test, and a server out of reach shows the hint`() {
        actions.localNetwork = false
        info = ApiResult.Failure(FailureKind.Network, "failed to connect")
        viewModel.edit("http://192.168.1.10:8080", token, privateNetwork = true)
        viewModel.testConnection()

        viewModel.localNetworkAnswered()

        assertEquals("failed to connect", viewModel.state.value.serverError)
        assertTrue(viewModel.state.value.serverUnreachable)
        assertEquals(FirstRunStep.Server, viewModel.state.value.step)
    }

    @Test
    fun `a refusal does not hold back a server that answers, over a VPN say`() {
        actions.localNetwork = false
        viewModel.edit("http://100.66.77.88:8080", token, privateNetwork = true)
        viewModel.testConnection()

        viewModel.localNetworkAnswered()

        assertEquals(FirstRunStep.Permissions, viewModel.state.value.step)
        assertFalse(viewModel.state.value.serverUnreachable)
    }

    @Test
    fun `the prompt is asked for again on each test until the permission is granted`() {
        actions.localNetwork = false
        info = ApiResult.Failure(FailureKind.Network, "failed to connect")
        viewModel.edit("http://192.168.1.10:8080", token, privateNetwork = true)
        viewModel.testConnection()
        viewModel.localNetworkAnswered()

        viewModel.testConnection()

        assertTrue(viewModel.state.value.askLocalNetwork)
        assertEquals(1, infoCalls)
    }

    @Test
    fun `a name that gives no sign of the local network is not asked about before the test`() {
        actions.localNetwork = false
        info = ApiResult.Failure(FailureKind.Network, "failed to connect")
        viewModel.edit("https://nas.example.com", token, privateNetwork = false)

        viewModel.testConnection()

        assertFalse(viewModel.state.value.askLocalNetwork)
        assertEquals(1, infoCalls)
    }

    @Test
    fun `a test that fails on the network marks the server unreachable, whatever its address`() {
        info = ApiResult.Failure(FailureKind.Network, "failed to connect")
        viewModel.edit("https://nas.example.com", token, privateNetwork = false)

        viewModel.testConnection()

        assertTrue(viewModel.state.value.serverUnreachable)
        assertEquals("failed to connect", viewModel.state.value.serverError)
    }

    @Test
    fun `a server that answers with a refusal is reachable`() {
        info = ApiResult.Failure(FailureKind.Unauthorized, "The server refused the token.")
        viewModel.edit("https://nytka.example", token, privateNetwork = false)

        viewModel.testConnection()

        assertFalse(viewModel.state.value.serverUnreachable)
    }

    @Test
    fun `the next test clears the mark`() {
        info = ApiResult.Failure(FailureKind.Network, "failed to connect")
        viewModel.edit("https://nas.example.com", token, privateNetwork = false)
        viewModel.testConnection()
        assertTrue(viewModel.state.value.serverUnreachable)

        info = ApiResult.Ok(ServerInfo("0.1.0", 1))
        viewModel.testConnection()

        assertFalse(viewModel.state.value.serverUnreachable)
        assertEquals(FirstRunStep.Permissions, viewModel.state.value.step)
    }

    @Test
    fun `consent needs the box ticked`() {
        viewModel.edit("https://nytka.example", token, privateNetwork = false)
        viewModel.testConnection()
        viewModel.permissionsAnswered(PermissionAnswer.Granted)

        viewModel.acceptConsent()
        assertEquals(FirstRunStep.Consent, viewModel.state.value.step)

        viewModel.setConsent(true)
        viewModel.acceptConsent()
        assertEquals(FirstRunStep.Pairing, viewModel.state.value.step)
        assertTrue(settings.state.value.consentGiven)
    }

    @Test
    fun `each step is saved as the user moves on`() {
        viewModel.edit("https://nytka.example", token, privateNetwork = false)
        viewModel.testConnection()
        assertEquals(FirstRunStep.Permissions, settings.state.value.firstRunStep)

        viewModel.permissionsAnswered(PermissionAnswer.Granted)
        assertEquals(FirstRunStep.Consent, settings.state.value.firstRunStep)

        viewModel.setConsent(true)
        viewModel.acceptConsent()
        assertEquals(FirstRunStep.Pairing, settings.state.value.firstRunStep)
    }

    @Test
    fun `a failed connection test leaves the saved step at the server`() {
        info = ApiResult.Failure(FailureKind.Network, "timeout")
        viewModel.edit("https://nytka.example", token, privateNetwork = false)

        viewModel.testConnection()

        assertEquals(FirstRunStep.Server, settings.state.value.firstRunStep)
    }

    @Test
    fun `a restart resumes at the step the user reached`() {
        viewModel.edit("https://nytka.example", token, privateNetwork = false)
        viewModel.testConnection()
        viewModel.permissionsAnswered(PermissionAnswer.Granted)

        val restarted = newViewModel()

        assertEquals(FirstRunStep.Consent, restarted.state.value.step)
        assertTrue(restarted.state.value.loaded)
    }

    @Test
    fun `a restart on the server step brings back the address and token a test saved`() {
        info = ApiResult.Failure(FailureKind.Network, "timeout")
        viewModel.edit("http://10.0.0.5:8080", token, privateNetwork = true)
        viewModel.testConnection()

        val state = newViewModel().state.value

        assertEquals(FirstRunStep.Server, state.step)
        assertEquals("http://10.0.0.5:8080/", state.url)
        assertEquals(token, state.token)
        assertTrue(state.privateNetwork)
    }

    @Test
    fun `first run resumes at pairing once consent was given`() {
        settings.state.value = Settings(consentGiven = true, firstRunStep = FirstRunStep.Pairing)

        assertEquals(FirstRunStep.Pairing, newViewModel().state.value.step)
    }

    @Test
    fun `allowing nearby devices moves on to consent`() {
        viewModel.permissionsAnswered(PermissionAnswer.Granted)

        assertEquals(FirstRunStep.Consent, viewModel.state.value.step)
        assertNull(viewModel.state.value.permissions)
    }

    @Test
    fun `a refusal keeps the user on the permissions step to be asked again`() {
        viewModel.edit("https://nytka.example", token, privateNetwork = false)
        viewModel.testConnection()

        viewModel.permissionsAnswered(PermissionAnswer.Denied)

        assertEquals(FirstRunStep.Permissions, viewModel.state.value.step)
        assertEquals(PermissionAnswer.Denied, viewModel.state.value.permissions)
        assertEquals(emptyList<String>(), actions.calls)
    }

    @Test
    fun `a refusal Android will not repeat leads to the system settings`() {
        viewModel.permissionsAnswered(PermissionAnswer.Blocked)

        assertEquals(PermissionAnswer.Blocked, viewModel.state.value.permissions)
        viewModel.openSettings()
        assertEquals(listOf("open settings"), actions.calls)
    }

    @Test
    fun `a refusal that repeats leads to the system settings on the permissions step too`() {
        viewModel.permissionsAnswered(PermissionAnswer.Denied)
        assertEquals(PermissionAnswer.Denied, viewModel.state.value.permissions)

        viewModel.permissionsAnswered(PermissionAnswer.Denied)

        assertEquals(PermissionAnswer.Blocked, viewModel.state.value.permissions)
    }

    @Test
    fun `allowing it after a refusal moves on`() {
        viewModel.permissionsAnswered(PermissionAnswer.Blocked)

        viewModel.permissionsAnswered(PermissionAnswer.Granted)

        assertEquals(FirstRunStep.Consent, viewModel.state.value.step)
    }

    @Test
    fun `pairing finishes first run and starts capture`() {
        viewModel.paired(PairedPendant("AA:BB:CC:DD:EE:FF", "Omi"))

        assertTrue(viewModel.state.value.done)
        assertTrue(settings.state.value.onboarded)
        assertEquals("AA:BB:CC:DD:EE:FF", settings.state.value.pendantAddress)
        assertEquals(listOf("start"), actions.calls)
    }

    @Test
    fun `setting up later finishes without a pendant`() {
        viewModel.skipPairing()

        assertTrue(viewModel.state.value.done)
        assertTrue(settings.state.value.onboarded)
        assertEquals(emptyList<String>(), actions.calls)
    }

    @Test
    fun `the consent text is the spec's`() {
        assertEquals(
            "Recording people without their consent is illegal in some places. " +
                "You are responsible for following the law where you use Nytka. " +
                "The pendant also records while the phone is away.",
            CONSENT_TEXT,
        )
    }
}
