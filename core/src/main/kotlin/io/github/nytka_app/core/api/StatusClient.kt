package io.github.nytka_app.core.api

fun interface StatusClient {
    suspend fun status(): ApiResult<ServerStatus>
}
