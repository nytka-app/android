package io.github.nytka_app.core.api

fun interface InfoClient {
    suspend fun info(): ApiResult<ServerInfo>
}
