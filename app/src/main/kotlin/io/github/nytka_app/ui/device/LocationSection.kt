package io.github.nytka_app.ui.device

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.nytka_app.R
import io.github.nytka_app.ui.PermissionAnswer
import io.github.nytka_app.ui.rememberPermissionRequest

/**
 * The Location tags switch. On asks for coarse location first; precise coordinates never leave the phone, only a
 * saved place's name or a city-level label does, as a tag suggestion on the conversation the session becomes.
 */
@Composable
internal fun LocationSection(
    state: DeviceUiState,
    onSwitch: (Boolean) -> Unit,
    onPermission: (PermissionAnswer) -> Unit,
    onOpenSettings: () -> Unit,
    onAddPlace: (String) -> Unit,
    onRemovePlace: (String) -> Unit,
) {
    val context = LocalContext.current
    val request =
        rememberPermissionRequest(listOf(Manifest.permission.ACCESS_COARSE_LOCATION), onAnswer = onPermission)
    val needsPermission =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
    var placeName by rememberSaveable { mutableStateOf("") }
    Section(stringResource(R.string.location_tags_title)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(
                checked = state.locationTags,
                onCheckedChange = { on -> if (on && needsPermission) request() else onSwitch(on) },
            )
            Text(stringResource(R.string.location_tags_label))
        }
        Text(stringResource(R.string.location_tags_explanation), style = MaterialTheme.typography.bodySmall)
        when (state.locationPermission) {
            PermissionAnswer.Denied ->
                Text(stringResource(R.string.location_tags_permission_denied), color = MaterialTheme.colorScheme.error)

            PermissionAnswer.Blocked -> {
                Text(stringResource(R.string.location_tags_permission_blocked), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings)) }
            }

            else -> Unit
        }
        if (state.locationTags) {
            state.savedPlaces.forEach { place ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(place.name)
                    TextButton(onClick = { onRemovePlace(place.name) }) {
                        Text(stringResource(R.string.location_tags_remove_place))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = placeName,
                    onValueChange = { placeName = it },
                    label = { Text(stringResource(R.string.location_tags_place_name)) },
                )
                OutlinedButton(
                    onClick = {
                        onAddPlace(placeName)
                        placeName = ""
                    },
                    enabled = placeName.isNotBlank(),
                ) { Text(stringResource(R.string.location_tags_add_place)) }
            }
        }
    }
}
