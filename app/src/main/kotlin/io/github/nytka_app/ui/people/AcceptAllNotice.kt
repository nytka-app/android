package io.github.nytka_app.ui.people

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import io.github.nytka_app.R
import io.github.nytka_app.core.api.FailureKind

/** What "accept every suggestion for one name" came to on the banner; the screen words it. */
sealed interface AcceptAllNotice {
    /** The server accepted [accepted] suggestions for [name]; [skipped] no longer applied. */
    data class Added(
        val name: String,
        val accepted: Int,
        val skipped: Int,
    ) : AcceptAllNotice

    /** A `409`: the pending ones disagree, so they are answered one by one. */
    data object Disagree : AcceptAllNotice

    data object NeedsUpdate : AcceptAllNotice

    data object NeedsAdmin : AcceptAllNotice

    data object Failed : AcceptAllNotice
}

/** The notice for a refusal other than a `404`, which the caller reads against the list. */
fun acceptAllFailure(kind: FailureKind): AcceptAllNotice =
    when (kind) {
        FailureKind.Conflict -> AcceptAllNotice.Disagree
        FailureKind.Forbidden -> AcceptAllNotice.NeedsAdmin
        FailureKind.NotFound, FailureKind.Unsupported -> AcceptAllNotice.NeedsUpdate
        else -> AcceptAllNotice.Failed
    }

/** "Added 16 voices to Аня", then "2 skipped" when the server skipped any. */
fun addedAllText(
    context: Context,
    name: String,
    accepted: Int,
    skipped: Int,
): String {
    val added = context.resources.getQuantityString(R.plurals.accept_all_added, accepted, accepted, name)
    if (skipped <= 0) return added
    return added + " " + context.resources.getQuantityString(R.plurals.accept_all_skipped, skipped, skipped)
}

fun acceptAllNoticeText(
    context: Context,
    notice: AcceptAllNotice,
): String =
    when (notice) {
        is AcceptAllNotice.Added -> addedAllText(context, notice.name, notice.accepted, notice.skipped)
        AcceptAllNotice.Disagree -> context.getString(R.string.accept_all_disagree)
        AcceptAllNotice.NeedsUpdate -> context.getString(R.string.server_needs_update)
        AcceptAllNotice.NeedsAdmin -> context.getString(R.string.app_needs_admin_token)
        AcceptAllNotice.Failed -> context.getString(R.string.suggestion_failed)
    }

/** "Accept all 16"; TalkBack reads "Accept all 16 suggestions for Аня". */
@Composable
fun AcceptAllButton(
    count: Int,
    name: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spoken = acceptAllDescription(count, name)
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(
            stringResource(R.string.accept_all_format, count),
            modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
        )
    }
}

@Composable
@ReadOnlyComposable
internal fun acceptAllDescription(
    count: Int,
    name: String,
): String = pluralStringResource(R.plurals.accept_all_description, count, count, name)
