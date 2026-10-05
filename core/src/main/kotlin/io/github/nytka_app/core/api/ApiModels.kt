package io.github.nytka_app.core.api

import kotlinx.serialization.Serializable

/** A server without `scope` (v0.1) counts as `admin`. */
@Serializable
data class ServerInfo(
    val serverVersion: String,
    val apiVersion: Int,
    val scope: String = SCOPE_ADMIN,
    /** What the server can do beyond v0.1; a server without the field lists nothing. */
    val features: List<String> = emptyList(),
) {
    fun has(feature: String) = feature in features

    companion object {
        const val FEATURE_OFFLINE_SYNC = "offline-sync"
        const val FEATURE_VOICE = "voice"
        const val FEATURE_PEOPLE = "people"
        const val FEATURE_VOICE_GROUPS = "voice-groups"

        const val SCOPE_ADMIN = "admin"
        const val SCOPE_READ = "read"
    }
}

/** What `/status` says about the model calls, v0.2 servers only. */
@Serializable
data class AiStatus(
    val configured: Boolean = false,
    val pending: Int = 0,
    val lastError: String? = null,
    val lastErrorAt: String? = null,
)

@Serializable
data class ServerStatus(
    val pendingChunks: Long,
    val oldestPendingAt: String? = null,
    val lastError: String? = null,
    val lastErrorAt: String? = null,
    val lastSuccessAt: String? = null,
    val ai: AiStatus? = null,
)

/** `aiStatus` of a v0.1 server is absent: nothing was ever summarized. */
object AiState {
    const val NONE = "none"
    const val PENDING = "pending"
    const val DONE = "done"
    const val SKIPPED = "skipped"
    const val FAILED = "failed"
}

@Serializable
data class ConversationSummary(
    val id: String,
    val startedAt: String,
    val endedAt: String,
    val status: String,
    val preview: String,
    val title: String? = null,
    val summary: String? = null,
    val aiStatus: String = AiState.NONE,
    /** How many bookmarks fall in the conversation; a server before v0.8 sends none. */
    val bookmarks: Int = 0,
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
    val speaker: String? = null,
    /** The provider's stable id for the voice; only a segment with one can be named. */
    val speakerId: String? = null,
    /** True for the wearer's own lines; null when the provider did not say. */
    val isUser: Boolean? = null,
    /**
     * What decided [isUser]: "manual" (a mark by the wearer), "voice" (Nytka's voiceprint) or "provider"; null when
     * nothing did, and on a server before 0.12.
     */
    val isUserSource: String? = null,
    val personId: String? = null,
    /** The name given to the voice, or null. */
    val personName: String? = null,
)

/** A task the model found. The `conversation*` fields spare a list a request per source. */
@Serializable
data class NytkaTask(
    val id: String,
    val conversationId: String,
    val text: String,
    val done: Boolean = false,
    val conversationTitle: String? = null,
    val conversationStartedAt: String? = null,
    val doneAt: String? = null,
    val createdAt: String? = null,
    /** The person the task is owed to; server 0.14 and later. */
    val personId: String? = null,
    val personName: String? = null,
)

@Serializable
data class TaskPage(
    val items: List<NytkaTask>,
    val nextBefore: String? = null,
)

@Serializable
data class ConversationDetail(
    val id: String,
    val startedAt: String,
    val endedAt: String,
    val status: String,
    val segments: List<Segment>,
    val title: String? = null,
    val summary: String? = null,
    val aiStatus: String = AiState.NONE,
    val titleEdited: Boolean = false,
    val aiMessage: String? = null,
    val aiUpdatedAt: String? = null,
    val tasks: List<NytkaTask> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
)

/** A tap on the pendant (or a mark from the app) at [at], with the note a person added. */
@Serializable
data class Bookmark(
    val id: String,
    val at: String,
    val note: String? = null,
)

/** One entry of `GET /settings`; values travel as strings, and a secret has no value, only [isSet]. */
@Serializable
data class ServerSetting(
    val key: String,
    val type: String,
    val value: String? = null,
    val isSet: Boolean = false,
    val source: String = "default",
    val locked: Boolean = false,
    val default: String? = null,
) {
    val secret: Boolean get() = type == TYPE_SECRET

    companion object {
        const val TYPE_SECRET = "secret"
        const val TYPE_INT = "int"
        const val TYPE_BOOL = "bool"
    }
}

@Serializable
internal data class SettingsAnswer(
    val items: List<ServerSetting>,
)

@Serializable
data class AccessToken(
    val id: String,
    val name: String,
    val scope: String,
    val hint: String = "",
    val createdAt: String? = null,
    val lastUsedAt: String? = null,
    val revokedAt: String? = null,
)

/** The create answer: the token's fields and [token], the secret, which the server shows once. */
@Serializable
data class CreatedToken(
    val id: String,
    val name: String,
    val scope: String,
    val token: String,
    val hint: String = "",
    val createdAt: String? = null,
) {
    /** Never prints the secret. */
    override fun toString() = "CreatedToken(id=$id, name=$name, scope=$scope, token=<redacted>)"

    /** The listing's view of it, without the secret. */
    fun listed() = AccessToken(id, name, scope, hint, createdAt)
}

@Serializable
internal data class TokensAnswer(
    val items: List<AccessToken>,
)

@Serializable
internal data class UploadAnswer(
    val acceptedThroughSeq: Long,
)

@Serializable
internal data class DiagnosticsAnswer(
    val accepted: Int,
)

/** The part of a 400's problem details that matters: ASP.NET's `errors`, a key or field to its messages. */
@Serializable
internal data class ProblemErrors(
    val errors: Map<String, List<String>>? = null,
)

/**
 * Why a call failed. [Forbidden] is a 403 (the token's scope is too small), [Conflict] a 409 and
 * [Invalid] a 400 that names what was wrong in [ApiResult.Failure.errors]. [Unsupported] is a 405: the server does
 * not know the endpoint, whatever the item. [Unavailable] is a 503 (a model the call needs is not set up) and
 * [Timeout] a 504 (the server's own call to it ran out of time). [BadGateway] is a 502 (that call failed another way). A read or call timeout is also [Timeout].
 */
enum class FailureKind {
    NotConfigured,
    Unauthorized,
    Forbidden,
    NotFound,
    Conflict,
    Invalid,
    Server,
    Network,
    Unsupported,
    Unavailable,
    Timeout,
    BadGateway,
}

sealed interface ApiResult<out T> {
    data class Ok<T>(
        val value: T,
    ) : ApiResult<T>

    data class Failure(
        val kind: FailureKind,
        val message: String,
        /** [FailureKind.Invalid] only: the server's messages by key or field, for the screen and nothing else. */
        val errors: Map<String, List<String>> = emptyMap(),
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

    /** 400: the server cannot read the body; some sample in it is the problem. */
    data object BadRequest : DiagnosticsResult

    /** 413: the body is over the server's limit; a smaller page may fit. */
    data object TooLarge : DiagnosticsResult

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
