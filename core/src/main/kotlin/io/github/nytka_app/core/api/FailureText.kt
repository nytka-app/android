package io.github.nytka_app.core.api

/**
 * What a screen says about a failed call. A server that lacks an endpoint answers 404 (a v0.1 server has none of
 * v0.2's), and a `read` token gets 403 on everything but reading.
 */
fun ApiResult.Failure.forScreen(): String =
    when (kind) {
        FailureKind.NotFound -> "This server needs an update."
        FailureKind.Forbidden -> "The app needs an admin token."
        else -> message
    }
