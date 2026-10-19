package io.github.nytka_app.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nytka_app.R
import io.github.nytka_app.ui.SettingsCard
import io.github.nytka_app.ui.SettingsRow

/** The paired pendant: its name and address, and the one place that forgets it, behind a dialog that says what goes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendantDetailScreen(
    viewModel: DeviceViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    // Forgetting clears the address: nothing is left to show, so leave.
    val address = state.pendantAddress
    val name = state.pendantName ?: stringResource(R.string.no_pendant_paired)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pendant_details)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsCard(stringResource(R.string.pendant)) {
                SettingsRow(name)
                address?.let { SettingsRow(stringResource(R.string.pendant_address), supporting = it, mono = true) }
                if (address != null) {
                    OutlinedButton(onClick = { confirmForget = true }) { Text(stringResource(R.string.forget)) }
                }
            }
        }
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text(stringResource(R.string.forget_pendant_title_format, name)) },
            text = { Text(stringResource(R.string.forget_pendant_message_format, name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    viewModel.forgetPendant()
                    onBack()
                }) { Text(stringResource(R.string.forget), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
