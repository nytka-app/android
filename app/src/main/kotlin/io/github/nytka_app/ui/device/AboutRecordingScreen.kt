package io.github.nytka_app.ui.device

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nytka_app.R
import io.github.nytka_app.ui.firstrun.CONSENT_TEXT
import io.github.nytka_app.ui.firstrun.ConsentNote

/** Settings › About recording: the four points the first run shows, to read again, with no box to tick. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutRecordingScreen(
    onBack: () -> Unit,
    onOpenSignals: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_recording)) },
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
        ) {
            ConsentNote(
                points =
                    listOf(
                        stringResource(R.string.consent_point_who),
                        stringResource(R.string.consent_point_stop),
                        CONSENT_TEXT,
                        stringResource(R.string.consent_point_data),
                    ),
            )
            TextButton(onClick = onOpenSignals) { Text(stringResource(R.string.signals_title)) }
        }
    }
}
