package io.github.nytka_app.core.api

import kotlinx.serialization.Serializable

@Serializable
data class ServerInfo(
    val serverVersion: String,
    val apiVersion: Int,
)

@Serializable
data class ServerStatus(
    val pendingChunks: Long,
    val oldestPendingAt: String? = null,
    val lastError: String? = null,
)

@Serializable
data class ConversationSummary(
    val id: String,
    val startedAt: String,
    val endedAt: String,
    val status: String,
    val preview: String,
)

@Serializable
data class ConversationPage(
    val items: List<ConversationSummary>,
    val nextBefore: String? = null,
)

@Serializable
data class Segment(
    val id: Long,
    val startedAt: String,
    val endedAt: String,
    val text: String,
)

@Serializable
data class ConversationDetail(
    val id: String,
    val startedAt: String,
    val endedAt: String,
    val status: String,
    val segments: List<Segment>,
)

@Serializable
internal data class UploadAnswer(
    val acceptedThroughSeq: Long,
)

@Serializable
internal data class DiagnosticsAnswer(
    val accepted: Int,
)

enum class FailureKind { NotConfigured, Unauthorized, NotFound, Server, Network }

sealed interface ApiResult<out T> {
    data class Ok<T>(
        val value: T,
    ) : ApiResult<T>

    data class Failure(
        val kind: FailureKind,
        val message: String,
    ) : ApiResult<Nothing>
}

sealed interface UploadResult {
    /** 202 (stored) or 200 (the server already had it): the chunk can leave the queue. */
    data class Accepted(
        val throughSeq: Long,
        val duplicate: Boolean,
    ) : UploadResult

    /** 400, 409 or 413: retrying cannot help, so the chunk leaves the queue with this reason. */
    data class Dropped(
        val code: Int,
        val reason: String,
    ) : UploadResult

    data object Unauthorized : UploadResult

    data class NotConfigured(
        val reason: String,
    ) : UploadResult

    data class Retry(
        val reason: String,
    ) : UploadResult
}

fun interface UploadClient {
    suspend fun upload(body: ByteArray): UploadResult
}

sealed interface DiagnosticsResult {
    /** 200: the server has these samples (a retry of the same ids is harmless). */
    data class Accepted(
        val count: Int,
    ) : DiagnosticsResult

    /** 404: an older server without the endpoint. */
    data object NotSupported : DiagnosticsResult

    data object Unauthorized : DiagnosticsResult

    data class NotConfigured(
        val reason: String,
    ) : DiagnosticsResult

    data class Retry(
        val reason: String,
    ) : DiagnosticsResult
}

fun interface DiagnosticsClient {
    /** [samplesJson] is a JSON array of 1 to 500 samples. */
    suspend fun uploadDiagnostics(samplesJson: String): DiagnosticsResult
}
