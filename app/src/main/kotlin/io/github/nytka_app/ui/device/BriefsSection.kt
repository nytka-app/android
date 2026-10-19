package io.github.nytka_app.ui.device

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import io.github.nytka_app.R
import io.github.nytka_app.ui.ErrorLine
import io.github.nytka_app.ui.PermissionAnswer
import io.github.nytka_app.ui.SettingsBlock
import io.github.nytka_app.ui.SettingsNote
import io.github.nytka_app.ui.SettingsToggleRow
import io.github.nytka_app.ui.rememberPermissionRequest

/** The Meeting briefs switch. On Android 13 and later it asks for notifications before it turns on. */
@Composable
internal fun BriefsSection(
    state: DeviceUiState,
    onSwitch: (Boolean) -> Unit,
    onPermission: (PermissionAnswer) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val request = rememberPermissionRequest(listOf(Manifest.permission.POST_NOTIFICATIONS), onAnswer = onPermission)
    val needsPermission =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
    SettingsBlock(stringResource(R.string.briefs_title)) {
        SettingsToggleRow(
            stringResource(R.string.briefs_label),
            checked = state.briefNotifications,
            onCheckedChange = { on -> if (on && needsPermission) request() else onSwitch(on) },
            supporting = stringResource(R.string.briefs_explanation),
        )
        when (state.calendarLine) {
            CalendarLine.NoFeed -> SettingsNote(stringResource(R.string.briefs_no_feed))
            CalendarLine.NeedsUpdate -> SettingsNote(stringResource(R.string.server_needs_update))
            null -> Unit
        }
        when (state.briefPermission) {
            PermissionAnswer.Denied -> ErrorLine(stringResource(R.string.briefs_permission_denied))

            PermissionAnswer.Blocked -> {
                ErrorLine(stringResource(R.string.briefs_permission_blocked))
                OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings)) }
            }

            else -> Unit
        }
    }
}
