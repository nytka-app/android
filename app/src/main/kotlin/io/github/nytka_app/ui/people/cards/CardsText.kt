package io.github.nytka_app.ui.people.cards

import android.content.Context
import io.github.nytka_app.R
import io.github.nytka_app.core.api.FailureKind

internal fun noticeText(
    context: Context,
    notice: CardNotice,
): String =
    when (notice) {
        CardNotice.ClipGone -> context.getString(R.string.cards_clip_gone)
        CardNotice.ClipFailed -> context.getString(R.string.cards_clip_failed)
        is CardNotice.Failed ->
            when {
                notice.kind == FailureKind.NotFound && notice.item -> context.getString(R.string.item_no_longer_exists)
                notice.kind == FailureKind.NotFound || notice.kind == FailureKind.Unsupported ->
                    context.getString(R.string.server_needs_update)

                notice.kind == FailureKind.Forbidden -> context.getString(R.string.app_needs_admin_token)
                else -> notice.message
            }
    }
