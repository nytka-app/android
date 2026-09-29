package io.github.nytka_app.ui.developer.server

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.core.api.Delivery
import io.github.nytka_app.core.api.Webhook
import kotlinx.coroutines.launch

/** The server's webhooks: a list, and a screen for one with its test button and delivery log. Admin token only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebhooksScreen(
    onBack: () -> Unit,
    viewModel: WebhooksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected = state.selected
    val back = { if (selected != null) viewModel.close() else onBack() }
    BackHandler(enabled = selected != null) { viewModel.close() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selected == null) "Webhooks" else "Webhook") },
                navigationIcon = {
                    IconButton(
                        onClick = back,
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (selected == null && state.error == null) {
                        IconButton(onClick = viewModel::startAdd) {
                            Icon(Icons.Filled.Add, contentDescription = "Add a webhook")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (selected == null) {
                WebhookList(state, viewModel, Modifier.weight(1f))
            } else {
                WebhookDetail(selected, state, viewModel, Modifier.weight(1f))
            }
        }
    }

    state.creator?.let { creator ->
        AddWebhookDialog(creator, onCreate = viewModel::create, onDismiss = viewModel::dismissCreator)
    }
    state.secret?.let { secret -> SecretDialog(secret, onDone = viewModel::dismissSecret) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebhookList(
    state: WebhooksUiState,
    viewModel: WebhooksViewModel,
    modifier: Modifier,
) {
    PullToRefreshBox(isRefreshing = state.loading, onRefresh = viewModel::refresh, modifier = modifier) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            state.error?.let { error ->
                item {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::refresh) { Text("Retry") }
                }
            }
            items(state.webhooks, key = { it.id }) { hook ->
                ListItem(
                    headlineContent = { Text(hook.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${eventsText(hook)} · ${lastDeliveryText(hook)}") },
                    trailingContent = {
                        Switch(checked = hook.active, onCheckedChange = { viewModel.setActive(hook.id, it) })
                    },
                    modifier = Modifier.clickable { viewModel.open(hook.id) },
                )
            }
            if (state.webhooks.isEmpty() && !state.loading && state.error == null) {
                item {
                    Text(
                        "No webhooks yet. A webhook tells another tool when a conversation, task or memory is new.",
                    )
                }
            }
        }
    }
}

private fun eventsText(hook: Webhook) = if ("*" in hook.events) "All events" else hook.events.joinToString(", ")

private fun lastDeliveryText(hook: Webhook) = hook.lastDelivery?.let { "last ${it.status}" } ?: "no deliveries"

@Composable
private fun WebhookDetail(
    hook: Webhook,
    state: WebhooksUiState,
    viewModel: WebhooksViewModel,
    modifier: Modifier,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(hook.url, style = MaterialTheme.typography.titleMedium)
            hook.description?.let { Text(it) }
            Text(eventsText(hook), style = MaterialTheme.typography.bodySmall)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = viewModel::sendTest) { Text("Send test") }
                OutlinedButton(onClick = { confirmDelete = true }) { Text("Delete") }
            }
            state.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        item { Text("Deliveries", style = MaterialTheme.typography.titleSmall) }
        items(state.deliveries, key = { it.id }) { delivery ->
            ListItem(
                headlineContent = { Text("${delivery.eventType} · ${delivery.status}") },
                supportingContent = { Text(deliveryDetail(delivery)) },
            )
        }
        if (state.deliveries.isEmpty() && !state.deliveriesLoading) item { Text("No deliveries yet.") }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this webhook?") },
            text = { Text("Its delivery log goes with it. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

internal fun deliveryDetail(delivery: Delivery): String {
    val outcome =
        when {
            delivery.lastError != null -> delivery.lastError
            delivery.lastStatusCode != null -> "HTTP ${delivery.lastStatusCode}"
            else -> null
        }
    val attempts = if (delivery.attempts == 1) "1 attempt" else "${delivery.attempts} attempts"
    return listOfNotNull(attempts, outcome, delivery.deliveredAt ?: delivery.createdAt).joinToString(" · ")
}

@Composable
private fun AddWebhookDialog(
    creator: WebhookCreator,
    onCreate: (url: String, events: List<String>, description: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var all by rememberSaveable { mutableStateOf(true) }
    var chosen by rememberSaveable { mutableStateOf("") }
    val picked = chosen.split(",").filter { it.isNotEmpty() }.toSet()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a webhook") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    isError = creator.error != null,
                    supportingText = creator.error?.let { error -> { Text(error) } },
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    singleLine = true,
                )
                EventRow("All events", all) { all = it }
                if (!all) {
                    WebhooksViewModel.EVENTS.forEach { (event, label) ->
                        EventRow(label, event in picked) { on ->
                            chosen = (if (on) picked + event else picked - event).joinToString(",")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        url,
                        if (all) {
                            listOf("*")
                        } else {
                            WebhooksViewModel.EVENTS.map { it.first }.filter {
                                it in
                                    picked
                            }
                        },
                        description,
                    )
                },
                enabled = url.isNotBlank() && !creator.saving,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EventRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}

/** The signing secret, once: the server cannot show it again. */
@Composable
private fun SecretDialog(
    secret: NewSecret,
    onDone: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Signing secret") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Copy it now. It is not shown again, and it signs what ${secret.url} receives.")
                SelectionContainer { Text(secret.secret, fontFamily = FontFamily.Monospace) }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done") } },
        dismissButton = {
            TextButton(onClick = {
                scope.launch {
                    clipboard.setClipEntry(
                        ClipEntry(android.content.ClipData.newPlainText("Webhook secret", secret.secret)),
                    )
                }
            }) { Text("Copy") }
        },
    )
}
