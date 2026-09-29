package io.github.nytka_app.core.api

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Named access tokens, admin only. A v0.1 server answers [FailureKind.NotFound]. */
interface TokensClient {
    /** Newest first, revoked ones included; never a secret. */
    suspend fun tokens(): ApiResult<List<AccessToken>>

    /** The answer carries the token itself, which the server shows once. [FailureKind.Conflict]: the name is in use. */
    suspend fun createToken(
        name: String,
        scope: String,
    ): ApiResult<CreatedToken>

    suspend fun revokeToken(id: String): ApiResult<Unit>
}

class TokensApi(
    private val api: NytkaApi,
) : TokensClient {
    override suspend fun tokens(): ApiResult<List<AccessToken>> =
        api.request("GET", "api/v1/tokens") { api.json.decodeFromString<TokensAnswer>(it).items }

    override suspend fun createToken(
        name: String,
        scope: String,
    ): ApiResult<CreatedToken> =
        api.request(
            "POST",
            "api/v1/tokens",
            body =
                buildJsonObject {
                    put("name", name)
                    put("scope", scope)
                }.toString(),
        ) { api.json.decodeFromString(it) }

    override suspend fun revokeToken(id: String): ApiResult<Unit> = api.request("DELETE", "api/v1/tokens/$id") { }
}
