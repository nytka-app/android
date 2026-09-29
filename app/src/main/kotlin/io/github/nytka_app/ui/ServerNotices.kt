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
