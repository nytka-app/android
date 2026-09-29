package io.github.nytka_app.ui

import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.FailureKind

/** What a screen of a newer feature says about a failed call: a server without the endpoint answers 404. */
const val NEEDS_UPDATE = "This server needs an update"

/** The sentence for a failure. Fixed wording, never the server's own text. */
fun ApiResult.Failure.notice(): String =
    when (kind) {
        FailureKind.NotFound -> NEEDS_UPDATE
        FailureKind.Forbidden -> "The app needs an admin token."
        else -> message
    }

/** The sentence for a call on one item: a 404 there means the item is gone, not that the server is old. */
const val ITEM_GONE = "This item no longer exists"

fun ApiResult.Failure.itemNotice(): String = if (kind == FailureKind.NotFound) ITEM_GONE else notice()
