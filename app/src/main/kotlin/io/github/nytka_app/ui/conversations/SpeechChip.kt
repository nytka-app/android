package io.github.nytka_app.ui.conversations

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.github.nytka_app.R
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.Segment

/** What a line that is not a person's can be called. */
enum class SpeechTag { Media, Call }

/** The chip on a line: [guess] is true while the server only guesses (shadow mode) and no kind is set. */
data class SpeechLabel(
    val tag: SpeechTag,
    val guess: Boolean,
)

/** A kind of "media" or "call" shows solid; with no kind, a guess of either shows outlined. Nothing else shows. */
internal fun Segment.speechLabel(): SpeechLabel? {
    fun tagOf(kind: String?) =
        when (kind) {
            "media" -> SpeechTag.Media
            "call" -> SpeechTag.Call
            else -> null
        }
    if (speechKind != null) return tagOf(speechKind)?.let { SpeechLabel(it, guess = false) }
    return tagOf(speechGuess)?.let { SpeechLabel(it, guess = true) }
}

/** What a failed mark came to; the screen words it. Never carries text of the transcript. */
enum class SpeechNotice { Failed, NeedsAdmin, Gone, NeedsUpdate }

internal fun ApiResult.Failure.speechNotice(): SpeechNotice =
    when (kind) {
        FailureKind.Forbidden -> SpeechNotice.NeedsAdmin
        FailureKind.NotFound -> SpeechNotice.Gone
        FailureKind.Unsupported -> SpeechNotice.NeedsUpdate
        else -> SpeechNotice.Failed
    }

private fun speechNoticeText(
    context: Context,
    notice: SpeechNotice,
): String =
    when (notice) {
        SpeechNotice.Failed -> context.getString(R.string.speech_mark_failed)
        SpeechNotice.NeedsAdmin -> context.getString(R.string.app_needs_admin_token)
        SpeechNotice.Gone -> context.getString(R.string.item_no_longer_exists)
        SpeechNotice.NeedsUpdate -> context.getString(R.string.server_needs_update)
    }

/** Shows the sentence for [notice] once in [host], then calls [shown]. */
@Composable
fun SpeechNoticeEffect(
    notice: SpeechNotice?,
    host: SnackbarHostState,
    shown: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(notice) {
        notice ?: return@LaunchedEffect
        host.showSnackbar(speechNoticeText(context, notice))
        shown()
    }
}

/** "Media" or "Call", solid for a kind, outlined with a question mark for a guess. */
@Composable
fun SpeechChip(
    label: SpeechLabel,
    modifier: Modifier = Modifier,
) {
    val media = label.tag == SpeechTag.Media
    val text =
        stringResource(
            when {
                media && label.guess -> R.string.speech_chip_media_guess
                media -> R.string.speech_chip_media
                label.guess -> R.string.speech_chip_call_guess
                else -> R.string.speech_chip_call
            },
        )
    val spoken =
        stringResource(
            when {
                media && label.guess -> R.string.speech_chip_media_probably
                media -> R.string.speech_chip_media
                label.guess -> R.string.speech_chip_call_probably
                else -> R.string.speech_chip_call
            },
        )
    Surface(
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
        shape = MaterialTheme.shapes.small,
        color = if (label.guess) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.secondaryContainer,
        contentColor =
            if (label.guess) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        border = if (label.guess) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** The conversation menu's two bulk marks, for the voices that are not the wearer's; nothing unless [visible]. */
@Composable
internal fun OtherVoicesItems(
    visible: Boolean,
    onMark: (String) -> Unit,
    close: () -> Unit,
) {
    if (!visible) return
    val items = listOf(R.string.speech_others_media to "media", R.string.speech_others_people to "person")
    for ((label, kind) in items) {
        DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
            close()
            onMark(kind)
        })
    }
}
