package io.github.nytka_app.ui

import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** How the user answered Android's prompt for a permission. */
enum class PermissionAnswer {
    Granted,

    /** Refused, and Android shows the prompt again when asked. */
    Denied,

    /** Refused for good: after two refusals Android stops showing the prompt, and only the system settings help. */
    Blocked,
}

/**
 * Judges a finished request on the [required] permissions. [canAskAgain] is Android's
 * `shouldShowRequestPermissionRationale`, which after a refusal turns false once the prompt is gone for good.
 */
fun permissionAnswer(
    required: List<String>,
    granted: (String) -> Boolean,
    canAskAgain: (String) -> Boolean,
): PermissionAnswer {
    val refused = required.filterNot(granted)
    return when {
        refused.isEmpty() -> PermissionAnswer.Granted
        refused.all(canAskAgain) -> PermissionAnswer.Denied
        else -> PermissionAnswer.Blocked
    }
}

/**
 * Returns the function that shows Android's prompt for [permissions]. [onAnswer] hears how the user answered for the
 * [required] ones; the others are asked along and may be refused without harm.
 */
@Composable
fun rememberPermissionRequest(
    permissions: List<String>,
    required: List<String> = permissions,
    onAnswer: (PermissionAnswer) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val latest by rememberUpdatedState(onAnswer)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            // No results: the request was cancelled, by a configuration change for one. Nothing was answered.
            if (results.isEmpty()) return@rememberLauncherForActivityResult
            latest(
                permissionAnswer(
                    required,
                    granted = { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED },
                    canAskAgain = {
                        activity == null ||
                            ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
                    },
                ),
            )
        }
    return { launcher.launch(permissions.toTypedArray()) }
}
