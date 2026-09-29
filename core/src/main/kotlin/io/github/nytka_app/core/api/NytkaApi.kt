package io.github.nytka_app.core.api

import io.github.nytka_app.core.chunks.ChunkFormat
import io.github.nytka_app.core.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
) : UploadClient,
    ConversationsClient,
    InfoClient,
    StatusClient,
    DiagnosticsClient {
    /** What the clients in this module decode and encode with; unknown keys are ignored, so a newer server works. */
    internal val json = Json { ignoreUnknownKeys = true }

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
                    // 403: the token cannot upload (a read token); pause and say so instead of retrying forever.
                    401, 403 -> UploadResult.Unauthorized
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

    override suspend fun uploadDiagnostics(samplesJson: String): DiagnosticsResult {
        val target =
            when (val t = target()) {
                is Target.Missing -> return DiagnosticsResult.NotConfigured(t.reason)
                is Target.Ready -> t
            }
        return try {
            val requestBody = samplesJson.toRequestBody("application/json".toMediaType())
            execute(target, "POST", "api/v1/diagnostics", requestBody).use { response ->
                when (response.code) {
                    200 ->
                        DiagnosticsResult.Accepted(
                            json.decodeFromString<DiagnosticsAnswer>(response.body.string()).accepted,
                        )
                    401 -> DiagnosticsResult.Unauthorized
                    404 -> DiagnosticsResult.NotSupported
                    400 -> DiagnosticsResult.BadRequest
                    413 -> DiagnosticsResult.TooLarge
                    else -> DiagnosticsResult.Retry("The server answered ${response.code}.")
                }
            }
        } catch (e: IOException) {
            DiagnosticsResult.Retry(e.message ?: "Network error")
        } catch (e: SerializationException) {
            DiagnosticsResult.Retry("Unexpected answer: ${e.message}")
        }
    }

    override suspend fun info(): ApiResult<ServerInfo> = request("GET", "api/v1/info") { json.decodeFromString(it) }

    override suspend fun status(): ApiResult<ServerStatus> =
        request("GET", "api/v1/status") { json.decodeFromString(it) }

    override suspend fun conversations(
        before: String?,
        limit: Int,
    ): ApiResult<ConversationPage> =
        request("GET", "api/v1/conversations", mapOf("before" to before, "limit" to limit.toString())) {
            json.decodeFromString(it)
        }

    override suspend fun conversation(id: String): ApiResult<ConversationDetail> =
        request("GET", "api/v1/conversations/$id") { json.decodeFromString(it) }

    override suspend fun renameConversation(
        id: String,
        title: String?,
    ): ApiResult<ConversationDetail> =
        request(
            "PATCH",
            "api/v1/conversations/$id",
            body = buildJsonObject { put("title", title?.let(::JsonPrimitive) ?: JsonNull) }.toString(),
        ) { json.decodeFromString(it) }

    override suspend fun enrichConversation(id: String): ApiResult<Unit> =
        request("POST", "api/v1/conversations/$id/enrich") { }

    override suspend fun deleteConversation(id: String): ApiResult<Unit> =
        request("DELETE", "api/v1/conversations/$id") { }

    /** The raw responses, pretty enough for developer mode as they come. */
    override suspend fun transcriptionsJson(id: String): ApiResult<String> =
        request("GET", "api/v1/conversations/$id/transcriptions") { it }

    /**
     * One call to the API, for the clients in this module. [body] is the JSON text to send; a POST or PATCH
     * without one sends an empty body. [parse] reads a success. A failure carries a fixed sentence, never the
     * server's own text, except the field messages of a 400 that names them.
     */
    internal suspend fun <T> request(
        method: String,
        path: String,
        query: Map<String, String?> = emptyMap(),
        body: String? = null,
        parse: (String) -> T,
    ): ApiResult<T> {
        val target =
            when (val t = target()) {
                is Target.Missing -> return ApiResult.Failure(FailureKind.NotConfigured, t.reason)
                is Target.Ready -> t
            }
        return try {
            execute(target, method, path, requestBody(method, body), query).use { response ->
                if (response.isSuccessful) ApiResult.Ok(parse(response.body.string())) else failure(response)
            }
        } catch (e: IOException) {
            ApiResult.Failure(FailureKind.Network, e.message ?: "Network error")
        } catch (e: SerializationException) {
            ApiResult.Failure(FailureKind.Server, "Unexpected answer: ${e.message}")
        }
    }

    private fun failure(response: Response): ApiResult.Failure =
        when (response.code) {
            400 -> invalid(response.body.string()) ?: ApiResult.Failure(FailureKind.Server, "The server answered 400.")
            401 -> ApiResult.Failure(FailureKind.Unauthorized, "The server refused the token.")
            403 -> ApiResult.Failure(FailureKind.Forbidden, "The token is not allowed to do this.")
            // A v0.1 server answers 405 on a method it lacks, such as PATCH /conversations/{id}.
            404, 405 -> ApiResult.Failure(FailureKind.NotFound, "Not found.")
            409 -> ApiResult.Failure(FailureKind.Conflict, "This conflicts with what the server holds.")
            else -> ApiResult.Failure(FailureKind.Server, "The server answered ${response.code}.")
        }

    /** A 400 counts as invalid values only when its problem details carry `errors`. */
    private fun invalid(problem: String): ApiResult.Failure? =
        try {
            json.decodeFromString<ProblemErrors>(problem).errors?.let {
                ApiResult.Failure(FailureKind.Invalid, "The server rejected the request.", it)
            }
        } catch (_: SerializationException) {
            null
        }

    /** OkHttp refuses a POST or PATCH without a body, and calls such as "enrich" have none to send. */
    private fun requestBody(
        method: String,
        body: String?,
    ): RequestBody? =
        when {
            body != null -> body.toRequestBody("application/json".toMediaType())
            method == "POST" || method == "PATCH" || method == "PUT" -> ByteArray(0).toRequestBody()
            else -> null
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
