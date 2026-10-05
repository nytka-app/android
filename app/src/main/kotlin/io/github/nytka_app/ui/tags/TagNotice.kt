package io.github.nytka_app.ui.tags

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import io.github.nytka_app.R
import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.ServerInfo

/** What the tags of a screen show and allow, from `/info`. */
enum class TagAccess {
    /** The server lacks the `tags` feature, or `/info` could not be read: no chips, no call. */
    None,

    /** A read token: chips show, nothing changes. */
    Read,

    /** An admin token: chips show and can be added and removed. */
    Edit,
}

fun ServerInfo.tagAccess(): TagAccess =
    when {
        !has(ServerInfo.FEATURE_TAGS) -> TagAccess.None
        scope == ServerInfo.SCOPE_ADMIN -> TagAccess.Edit
        else -> TagAccess.Read
    }

/** What a failed add or remove came to; the screen words it. Never carries a tag name. */
sealed interface TagNotice {
    /** A `400`: the server refused the name. */
    data object InvalidTag : TagNotice

    /** A `409`: the item holds 20 tags already. */
    data object TooManyTags : TagNotice

    data object NeedsAdmin : TagNotice

    /** A `404` on an item's tag route: the conversation or person is gone. */
    data object Gone : TagNotice

    data object NeedsUpdate : TagNotice

    /** A fixed sentence for any other failure. */
    data class Failed(
        val message: String,
    ) : TagNotice
}

fun ApiResult.Failure.tagNotice(): TagNotice =
    when (kind) {
        FailureKind.Invalid -> TagNotice.InvalidTag
        FailureKind.Conflict -> TagNotice.TooManyTags
        FailureKind.Forbidden -> TagNotice.NeedsAdmin
        FailureKind.NotFound -> TagNotice.Gone
        FailureKind.Unsupported -> TagNotice.NeedsUpdate
        else -> TagNotice.Failed(message)
    }

internal fun tagNoticeText(
    context: Context,
    notice: TagNotice,
): String =
    when (notice) {
        TagNotice.InvalidTag -> context.getString(R.string.tag_invalid)
        TagNotice.TooManyTags -> context.getString(R.string.tag_too_many)
        TagNotice.NeedsAdmin -> context.getString(R.string.app_needs_admin_token)
        TagNotice.Gone -> context.getString(R.string.item_no_longer_exists)
        TagNotice.NeedsUpdate -> context.getString(R.string.server_needs_update)
        is TagNotice.Failed -> notice.message
    }

/** Shows the sentence for [notice] once in [host], then calls [shown]. */
@Composable
fun TagNoticeEffect(
    notice: TagNotice?,
    host: SnackbarHostState,
    shown: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(notice) {
        notice ?: return@LaunchedEffect
        host.showSnackbar(tagNoticeText(context, notice))
        shown()
    }
}
