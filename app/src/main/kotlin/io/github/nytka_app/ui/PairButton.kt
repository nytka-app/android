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
import io.github.nytka_app.pendant.RecorderModel

/**
 * Opens the system's companion-device chooser, filtered on [model]'s service. The default is the model
 * an install without a saved one means; a screen with a model picker passes the chosen one.
 */
@Composable
fun PairButton(
    model: RecorderModel = RecorderModel.of(null),
    label: String = stringResource(R.string.pair_pendant),
    onPaired: (PairedPendant) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val pairing = remember { CompanionPairing(context) }
    val noPendantString = stringResource(R.string.pairing_chooser_no_pendant)
    val chooser =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                pairing.complete(result.data, model)?.let(onPaired)
                    ?: onError(noPendantString)
            }
        }
    Button(onClick = {
        pairing.associate(
            model,
            onChooser = { chooser.launch(IntentSenderRequest.Builder(it).build()) },
            onError = onError,
        )
    }) { Text(label) }
}
