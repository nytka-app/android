package io.github.nytka_app.ui.people

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.core.api.FailureKind

/** The foot of People settings: delete the voice groups and voiceprints Nytka itself keeps. */
@Composable
fun VoiceModelsSection(viewModel: VoiceModelsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column {
        Text(stringResource(R.string.voice_models_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.voice_models_explanation),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        OutlinedButton(onClick = viewModel::ask, enabled = !state.deleting) {
            Text(stringResource(R.string.voice_models_delete))
        }
        state.notice?.let {
            val failed = it is VoiceModelsNotice.Failed
            Text(
                voiceModelsNoticeText(context, it),
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
    if (state.confirming) {
        AlertDialog(
            onDismissRequest = viewModel::cancel,
            title = { Text(stringResource(R.string.voice_models_confirm_title)) },
            text = { Text(stringResource(R.string.voice_models_confirm_text)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirm) { Text(stringResource(R.string.voice_models_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::cancel,
                ) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

internal fun voiceModelsNoticeText(
    context: Context,
    notice: VoiceModelsNotice,
): String =
    when (notice) {
        VoiceModelsNotice.Deleted -> context.getString(R.string.voice_models_deleted)
        is VoiceModelsNotice.Failed ->
            when (notice.kind) {
                FailureKind.NotFound, FailureKind.Unsupported -> context.getString(R.string.server_needs_update)
                FailureKind.Forbidden -> context.getString(R.string.app_needs_admin_token)
                else -> notice.message
            }
    }
