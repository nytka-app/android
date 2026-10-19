package io.github.nytka_app.ui.device

import io.github.nytka_app.FakeDeviceActions
import io.github.nytka_app.FakePendantSettingsControls
import io.github.nytka_app.FakeSettings
import io.github.nytka_app.FakeSyncControls
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.briefs.BriefScheduler
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.api.ServerSetting
import io.github.nytka_app.core.api.ServerSettingsClient
import io.github.nytka_app.core.settings.Settings
import io.github.nytka_app.ui.PermissionAnswer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DeviceLocationTagsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val settings = FakeSettings(Settings(serverUrl = "https://old.example/", token = "t".repeat(40)))
    private var location: Pair<Double, Double>? = null

    private val noServerSettings =
        object : ServerSettingsClient {
            override suspend fun settings(): ApiResult<List<ServerSetting>> = ApiResult.Ok(emptyList())

            override suspend fun update(values: Map<String, String?>): ApiResult<List<ServerSetting>> =
                ApiResult.Ok(emptyList())
        }
    private val noBriefs =
        object : BriefScheduler {
            override fun schedule() = Unit

            override fun cancel() = Unit
        }

    private fun viewModel() =
        DeviceViewModel(
            settings,
            { ApiResult.Ok(ServerInfo("0.1.0", 1)) },
            FakeDeviceActions(),
            FakeSyncControls(),
            noServerSettings,
            { "UTC" },
            FakePendantSettingsControls(),
            { null },
            noBriefs,
            { location },
        )

    @Test
    fun `switching location tags on saves the setting, off clears any pending place`() {
        val viewModel = viewModel()

        viewModel.setLocationTags(true)
        assertTrue(settings.state.value.locationTags)
        assertTrue(viewModel.state.value.locationTags)

        settings.state.value = settings.state.value.copy(pendingLocationPlace = "Home", pendingLocationAt = 1L)
        viewModel.setLocationTags(false)
        assertFalse(settings.state.value.locationTags)
        assertNull(settings.state.value.pendingLocationPlace)
    }

    @Test
    fun `a granted location prompt switches location tags on and clears the refusal`() {
        val viewModel = viewModel()
        viewModel.locationPermissionAnswered(PermissionAnswer.Denied)

        viewModel.locationPermissionAnswered(PermissionAnswer.Granted)

        assertTrue(settings.state.value.locationTags)
        assertNull(viewModel.state.value.locationPermission)
    }

    @Test
    fun `a denied location prompt is recorded without touching the setting`() {
        val viewModel = viewModel()

        viewModel.locationPermissionAnswered(PermissionAnswer.Denied)

        assertFalse(settings.state.value.locationTags)
        assertEquals(PermissionAnswer.Denied, viewModel.state.value.locationPermission)
    }

    @Test
    fun `saving the current place with no fix does nothing`() {
        location = null
        val viewModel = viewModel()

        viewModel.addSavedPlace("Home")

        assertEquals(emptyList<Any>(), settings.state.value.savedPlaces)
    }

    @Test
    fun `saving the current place under a name already used replaces it`() {
        location = 40.7128 to -74.0060
        val viewModel = viewModel()
        viewModel.addSavedPlace("Home")

        location = 51.5074 to -0.1278
        viewModel.addSavedPlace("Home")

        assertEquals(
            listOf("Home"),
            settings.state.value.savedPlaces
                .map { it.name },
        )
        assertEquals(
            51.5074,
            settings.state.value.savedPlaces
                .single()
                .latitude,
            0.0,
        )
    }

    @Test
    fun `removing a saved place drops it by name`() {
        location = 40.7128 to -74.0060
        val viewModel = viewModel()
        viewModel.addSavedPlace("Home")

        viewModel.removeSavedPlace("Home")

        assertEquals(emptyList<Any>(), settings.state.value.savedPlaces)
    }
}
