package io.github.nytka_app.ui.firstrun

import io.github.nytka_app.FakeDeviceActions
import io.github.nytka_app.FakeSettings
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.capture.PairedPendant
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.ServerInfo
import org.junit.Assert.assertEquals
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
    private val viewModel =
        FirstRunViewModel(settings, {
            infoCalls++
            info
        }, actions)

    private val token = "t".repeat(48)

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
        viewModel.permissionsDone()

        viewModel.acceptConsent()
        assertEquals(FirstRunStep.Consent, viewModel.state.value.step)

        viewModel.setConsent(true)
        viewModel.acceptConsent()
        assertEquals(FirstRunStep.Pairing, viewModel.state.value.step)
        assertTrue(settings.state.value.consentGiven)
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
