package io.github.nytka_app.ui.developer.server

import android.content.ClipData
import android.content.ClipDescription.EXTRA_IS_SENSITIVE
import android.os.PersistableBundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.core.api.AccessToken
import io.github.nytka_app.ui.conversations.Formatting
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Named access tokens: create one (shown once), revoke one. Developer mode, admin token only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokensScreen(
    onBack: () -> Unit,
    viewModel: TokensViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    var revoking by remember { mutableStateOf<AccessToken?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.access_tokens_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                FilledTonalButton(onClick = {
                    creating = true
                }, enabled = state.error == null) { Text(stringResource(R.string.create_token)) }
            }
            if (state.loading) item { CircularProgressIndicator() }
            state.error?.let { error ->
                item {
                    Column {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = viewModel::load) { Text(stringResource(R.string.action_retry)) }
                    }
                }
            }
            items(state.tokens, key = { it.id }) { token -> TokenCard(token) { revoking = token } }
        }
    }

    if (creating) {
        CreateDialog(
            error = state.createError,
            onDismiss = {
                creating = false
                viewModel.clearCreateError()
            },
            onCreate = viewModel::create,
        )
    }
    // The dialog closes once the server has made the token; the secret takes its place.
    LaunchedEffect(state.created) { if (state.created != null) creating = false }
    state.created?.let { created ->
        CreatedDialog(created.name, created.token, onDone = viewModel::dismissCreated)
    }
    revoking?.let { token ->
        AlertDialog(
            onDismissRequest = { revoking = null },
            title = { Text(stringResource(R.string.revoke_token_title_format, token.name)) },
            text = { Text(stringResource(R.string.revoke_token_message)) },
            confirmButton = {
                TextButton(onClick = {
                    revoking = null
                    viewModel.revoke(token.id)
                }) { Text(stringResource(R.string.revoke)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { revoking = null },
                ) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun TokenCard(
    token: AccessToken,
    onRevoke: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(token.name, style = MaterialTheme.typography.titleMedium)
            Text(
                if (token.hint.isNotEmpty()) {
                    stringResource(R.string.token_scope_hint_format, token.scope, token.hint)
                } else {
                    stringResource(R.string.token_scope_format, token.scope)
                },
            )
            Text(
                stringResource(R.string.token_created_format, whenText(token.createdAt)),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(
                    R.string.token_last_used_format,
                    token.lastUsedAt?.let { whenText(it) } ?: stringResource(R.string.never),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            if (token.revokedAt != null) {
                Text(
                    stringResource(R.string.token_revoked_format, whenText(token.revokedAt)),
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                OutlinedButton(onClick = onRevoke) { Text(stringResource(R.string.revoke)) }
            }
        }
    }
}

@Composable
private fun CreateDialog(
    error: String?,
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var scope by rememberSaveable { mutableStateOf("read") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_token)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_NAME) },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                )
                ScopeChoice("read", stringResource(R.string.token_scope_read), scope) { scope = it }
                ScopeChoice("admin", stringResource(R.string.token_scope_admin), scope) { scope = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, scope) },
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ScopeChoice(
    value: String,
    label: String,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected == value, onClick = { onSelect(value) })
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The one time the token is shown: Copy is the way to keep it, and closing drops it. */
@Composable
private fun CreatedDialog(
    name: String,
    token: String,
    onDone: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.token_title_format, name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.token_copy_now_message))
                Text(token, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.action_done)) } },
        dismissButton = {
            TextButton(onClick = {
                scope.launch {
                    clipboard.setClipEntry(
                        ClipEntry(
                            ClipData.newPlainText("Nytka token", token).apply {
                                // A secret: keep it out of clipboard previews.
                                description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
                            },
                        ),
                    )
                }
            }) { Text(stringResource(R.string.action_copy)) }
        },
    )
}

private const val MAX_NAME = 64

private val whenFormat = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)

@Composable
private fun whenText(instant: String?): String =
    instant
        ?.let { Formatting.parse(it)?.let { at -> whenFormat.format(at.atZone(ZoneId.systemDefault())) } }
        ?: stringResource(R.string.unknown)
