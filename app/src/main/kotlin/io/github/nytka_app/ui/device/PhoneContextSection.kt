package io.github.nytka_app.ui.device

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.nytka_app.R
import io.github.nytka_app.capture.NowPlayingListener
import io.github.nytka_app.capture.TransitionWatcher
import io.github.nytka_app.ui.ErrorLine
import io.github.nytka_app.ui.PermissionAnswer
import io.github.nytka_app.ui.SettingsBlock
import io.github.nytka_app.ui.SettingsNote
import io.github.nytka_app.ui.SettingsToggleRow
import io.github.nytka_app.ui.rememberPermissionRequest

/**
 * The Device tab's switches for phone context: times of speaker playback, calls, the screen and headphones, never
 * what played; and, each off until switched on, activity (Play build only, asks for the permission first) and the
 * kind of app that plays (needs notification access, opened from here).
 */
@Composable
internal fun PhoneContextSection(
    state: DeviceUiState,
    onSwitch: (Boolean) -> Unit,
    onActivity: (Boolean) -> Unit,
    onActivityPermission: (PermissionAnswer) -> Unit,
    onNowPlaying: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val requestActivity =
        rememberPermissionRequest(listOf(Manifest.permission.ACTIVITY_RECOGNITION), onAnswer = onActivityPermission)
    // Notification access is granted in the system settings: look again whenever the screen comes back.
    var visits by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { visits++ }
    val access = remember(visits) { NowPlayingListener.granted(context) }
    SettingsBlock(stringResource(R.string.phone_context_title)) {
        SettingsToggleRow(
            stringResource(R.string.phone_context_label),
            checked = state.phoneContext,
            onCheckedChange = onSwitch,
            supporting = stringResource(R.string.phone_context_explanation),
        )
        if (!state.phoneContext) return@SettingsBlock
        if (TransitionWatcher.AVAILABLE) {
            SettingsToggleRow(
                stringResource(R.string.activity_context_label),
                checked = state.activityContext,
                onCheckedChange = { on -> if (on) requestActivity() else onActivity(false) },
                supporting = stringResource(R.string.activity_context_explanation),
            )
            when (state.activityPermission) {
                PermissionAnswer.Denied -> ErrorLine(stringResource(R.string.activity_context_denied))

                PermissionAnswer.Blocked -> {
                    ErrorLine(stringResource(R.string.activity_context_blocked))
                    OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings)) }
                }

                else -> Unit
            }
        }
        SettingsToggleRow(
            stringResource(R.string.now_playing_context_label),
            checked = state.nowPlayingContext,
            onCheckedChange = onNowPlaying,
            supporting = stringResource(R.string.now_playing_context_explanation),
        )
        if (state.nowPlayingContext && !access) {
            SettingsNote(stringResource(R.string.now_playing_context_access))
            OutlinedButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            ) { Text(stringResource(R.string.now_playing_context_open)) }
        }
    }
}
