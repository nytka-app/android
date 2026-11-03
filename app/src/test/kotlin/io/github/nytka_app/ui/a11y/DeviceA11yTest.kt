package io.github.nytka_app.ui.a11y

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nytka_app.FakeDeviceActions
import io.github.nytka_app.FakePendantSettingsControls
import io.github.nytka_app.FakePhoneMicControls
import io.github.nytka_app.FakeSettings
import io.github.nytka_app.FakeSyncControls
import io.github.nytka_app.MainDispatcherRule
import io.github.nytka_app.briefs.BriefScheduler
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.api.ServerSetting
import io.github.nytka_app.core.api.ServerSettingsClient
import io.github.nytka_app.core.settings.Settings
import io.github.nytka_app.ui.StatusUiState
import io.github.nytka_app.ui.device.DeviceScreen
import io.github.nytka_app.ui.device.DeviceViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h16000dp")
class DeviceA11yTest {
    @get:Rule(order = 0)
    val main = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val everything =
        ServerInfo(
            "0.36.7",
            1,
            features =
                listOf(
                    ServerInfo.FEATURE_OFFLINE_SYNC,
                    ServerInfo.FEATURE_VOICE,
                    ServerInfo.FEATURE_CONTEXT_RANGES,
                    ServerInfo.FEATURE_CONTEXT_DEVICE,
                    ServerInfo.FEATURE_CONTEXT_ACTIVITY,
                    ServerInfo.FEATURE_CONTEXT_NOW_PLAYING,
                    ServerInfo.FEATURE_PHONE_MIC,
                    ServerInfo.FEATURE_NUDGES,
                ),
        )

    private class NoServerSettings : ServerSettingsClient {
        override suspend fun settings(): ApiResult<List<ServerSetting>> = ApiResult.Ok(emptyList())

        override suspend fun update(values: Map<String, String?>): ApiResult<List<ServerSetting>> =
            ApiResult.Ok(emptyList())
    }

    private class NoBriefs : BriefScheduler {
        override fun schedule() = Unit

        override fun cancel() = Unit
    }

    private fun show(
        fontScale: Float,
        dark: Boolean,
        paired: Boolean,
    ) {
        val settings =
            FakeSettings(
                Settings(
                    serverUrl = "https://nytka.example/",
                    token = "t".repeat(40),
                    pendantAddress = "AA:BB".takeIf { paired },
                    pendantName = "Omi".takeIf { paired },
                    pendantModel = "omi".takeIf { paired },
                ),
            )
        val model =
            DeviceViewModel(
                settings,
                { ApiResult.Ok(everything) },
                FakeDeviceActions(),
                FakeSyncControls(),
                NoServerSettings(),
                { "Europe/Kyiv" },
                FakePendantSettingsControls(),
                { _, _ -> null },
                NoBriefs(),
                { null },
                FakePhoneMicControls(),
            )
        compose.setScreen(fontScale, dark) {
            DeviceScreen(
                status = StatusUiState(serverLine = "Uploaded 2 min ago"),
                onMute = {},
                onOpenDeveloper = {},
                onOpenVoice = {},
                onOpenPendant = {},
                onOpenSignals = {},
                onOpenAboutRecording = {},
                onOpenPhoneTranscription = {},
                viewModel = model,
            )
        }
    }

    private fun clean(
        fontScale: Float,
        dark: Boolean = false,
        paired: Boolean = true,
    ) {
        show(fontScale, dark, paired)
        compose.assertClean()
    }

    @Test
    fun `device at 1_0 in light`() = clean(1f)

    @Test
    fun `device at 1_0 in dark`() = clean(1f, dark = true)

    @Test
    fun `device at 1_3 in light`() = clean(1.3f)

    @Test
    fun `device at 2_0 in light`() = clean(2f)

    @Test
    fun `device at 2_0 in dark`() = clean(2f, dark = true)

    @Test
    fun `device with no pendant at 2_0`() = clean(2f, paired = false)
}
