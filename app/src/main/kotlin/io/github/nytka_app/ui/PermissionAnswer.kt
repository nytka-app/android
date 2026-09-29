package io.github.nytka_app.ui

import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** How the user answered Android's prompt for a permission. */
enum class PermissionAnswer {
    Granted,

    /** Refused, or the prompt was backed out of: Android shows it again when asked. */
    Denied,

    /** Refused again after Android had shown the prompt once, or over and over: the settings are the sure way. */
    Blocked,
}

/**
 * Judges a finished request on the [required] permissions. [rationaleBefore] and [rationaleAfter] are Android's
 * `shouldShowRequestPermissionRationale` when the request was made and when it ended.
 *
 * A refusal is final only when the prompt had been refused once (the rationale was true) and the rationale has turned
 * false since: the user refused a second time, and Android stops showing the prompt. Backing out of the first prompt
 * leaves no trace: Android answers "denied" and the rationale stays false, which is [PermissionAnswer.Denied].
 */
fun permissionAnswer(
    required: List<String>,
    granted: (String) -> Boolean,
    rationaleBefore: (String) -> Boolean,
    rationaleAfter: (String) -> Boolean,
): PermissionAnswer {
    val refused = required.filterNot(granted)
    return when {
        refused.isEmpty() -> PermissionAnswer.Granted
        refused.any { rationaleBefore(it) && !rationaleAfter(it) } -> PermissionAnswer.Blocked
        else -> PermissionAnswer.Denied
    }
}

/**
 * A prompt that was backed out of and one that Android no longer shows both come back as "denied" with no rationale,
 * so a refusal right after another one counts as final: the user is offered the system settings beside Allow.
 */
fun PermissionAnswer.after(previous: PermissionAnswer?): PermissionAnswer =
    if (this == PermissionAnswer.Denied && previous != null && previous != PermissionAnswer.Granted) {
        PermissionAnswer.Blocked
    } else {
        this
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
    val rationaleBefore = remember { mutableSetOf<String>() }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            // Android answers an interrupted request with nothing at all. That is no refusal for good: ask again.
            val interrupted = results.isEmpty()
            latest(
                permissionAnswer(
                    required,
                    granted = { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED },
                    rationaleBefore = { it in rationaleBefore },
                    rationaleAfter = {
                        interrupted ||
                            activity == null ||
                            ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
                    },
                ),
            )
        }
    return {
        rationaleBefore.clear()
        if (activity != null) {
            required.filterTo(rationaleBefore) { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        }
        launcher.launch(permissions.toTypedArray())
    }
}
