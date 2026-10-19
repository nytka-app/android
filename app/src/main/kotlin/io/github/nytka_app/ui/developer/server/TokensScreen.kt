package io.github.nytka_app.ui.developer.server

import android.content.ClipData
import android.content.ClipDescription.EXTRA_IS_SENSITIVE
import android.os.PersistableBundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.core.api.AccessToken
import io.github.nytka_app.ui.ErrorLine
import io.github.nytka_app.ui.ListFailure
import io.github.nytka_app.ui.ListState
import io.github.nytka_app.ui.ListStateKind
import io.github.nytka_app.ui.NytkaTextField
import io.github.nytka_app.ui.SettingsCard
import io.github.nytka_app.ui.SettingsRow
import io.github.nytka_app.ui.conversations.Formatting
import io.github.nytka_app.ui.maskedToken
import io.github.nytka_app.ui.theme.NytkaTheme
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    val detail = state.tokens.firstOrNull { it.id == detailId }
    BackHandler(enabled = detail != null) { detailId = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.name ?: stringResource(R.string.access_tokens_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = { if (detail != null) detailId = null else onBack() },
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
        if (detail != null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                TokenDetail(detail) { revoking = detail }
            }
        } else {
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
                if (state.loading) item { ListState(ListStateKind.Loading, stringResource(R.string.list_loading)) }
                state.error?.let { error ->
                    item { ListFailure(error, offline = false, onRetry = viewModel::load) }
                }
                if (state.tokens.isEmpty() && !state.loading && state.error == null) {
                    item { ListState(ListStateKind.Empty, stringResource(R.string.no_tokens_yet)) }
                }
                items(state.tokens, key = { it.id }) { token -> TokenCard(token) { detailId = token.id } }
            }
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
                    detailId = null
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
    onOpen: () -> Unit,
) {
    SettingsCard(token.name) {
        SettingsRow(
            stringResource(R.string.token_scope_format, token.scope),
            supporting = maskedToken(token.hint),
            mono = true,
            value = if (token.revokedAt != null) stringResource(R.string.token_revoked_short) else null,
            onClick = onOpen,
        )
    }
}

/** One token: its masked form, when it was made and last used, and the one place that revokes it. */
@Composable
private fun TokenDetail(
    token: AccessToken,
    onRevoke: () -> Unit,
) {
    SettingsCard(token.name) {
        SettingsRow(
            stringResource(R.string.token_scope_format, token.scope),
            supporting = maskedToken(token.hint),
            mono = true,
        )
        SettingsRow(stringResource(R.string.token_created_format, whenText(token.createdAt)))
        SettingsRow(
            stringResource(
                R.string.token_last_used_format,
                token.lastUsedAt?.let { whenText(it) } ?: stringResource(R.string.never),
            ),
        )
        if (token.revokedAt != null) {
            ErrorLine(stringResource(R.string.token_revoked_format, whenText(token.revokedAt)))
        } else {
            OutlinedButton(onClick = onRevoke) { Text(stringResource(R.string.revoke)) }
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
                NytkaTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_NAME) },
                    label = stringResource(R.string.name),
                    error = error,
                )
                Column(Modifier.selectableGroup()) {
                    ScopeChoice("read", stringResource(R.string.token_scope_read), scope) { scope = it }
                    ScopeChoice("admin", stringResource(R.string.token_scope_admin), scope) { scope = it }
                }
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
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected == value, role = Role.RadioButton, onClick = { onSelect(value) }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected == value, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
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
                SelectionContainer { Text(token, style = NytkaTheme.typography.code) }
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

@Composable
private fun whenText(instant: String?): String {
    val format = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", LocalLocale.current.platformLocale)
    return instant
        ?.let { Formatting.parse(it)?.let { at -> format.format(at.atZone(ZoneId.systemDefault())) } }
        ?: stringResource(R.string.unknown)
}
