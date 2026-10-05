package io.github.nytka_app.ui.device

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.nytka_app.R
import io.github.nytka_app.ui.PermissionAnswer
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
    Section(stringResource(R.string.briefs_title)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(
                checked = state.briefNotifications,
                onCheckedChange = { on -> if (on && needsPermission) request() else onSwitch(on) },
            )
            Text(stringResource(R.string.briefs_label))
        }
        Text(stringResource(R.string.briefs_explanation), style = MaterialTheme.typography.bodySmall)
        when (state.calendarLine) {
            CalendarLine.NoFeed ->
                Text(
                    stringResource(R.string.briefs_no_feed),
                    style = MaterialTheme.typography.bodySmall,
                )
            CalendarLine.NeedsUpdate ->
                Text(
                    stringResource(R.string.server_needs_update),
                    style = MaterialTheme.typography.bodySmall,
                )
            null -> Unit
        }
        when (state.briefPermission) {
            PermissionAnswer.Denied ->
                Text(stringResource(R.string.briefs_permission_denied), color = MaterialTheme.colorScheme.error)

            PermissionAnswer.Blocked -> {
                Text(stringResource(R.string.briefs_permission_blocked), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings)) }
            }

            else -> Unit
        }
    }
}
