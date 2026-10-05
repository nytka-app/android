package io.github.nytka_app.ui.conversations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nytka_app.R

/** "{label} may be {name}", the line that carries it, and two text buttons. Nothing is sent until one is tapped. */
@Composable
fun SuggestionBanner(
    banner: SuggestionBannerState,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(
                    R.string.suggestion_banner_format,
                    banner.label ?: stringResource(R.string.suggestion_unknown_voice),
                    banner.name,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            banner.evidence?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onReject, enabled = !banner.busy) {
                    Text(stringResource(R.string.suggestion_reject_format, banner.name))
                }
                TextButton(onClick = onAccept, enabled = !banner.busy) {
                    Text(stringResource(R.string.suggestion_accept))
                }
            }
        }
    }
}

/** Shows the sentence for a failed answer once in [host], then calls [shown]. */
@Composable
fun SuggestionNoticeEffect(
    notice: SuggestionNotice?,
    host: SnackbarHostState,
    shown: () -> Unit,
) {
    val failed = stringResource(R.string.suggestion_failed)
    val needsAdmin = stringResource(R.string.app_needs_admin_token)
    LaunchedEffect(notice) {
        notice ?: return@LaunchedEffect
        host.showSnackbar(if (notice == SuggestionNotice.NeedsAdmin) needsAdmin else failed)
        shown()
    }
}

/** The banner as one list item, when there is one. */
fun LazyListScope.suggestion(
    banner: SuggestionBannerState?,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    banner ?: return
    item(key = "suggestion") { SuggestionBanner(banner, onAccept, onReject) }
}
