package io.github.nytka_app.ui.people

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import io.github.nytka_app.R
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.NameSuggestion
import io.github.nytka_app.core.api.ReviewClient
import io.github.nytka_app.ui.conversations.Formatting
import java.time.Clock
import java.time.Duration

/** How many auto-applied names a screen asks for, newest first. */
const val AUTO_NAMED_LIMIT = 200

/** How long an auto-applied name stays under "Named for you". */
val AUTO_NAMED_RECENT: Duration = Duration.ofDays(7)

/** The auto-applied names of the last [AUTO_NAMED_RECENT]; one without a time counts as recent. */
fun List<NameSuggestion>.recent(clock: Clock): List<NameSuggestion> {
    val since = clock.instant().minus(AUTO_NAMED_RECENT)
    return filter { s -> s.decidedAt?.let(Formatting::parse)?.isBefore(since) != true }
}

/**
 * Whether the auto-applied name named the speaker of a line with [voiceId] and [personId]: by the voice for a
 * speaker's name, else by the person.
 */
fun NameSuggestion.names(
    voiceId: String?,
    personId: String?,
): Boolean =
    if (target == NameSuggestion.TARGET_SPEAKER && speakerId != null) {
        voiceId == speakerId
    } else {
        this.personId != null && this.personId == personId
    }

/**
 * The auto-applied names one screen shows ([keep] picks them from the server's list) and their undo. A name being
 * undone stays away from a read in between.
 */
class AutoNameBook(
    private val review: ReviewClient,
    private val keep: (List<NameSuggestion>) -> List<NameSuggestion>,
) {
    var named: List<NameSuggestion> = emptyList()
        private set
    private val undoing = mutableSetOf<String>()

    /** A failure, an older server's included, leaves nothing to show. */
    suspend fun load() {
        val all = (review.autoApplied(AUTO_NAMED_LIMIT) as? ApiResult.Ok)?.value.orEmpty()
        named = keep(all).filterNot { it.id in undoing }
    }

    fun clear() {
        named = emptyList()
    }

    /**
     * Takes [suggestion] back; only a tap gets here. It leaves [named] at once ([onTaken] shows that) and comes back
     * only when the server could not answer: a `404` or `409` means nothing is left to undo. Null when not listed.
     */
    suspend fun undo(
        suggestion: NameSuggestion,
        onTaken: () -> Unit,
    ): UndoNotice? {
        val index = named.indexOf(suggestion)
        if (index < 0) return null
        named = named - suggestion
        undoing += suggestion.id
        onTaken()
        val result = review.undo(suggestion.id)
        undoing -= suggestion.id
        if (result is ApiResult.Ok) return UndoNotice.Undone(suggestion.name)
        val failure = result as ApiResult.Failure
        if (failure.kind != FailureKind.NotFound && failure.kind != FailureKind.Conflict) {
            named = named.toMutableList().apply { add(index.coerceAtMost(size), suggestion) }
        }
        return UndoNotice.of(failure)
    }
}

/** What undoing an auto-applied name came to; the screen words it. Never carries the server's text but a `400`'s. */
sealed interface UndoNotice {
    data class Undone(
        val name: String,
    ) : UndoNotice

    /** A `409`: it was no longer auto-applied. */
    data object AlreadyUndone : UndoNotice

    data class Failed(
        val kind: FailureKind,
        val message: String,
    ) : UndoNotice

    companion object {
        fun of(failure: ApiResult.Failure): UndoNotice =
            if (failure.kind == FailureKind.Conflict) AlreadyUndone else Failed(failure.kind, failure.message)
    }
}

/** A `404` on undo is one item gone: the server lists `auto-names`, so the route exists. */
fun undoNoticeText(
    context: Context,
    notice: UndoNotice,
): String =
    when (notice) {
        is UndoNotice.Undone -> context.getString(R.string.auto_named_undone_format, notice.name)
        UndoNotice.AlreadyUndone -> context.getString(R.string.auto_named_already_undone)
        is UndoNotice.Failed ->
            when (notice.kind) {
                FailureKind.NotFound -> context.getString(R.string.item_no_longer_exists)
                FailureKind.Unsupported -> context.getString(R.string.server_needs_update)
                FailureKind.Forbidden -> context.getString(R.string.app_needs_admin_token)
                else -> notice.message
            }
    }

/** Shows the sentence for [notice] once in [host], then calls [shown]. */
@Composable
fun UndoNoticeEffect(
    notice: UndoNotice?,
    host: SnackbarHostState,
    shown: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(notice) {
        notice ?: return@LaunchedEffect
        host.showSnackbar(undoNoticeText(context, notice))
        shown()
    }
}
