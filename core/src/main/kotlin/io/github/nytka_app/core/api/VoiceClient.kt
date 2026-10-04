package io.github.nytka_app.core.api

import io.github.nytka_app.core.chunks.ChunkFormat
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** `GET /api/v1/voice` of server 0.12: never the voiceprint itself. */
@Serializable
data class VoiceStatus(
    val enrolled: Boolean = false,
    val enrolledAt: String? = null,
    val updatedAt: String? = null,
    val enrolledSamples: Int = 0,
    val learnedSegments: Int = 0,
    val modelAvailable: Boolean = false,
)

/** `replace` starts over; `add` blends the new reading into the voiceprint. */
enum class EnrollMode(
    val query: String,
) {
    Replace("replace"),
    Add("add"),
}

/** The `reason` of a `422`: which rule the reading failed. */
enum class EnrollRefusal(
    val reason: String,
) {
    TooLittleSpeech("too-little-speech"),
    TooFewSamples("too-few-samples"),
    SamplesDisagree("samples-disagree"),
}

/** What the server made of a reading. Each refusal the server names has its own case, so the screen can say which. */
sealed interface EnrollResult {
    data class Enrolled(
        val speechSeconds: Double,
        val samples: Int,
        val minAgreement: Double,
    ) : EnrollResult

    /** A `422`; [refusal] is null for a reason this app does not know. */
    data class Refused(
        val refusal: EnrollRefusal?,
        val speechSeconds: Double,
        val samples: Int,
    ) : EnrollResult

    /** `413`: over 120 s. */
    data object TooLong : EnrollResult

    /** `400` or `415`: the server could not read the audio. */
    data object Unreadable : EnrollResult

    /** `409`: `add` onto a voiceprint another speaker model made. */
    data object OtherModel : EnrollResult

    /** `503`: the server has no speaker model. */
    data object NoModel : EnrollResult

    /** Anything else: the token, the network, a server without the route. */
    data class Failed(
        val failure: ApiResult.Failure,
    ) : EnrollResult
}

@Serializable
private data class EnrollAnswer(
    val speechSeconds: Double = 0.0,
    val samples: Int = 0,
    val minAgreement: Double? = null,
    val reason: String? = null,
)

/** The voice endpoints of docs/specs/your-voice.md in nytka-app/server; all need an admin token. */
interface VoiceClient {
    suspend fun voice(): ApiResult<VoiceStatus>

    /** [body] is one or more chunks back to back, at most 120 s of frames. */
    suspend fun enroll(
        body: ByteArray,
        mode: EnrollMode,
    ): EnrollResult

    /** Back to the enrolled voiceprint; [FailureKind.NotFound] with nothing enrolled. */
    suspend fun reset(): ApiResult<VoiceStatus>

    /** Forget my voice: also succeeds with nothing enrolled. */
    suspend fun forget(): ApiResult<Unit>
}

class VoiceApi(
    private val api: NytkaApi,
) : VoiceClient {
    override suspend fun voice(): ApiResult<VoiceStatus> =
        api.request("GET", "api/v1/voice") { api.json.decodeFromString(it) }

    override suspend fun enroll(
        body: ByteArray,
        mode: EnrollMode,
    ): EnrollResult {
        val result =
            api.exchange(
                "POST",
                "api/v1/voice/enrollment",
                body.toRequestBody(ChunkFormat.MEDIA_TYPE.toMediaType()),
                mapOf("mode" to mode.query),
            ) { code, text ->
                when (code) {
                    200 ->
                        api.json.decodeFromString<EnrollAnswer>(text()).let {
                            ApiResult.Ok(EnrollResult.Enrolled(it.speechSeconds, it.samples, it.minAgreement ?: 0.0))
                        }

                    422 ->
                        api.json.decodeFromString<EnrollAnswer>(text()).let { problem ->
                            ApiResult.Ok(
                                EnrollResult.Refused(
                                    EnrollRefusal.entries.firstOrNull { it.reason == problem.reason },
                                    problem.speechSeconds,
                                    problem.samples,
                                ),
                            )
                        }

                    413 -> ApiResult.Ok(EnrollResult.TooLong)
                    400, 415 -> ApiResult.Ok(EnrollResult.Unreadable)
                    409 -> ApiResult.Ok(EnrollResult.OtherModel)
                    503 -> ApiResult.Ok(EnrollResult.NoModel)
                    else -> null
                }
            }
        return when (result) {
            is ApiResult.Ok -> result.value
            is ApiResult.Failure -> EnrollResult.Failed(result)
        }
    }

    override suspend fun reset(): ApiResult<VoiceStatus> =
        api.request("POST", "api/v1/voice/reset") { api.json.decodeFromString(it) }

    override suspend fun forget(): ApiResult<Unit> = api.request("DELETE", "api/v1/voice") { }
}
