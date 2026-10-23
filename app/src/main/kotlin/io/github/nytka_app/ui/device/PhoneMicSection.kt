package io.github.nytka_app.ui.device

import android.Manifest
import android.os.Build
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.nytka_app.R
import io.github.nytka_app.phonemic.PhoneMicStatus
import io.github.nytka_app.ui.ErrorLine
import io.github.nytka_app.ui.PermissionAnswer
import io.github.nytka_app.ui.SettingsBlock
import io.github.nytka_app.ui.SettingsNote
import io.github.nytka_app.ui.SettingsToggleRow
import io.github.nytka_app.ui.rememberPermissionRequest

/**
 * The Phone microphone switch. It reads the service's state, not a stored setting, so it is off after a restart.
 * Turning it on always asks Android first (microphone, and notifications for the ongoing notice), and starts the
 * service from this visible screen, the only place Android lets a microphone service start.
 */
@Composable
internal fun PhoneMicSection(
    state: DeviceUiState,
    onSwitch: (Boolean) -> Unit,
    onPermission: (PermissionAnswer) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val permissions =
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
    val request = rememberPermissionRequest(permissions, onAnswer = onPermission)
    val on = state.phoneMic == PhoneMicStatus.Recording || state.phoneMic == PhoneMicStatus.Paused
    SettingsBlock(stringResource(R.string.phone_mic_title)) {
        if (!state.phoneMicAvailable) {
            SettingsNote(stringResource(R.string.phone_mic_no_encoder))
            return@SettingsBlock
        }
        SettingsToggleRow(
            stringResource(R.string.phone_mic_label),
            checked = on,
            onCheckedChange = { next -> if (next) request() else onSwitch(false) },
            supporting = stringResource(R.string.phone_mic_explanation),
        )
        (state.phoneMic as? PhoneMicStatus.Failed)?.let {
            ErrorLine(
                stringResource(R.string.phone_mic_failed, it.reason),
            )
        }
        when (state.phoneMicPermission) {
            PermissionAnswer.Denied -> ErrorLine(stringResource(R.string.phone_mic_denied))

            PermissionAnswer.Blocked -> {
                ErrorLine(stringResource(R.string.phone_mic_blocked))
                OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings)) }
            }

            else -> Unit
        }
    }
}
