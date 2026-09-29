package io.github.nytka_app.core.api

import io.github.nytka_app.core.chunks.ChunkFormat
import io.github.nytka_app.core.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/** HTTP API v1 of a Nytka server. Reads the settings on every call. Never logs the token. */
class NytkaApi(
    private val client: OkHttpClient,
    private val settings: suspend () -> Settings,
) : UploadClient {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun upload(body: ByteArray): UploadResult {
        val target =
            when (val t = target()) {
                is Target.Missing -> return UploadResult.NotConfigured(t.reason)
                is Target.Ready -> t
            }
        return try {
            val requestBody = body.toRequestBody(ChunkFormat.MEDIA_TYPE.toMediaType())
            execute(target, "POST", "api/v1/chunks", requestBody).use { response ->
                when (response.code) {
                    200, 202 ->
                        UploadResult.Accepted(
                            json.decodeFromString<UploadAnswer>(response.body.string()).acceptedThroughSeq,
                            duplicate = response.code == 200,
                        )
                    401 -> UploadResult.Unauthorized
                    400, 409, 413 -> UploadResult.Dropped(response.code, "The server answered ${response.code}.")
                    else -> UploadResult.Retry("The server answered ${response.code}.")
                }
            }
        } catch (e: IOException) {
            UploadResult.Retry(e.message ?: "Network error")
        } catch (e: SerializationException) {
            UploadResult.Retry("Unexpected answer: ${e.message}")
        }
    }

    suspend fun info(): ApiResult<ServerInfo> = request("GET", "api/v1/info") { json.decodeFromString(it) }

    suspend fun status(): ApiResult<ServerStatus> = request("GET", "api/v1/status") { json.decodeFromString(it) }

    suspend fun conversations(
        before: String?,
        limit: Int = 30,
    ): ApiResult<ConversationPage> =
        request("GET", "api/v1/conversations", mapOf("before" to before, "limit" to limit.toString())) {
            json.decodeFromString(it)
        }

    suspend fun conversation(id: String): ApiResult<ConversationDetail> =
        request("GET", "api/v1/conversations/$id") { json.decodeFromString(it) }

    suspend fun deleteConversation(id: String): ApiResult<Unit> = request("DELETE", "api/v1/conversations/$id") { }

    /** The raw responses, pretty enough for developer mode as they come. */
    suspend fun transcriptionsJson(id: String): ApiResult<String> =
        request("GET", "api/v1/conversations/$id/transcriptions") { it }

    private suspend fun <T> request(
        method: String,
        path: String,
        query: Map<String, String?> = emptyMap(),
        parse: (String) -> T,
    ): ApiResult<T> {
        val target =
            when (val t = target()) {
                is Target.Missing -> return ApiResult.Failure(FailureKind.NotConfigured, t.reason)
                is Target.Ready -> t
            }
        return try {
            execute(target, method, path, null, query).use { response ->
                when {
                    response.isSuccessful -> ApiResult.Ok(parse(response.body.string()))
                    response.code == 401 -> ApiResult.Failure(FailureKind.Unauthorized, "The server refused the token.")
                    response.code == 404 -> ApiResult.Failure(FailureKind.NotFound, "Not found.")
                    else -> ApiResult.Failure(FailureKind.Server, "The server answered ${response.code}.")
                }
            }
        } catch (e: IOException) {
            ApiResult.Failure(FailureKind.Network, e.message ?: "Network error")
        } catch (e: SerializationException) {
            ApiResult.Failure(FailureKind.Server, "Unexpected answer: ${e.message}")
        }
    }

    private suspend fun execute(
        target: Target.Ready,
        method: String,
        path: String,
        body: RequestBody?,
        query: Map<String, String?> = emptyMap(),
    ): Response {
        val url =
            target.base
                .newBuilder()
                .addEncodedPathSegments(path)
                .apply {
                    query.forEach { (name, value) -> if (value != null) addQueryParameter(name, value) }
                }.build()
        val request =
            Request
                .Builder()
                .url(url)
                .header("Authorization", "Bearer ${target.token}")
                .method(method, body)
                .build()
        return withContext(Dispatchers.IO) { client.newCall(request).execute() }
    }

    private suspend fun target(): Target {
        val current = settings()
        if (current.token.isEmpty()) return Target.Missing("No token is set.")
        return when (val check = ServerUrl.check(current.serverUrl, current.privateNetwork)) {
            is UrlCheck.Ok -> Target.Ready(check.base, current.token)
            is UrlCheck.Invalid -> Target.Missing(check.reason)
        }
    }

    private sealed interface Target {
        data class Ready(
            val base: HttpUrl,
            val token: String,
        ) : Target

        data class Missing(
            val reason: String,
        ) : Target
    }

    companion object {
        const val API_VERSION = 1
    }
}
