package io.github.nytka_app.ui

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.nytka_app.R
import io.github.nytka_app.capture.DeviceActions
import io.github.nytka_app.capture.localNetworkAllowed
import io.github.nytka_app.capture.startAppSettings
import io.github.nytka_app.core.api.LocalNetwork
import okhttp3.HttpUrl

/**
 * True when the screen should show Android's prompt before Nytka contacts the server at [base]: the address looks like
 * one on the local network and Nytka may not use it. Asked again each time: Android shows the prompt only while it is
 * willing to. A server whose address gives no sign of it is caught by [LocalNetworkHint] after it fails to answer.
 */
fun mustAskForLocalNetwork(
    base: HttpUrl,
    privateNetwork: Boolean,
    actions: DeviceActions,
): Boolean = !actions.localNetworkGranted() && LocalNetwork.mayNeedPermission(base, privateNetwork)

/** Shows Android's prompt for the local network whenever [ask] turns true, and calls [onAnswered] when it is over. */
@Composable
fun LocalNetworkPrompt(
    ask: Boolean,
    onAnswered: () -> Unit,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) return
    val request = rememberPermissionRequest(listOf(Manifest.permission.ACCESS_LOCAL_NETWORK)) { onAnswered() }
    LaunchedEffect(ask) { if (ask) request() }
}

/**
 * Goes under a server that cannot be reached ([unreachable]). Android 17 blocks a server on the local network until
 * Nytka may use it, and the address does not always say whether it is on it: a name can resolve to a local address,
 * and an IPv6 address can sit on the phone's own network. So this shows for any address that fails to answer, until the
 * user allows it. It offers the prompt and the system settings, and [onAllowed] runs once they allow it, to try the
 * server again. Nothing shows before Android 17.
 */
@Composable
fun LocalNetworkHint(
    unreachable: Boolean,
    onAllowed: () -> Unit = {},
) {
    if (!unreachable || Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) return
    val context = LocalContext.current
    // Looked at again on return from the system settings, where the user may have allowed it.
    var visits by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { visits++ }
    val allowed = remember(visits) { context.localNetworkAllowed() }
    if (allowed) return
    val allow =
        rememberPermissionRequest(listOf(Manifest.permission.ACCESS_LOCAL_NETWORK)) {
            if (it == PermissionAnswer.Granted) onAllowed()
        }
    Text(
        stringResource(R.string.local_network_blocked_message),
        color = MaterialTheme.colorScheme.error,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = allow) { Text(stringResource(R.string.action_allow)) }
        OutlinedButton(onClick = { context.startAppSettings() }) { Text(stringResource(R.string.open_settings)) }
    }
}
