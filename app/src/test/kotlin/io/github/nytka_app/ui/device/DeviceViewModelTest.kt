package io.github.nytka_app.ui.device

import io.github.nytka_app.FakeDeviceActions
import io.github.nytka_app.FakePendantSettingsControls
import io.github.nytka_app.FakeSettings
import io.github.nytka_app.FakeSyncControls
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.capture.PairedPendant
import io.github.nytka_app.capture.PendantSettingsState
import io.github.nytka_app.capture.StorageSyncStatus
import io.github.nytka_app.capture.SyncState
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.api.ServerSetting
import io.github.nytka_app.core.api.ServerSettingsClient
import io.github.nytka_app.core.settings.MuteSchedule
import io.github.nytka_app.core.settings.MuteWindow
import io.github.nytka_app.core.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

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
    private val sync = FakeSyncControls()
    private val pendantSettings = FakePendantSettingsControls()
    private var infoCalls = 0
    private var info: ApiResult<ServerInfo> =
        ApiResult.Ok(
            ServerInfo("0.1.0", 1, features = listOf(ServerInfo.FEATURE_OFFLINE_SYNC)),
        )

    private class FakeServerSettings : ServerSettingsClient {
        var catalog =
            listOf(
                ServerSetting("mute.windows", "json", "[]"),
                ServerSetting("user.timeZone", "string", null, default = "UTC"),
            )
        var failure: ApiResult.Failure? = null
        val sent = mutableListOf<Map<String, String?>>()

        override suspend fun settings(): ApiResult<List<ServerSetting>> = failure ?: ApiResult.Ok(catalog)

        override suspend fun update(values: Map<String, String?>): ApiResult<List<ServerSetting>> {
            sent += values
            failure?.let { return it }
            catalog = catalog.map { if (it.key in values) it.copy(value = values[it.key]) else it }
            return ApiResult.Ok(catalog)
        }
    }

    private val server = FakeServerSettings()
    private var phoneZone = "Europe/Kyiv"
    private val weekdays =
        MuteSchedule(
            listOf(MuteWindow(DayOfWeek.entries.take(5).toSet(), LocalTime.of(9, 30), LocalTime.of(10, 0))),
        )
    private val weekdaysJson = """[{"days":[1,2,3,4,5],"start":"09:30","end":"10:00"}]"""

    private fun viewModel() =
        DeviceViewModel(settings, {
            infoCalls++
            info
        }, actions, sync, server, { phoneZone }, pendantSettings)

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
    fun `saving a read token is refused and the old settings stay`() {
        val viewModel = viewModel()
        val before = settings.state.value
        info = ApiResult.Ok(ServerInfo("0.2.0", 1, scope = "read"))

        viewModel.save("https://other.example", "r".repeat(48), privateNetwork = false)

        assertEquals("The app needs an admin token.", viewModel.state.value.saveError)
        assertEquals(before.serverUrl, settings.state.value.serverUrl)
        assertEquals(before.token, settings.state.value.token)
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

        info = ApiResult.Ok(ServerInfo("0.1.0", 1, features = listOf(ServerInfo.FEATURE_OFFLINE_SYNC)))
        viewModel.checkServer()

        assertFalse(viewModel.state.value.serverUnreachable)
        assertEquals("Connected to Nytka server 0.1.0", viewModel.state.value.serverState)
    }

    @Test
    fun `the storage card follows the sync and drives it`() {
        val viewModel = viewModel()
        assertEquals(
            "Nothing stored",
            viewModel.state.value.storage!!
                .state,
        )
        assertEquals(
            StorageAction.SyncNow,
            viewModel.state.value.storage!!
                .action,
        )

        sync.sync.value = StorageSyncStatus(SyncState.Syncing, storedPackets = 2_000, runDone = 500, runTotal = 2_000)
        assertEquals(
            StorageAction.Stop,
            viewModel.state.value.storage!!
                .action,
        )
        viewModel.stopSync()
        sync.sync.value = StorageSyncStatus(SyncState.Idle, storedPackets = 100)
        viewModel.syncNow()

        assertEquals(listOf("stop", "sync now"), sync.calls)
    }

    @Test
    fun `sync now is not offered while the pendant is away`() {
        sync.link.value = false

        assertEquals(
            StorageAction.None,
            viewModel()
                .state.value.storage!!
                .action,
        )
    }

    @Test
    fun `the card is hidden without a pendant and on a server without offline sync`() {
        val viewModel = viewModel()
        sync.sync.value = StorageSyncStatus(SyncState.ServerOutdated(60_000))
        assertNull(viewModel.state.value.storage)

        sync.sync.value = StorageSyncStatus()
        assertTrue(viewModel.state.value.storage != null)
        settings.state.value = settings.state.value.copy(pendantAddress = null, pendantName = null)
        assertNull(viewModel.state.value.storage)
    }

    @Test
    fun `an unsupported pendant keeps the card with its reason and no actions`() {
        sync.sync.value = StorageSyncStatus(SyncState.Unsupported("Offline sync needs firmware 3.0.20 or later."))

        val card = viewModel().state.value.storage!!

        assertEquals("Offline sync needs firmware 3.0.20 or later.", card.state)
        assertEquals(StorageAction.None, card.action)
    }

    @Test
    fun `the backlog question is asked once and the answer goes to the sync`() {
        val viewModel = viewModel()
        assertNull(viewModel.state.value.backlogPackets)

        sync.sync.value = StorageSyncStatus(SyncState.AwaitingBacklog(100_000))
        assertEquals(100_000L, viewModel.state.value.backlogPackets)
        assertEquals(
            StorageAction.AnswerBacklog,
            viewModel.state.value.storage!!
                .action,
        )

        viewModel.importBacklog()
        sync.sync.value = StorageSyncStatus(SyncState.Syncing)
        assertNull(viewModel.state.value.backlogPackets)
        assertEquals(listOf("import"), sync.calls)
    }

    @Test
    fun `discarding the backlog is passed on`() {
        viewModel().discardBacklog()

        assertEquals(listOf("discard"), sync.calls)
    }

    @Test
    fun `the card is hidden when the server does not list offline sync`() {
        info = ApiResult.Ok(ServerInfo("0.2.0", 1))

        assertNull(viewModel().state.value.storage)
    }

    @Test
    fun `a failed check hides the card again`() {
        val viewModel = viewModel()
        assertTrue(viewModel.state.value.storage != null)

        info = ApiResult.Failure(FailureKind.Network, "failed to connect")
        viewModel.checkServer()

        assertNull(viewModel.state.value.storage)
    }

    @Test
    fun `the backlog question waits while the pendant is away`() {
        sync.sync.value = StorageSyncStatus(SyncState.AwaitingBacklog(100_000))
        sync.link.value = false

        val viewModel = viewModel()

        assertNull(viewModel.state.value.backlogPackets)
        assertEquals(
            StorageAction.None,
            viewModel.state.value.storage!!
                .action,
        )
    }

    private fun failedReason(viewModel: DeviceViewModel) =
        (viewModel.state.value.muteServer as MuteServerState.Failed).reason

    @Test
    fun `saving the schedule pushes it to the server and says so`() {
        val viewModel = viewModel()

        viewModel.setMuteSchedule(weekdays)

        assertEquals(weekdays, settings.state.value.muteSchedule)
        assertEquals(mapOf("mute.windows" to weekdaysJson), server.sent.last())
        assertEquals(MuteServerState.Applied, viewModel.state.value.muteServer)
    }

    @Test
    fun `clearing the schedule pushes an empty array`() {
        settings.state.value = settings.state.value.copy(muteSchedule = weekdays)
        val viewModel = viewModel()

        viewModel.setMuteSchedule(MuteSchedule())

        assertEquals(mapOf("mute.windows" to "[]"), server.sent.last())
    }

    @Test
    fun `the start pushes the saved schedule when the server holds another and skips it when equal`() {
        settings.state.value = settings.state.value.copy(muteSchedule = weekdays)
        viewModel()
        assertEquals(listOf(mapOf<String, String?>("mute.windows" to weekdaysJson)), server.sent)

        server.sent.clear()
        viewModel()
        assertTrue(server.sent.isEmpty())
    }

    @Test
    fun `a failed push says why in plain words and the next start retries`() {
        server.failure = ApiResult.Failure(FailureKind.Network, "offline")
        val viewModel = viewModel()

        viewModel.setMuteSchedule(weekdays)

        assertTrue(failedReason(viewModel).contains("could not be reached"))

        server.failure = null
        viewModel()
        assertEquals(weekdaysJson, server.sent.last()["mute.windows"])
    }

    @Test
    fun `a server without mute windows gets a quiet message`() {
        server.catalog = listOf(ServerSetting("llm.model", "string", "x"))
        val viewModel = viewModel()
        assertTrue(failedReason(viewModel).contains("does not know mute windows"))

        server.failure = ApiResult.Failure(FailureKind.NotFound, "not found")
        viewModel.setMuteSchedule(weekdays)

        assertTrue(failedReason(viewModel).contains("does not know mute windows"))
        assertNull(viewModel.state.value.timeZoneHint)
    }

    @Test
    fun `a utc server and a phone in another zone show the hint and one tap fixes it`() {
        val viewModel = viewModel()
        assertEquals("Europe/Kyiv", viewModel.state.value.timeZoneHint)

        viewModel.setServerTimeZone()

        assertEquals(mapOf("user.timeZone" to "Europe/Kyiv"), server.sent.last())
        assertNull(viewModel.state.value.timeZoneHint)
    }

    @Test
    fun `no hint when the server has the phone's zone or the phone is on utc`() {
        server.catalog = server.catalog.map { if (it.key == "user.timeZone") it.copy(value = "Europe/Kyiv") else it }
        assertNull(viewModel().state.value.timeZoneHint)

        server.catalog = server.catalog.map { if (it.key == "user.timeZone") it.copy(value = null) else it }
        phoneZone = "UTC"
        assertNull(viewModel().state.value.timeZoneHint)
    }

    @Test
    fun `a refused time zone change keeps the hint and explains`() {
        val viewModel = viewModel()
        server.failure = ApiResult.Failure(FailureKind.Forbidden, "no")

        viewModel.setServerTimeZone()

        assertEquals("Europe/Kyiv", viewModel.state.value.timeZoneHint)
        assertTrue(
            viewModel.state.value.timeZoneError!!
                .contains("cannot change server settings"),
        )
    }

    @Test
    fun `the pendant settings card is hidden until the pendant reports a setting`() {
        val viewModel = viewModel()
        assertNull(viewModel.state.value.pendantSettings)

        pendantSettings.current.value = PendantSettingsState(led = 50, gain = 6)

        assertEquals(PendantSettingsState(led = 50, gain = 6), viewModel.state.value.pendantSettings)
    }

    @Test
    fun `the card shows the one setting the pendant reports`() {
        val viewModel = viewModel()

        pendantSettings.current.value = PendantSettingsState(gain = 3)

        assertEquals(
            3,
            viewModel.state.value.pendantSettings
                ?.gain,
        )
        assertNull(
            viewModel.state.value.pendantSettings
                ?.led,
        )
    }

    @Test
    fun `the card is hidden while the pendant is not connected`() {
        val viewModel = viewModel()
        pendantSettings.current.value = PendantSettingsState(led = 50, gain = 6)

        sync.link.value = false

        assertNull(viewModel.state.value.pendantSettings)
    }

    @Test
    fun `the card carries a failed write's message`() {
        val viewModel = viewModel()

        pendantSettings.current.value = PendantSettingsState(led = 50, failures = 1, error = "Could not save.")

        assertEquals(
            "Could not save.",
            viewModel.state.value.pendantSettings
                ?.error,
        )
    }

    @Test
    fun `slider commits go to the pendant settings`() {
        val viewModel = viewModel()

        viewModel.commitLed(30)
        viewModel.commitGain(2)

        assertEquals(listOf("led 30", "gain 2"), pendantSettings.calls)
    }

    @Test
    fun `the slider words show percent, decibels and the two warnings`() {
        assertEquals("40 %", ledLabel(40))
        assertEquals("Level 6 of 8, +20 dB", gainLabel(6))
        assertEquals("Level 1 of 8, -20 dB", gainLabel(1))
        assertEquals("Level 3 of 8, 0 dB", gainLabel(3))
        assertEquals("Level 0, muted", gainLabel(0))
        assertTrue(ledWarning(0)!!.contains("dark"))
        assertNull(ledWarning(1))
        assertTrue(gainWarning(0)!!.contains("silence"))
        assertNull(gainWarning(1))
    }
}
