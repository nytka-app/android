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
                "You are responsible for following the law where you use Nytka.",
            CONSENT_TEXT,
        )
    }
}
