package io.github.nytka_app.ui

import android.Manifest
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.github.nytka_app.capture.DeviceActions
import io.github.nytka_app.core.api.LocalNetwork
import okhttp3.HttpUrl

/**
 * True when the screen should show Android's prompt before Nytka contacts the server at [base]: the server may need
 * the permission, Nytka does not have it, and Android has not stopped asking ([lastAnswer]).
 */
fun mustAskForLocalNetwork(
    base: HttpUrl,
    privateNetwork: Boolean,
    lastAnswer: PermissionAnswer?,
    actions: DeviceActions,
): Boolean =
    lastAnswer != PermissionAnswer.Blocked &&
        !actions.localNetworkGranted() &&
        LocalNetwork.mayNeedPermission(base, privateNetwork)

/** Shows Android's prompt for the local network whenever [ask] turns true. Before Android 17 there is none. */
@Composable
fun LocalNetworkPrompt(
    ask: Boolean,
    onAnswer: (PermissionAnswer) -> Unit,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) return
    val request = rememberPermissionRequest(listOf(Manifest.permission.ACCESS_LOCAL_NETWORK), onAnswer = onAnswer)
    LaunchedEffect(ask) { if (ask) request() }
}

/**
 * What to tell a user who has not let Nytka use the local network; [retry] names the button that asks again.
 * Nothing shows when [answer] is null or Granted.
 */
@Composable
fun LocalNetworkNote(
    answer: PermissionAnswer?,
    retry: String,
    onOpenSettings: () -> Unit,
) {
    when (answer) {
        PermissionAnswer.Denied ->
            Text(
                "Android blocks servers on your local network until you allow Nearby devices for Nytka. " +
                    "Tap $retry to be asked again.",
                color = MaterialTheme.colorScheme.error,
            )
        PermissionAnswer.Blocked -> {
            Text(
                "Android blocks servers on your local network until you allow Nearby devices for Nytka, and it no " +
                    "longer asks. Allow it in the system settings for Nytka, then tap $retry.",
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(onClick = onOpenSettings) { Text("Open settings") }
        }
        else -> Unit
    }
}
