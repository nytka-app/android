package io.github.nytka_app.ui.device

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import io.github.nytka_app.R
import io.github.nytka_app.ui.theme.NytkaTheme

/** The SIL Open Font License text the bundled fonts (Onest, Literata, JetBrains Mono) ask to travel with them. */
@Composable
internal fun FontLicensesDialog(onDismiss: () -> Unit) {
    val resources = LocalResources.current
    val text =
        remember(resources) {
            resources
                .openRawResource(R.raw.font_licenses)
                .bufferedReader()
                .use { it.readText() }
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.font_licenses)) },
        text = { Text(text, Modifier.verticalScroll(rememberScrollState()), style = NytkaTheme.typography.code) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
