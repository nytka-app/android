package io.github.nytka_app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.nytka_app.R
import io.github.nytka_app.capture.CompanionPairing
import io.github.nytka_app.capture.PairedPendant

/** Opens the system's companion-device chooser, filtered on the Omi audio service. */
@Composable
fun PairButton(
    label: String = stringResource(R.string.pair_pendant),
    onPaired: (PairedPendant) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val pairing = remember { CompanionPairing(context) }
    val chooser =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                pairing.complete(result.data)?.let(onPaired)
                    ?: onError(context.getString(R.string.pairing_chooser_no_pendant))
            }
        }
    Button(onClick = {
        pairing.associate(onChooser = { chooser.launch(IntentSenderRequest.Builder(it).build()) }, onError = onError)
    }) { Text(label) }
}
