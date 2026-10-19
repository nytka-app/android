package io.github.nytka_app.ui.conversations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nytka_app.R
import io.github.nytka_app.ui.people.AcceptAllButton
import io.github.nytka_app.ui.people.AcceptAllNotice
import io.github.nytka_app.ui.people.acceptAllNoticeText
import io.github.nytka_app.ui.people.acceptText
import io.github.nytka_app.ui.people.rejectText
import io.github.nytka_app.ui.people.title
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
/**
 * "{label} may be {name}" under a saffron header (it waits for the owner, it is not a problem), the line that carries
 * it, and a way to answer: accept, "not them" (the name is never suggested again) and "later", which only hides the
 * card here. Nothing is sent until a button is tapped.
 */
@Composable
fun SuggestionBanner(
    banner: SuggestionBannerState,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
    onSkip: () -> Unit = {},
    onAcceptAll: () -> Unit = {},
) {
    val saffron = MaterialTheme.colorScheme.tertiaryContainer
    OutlinedCard(modifier.fillMaxWidth(), border = BorderStroke(1.dp, saffron)) {
        Surface(Modifier.fillMaxWidth(), color = saffron) {
            Text(
                banner.wording.title(banner.label ?: stringResource(R.string.suggestion_unknown_voice)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            banner.evidence?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Button(onClick = onAccept, enabled = !banner.busy) { Text(banner.wording.acceptText()) }
                OutlinedButton(onClick = onReject, enabled = !banner.busy) { Text(banner.wording.rejectText()) }
                TextButton(onClick = onSkip, enabled = !banner.busy) { Text(stringResource(R.string.suggestion_skip)) }
                if (banner.acceptAllCount > 1) {
                    AcceptAllButton(banner.acceptAllCount, banner.name, enabled = !banner.busy, onClick = onAcceptAll)
                }
            }
        }
    }
}

/** Shows the sentence for an "accept all" once in [host], then calls [shown]. */
@Composable
fun AcceptAllNoticeEffect(
    notice: AcceptAllNotice?,
    host: SnackbarHostState,
    shown: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(notice) {
        notice ?: return@LaunchedEffect
        host.showSnackbar(acceptAllNoticeText(context, notice))
        shown()
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
    onSkip: () -> Unit,
    onAcceptAll: () -> Unit,
) {
    banner ?: return
    item(
        key = "suggestion",
    ) { SuggestionBanner(banner, onAccept, onReject, onSkip = onSkip, onAcceptAll = onAcceptAll) }
}

/** What "later" does to the card: [shown] is the banner unless set aside; [onSkip] sets it aside, with an Undo. */
class SuggestionSkip(
    val shown: SuggestionBannerState?,
    val onSkip: () -> Unit,
)

/** "Later" only hides the card on this screen; nothing is sent, and Undo in the snackbar brings it back. */
@Composable
fun rememberSuggestionSkip(
    banner: SuggestionBannerState?,
    host: SnackbarHostState,
): SuggestionSkip {
    var skipped by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val message = stringResource(R.string.suggestion_skipped)
    val undo = stringResource(R.string.action_undo)
    return SuggestionSkip(banner?.takeUnless { it.id == skipped }) {
        banner?.let {
            skipped = it.id
            scope.launch {
                val result = host.showSnackbar(message, undo, duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed && skipped == it.id) skipped = null
            }
        }
    }
}
