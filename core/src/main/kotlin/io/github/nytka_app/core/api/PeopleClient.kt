package io.github.nytka_app.core.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A named person; [voices] are the speaker ids the server matched to them, [segments] their lines. */
@Serializable
data class Person(
    val id: String,
    val name: String,
    val createdAt: String = "",
    val voices: List<String> = emptyList(),
    val segments: Int = 0,
    /** Your own note; the server sends it with the list. */
    val note: String? = null,
    /** Newest segment of the person and their live facts (server 0.14 and later), so both are optional. */
    val lastSeenAt: String? = null,
    val factCount: Int? = null,
    /** Sorted tag names (feature `tags`). */
    val tags: List<String> = emptyList(),
    /** False for a person known only by a role, such as "Repairman" (feature `roles`). */
    val named: Boolean = true,
)

/** A voice not yet named and not the wearer's. [label] is the transcription service's own, such as `SPEAKER_01`. */
@Serializable
data class UnnamedVoice(
    val speakerId: String,
    val label: String? = null,
    val segments: Int = 0,
    val lastSeenAt: String = "",
)

@Serializable
private data class PersonList(
    val items: List<Person>,
)

@Serializable
private data class VoiceList(
    val items: List<UnnamedVoice>,
)

@Serializable
private data class ForgetAnswer(
    val forgotten: Boolean = false,
)

/** What forgetting a person removed, one number per kind; [conversations] are the ones queued for a new summary. */
@Serializable
data class ForgetCounts(
    val lines: Int = 0,
    val facts: Int = 0,
    val memories: Int = 0,
    val tasks: Int = 0,
    val notes: Int = 0,
    val voiceprints: Int = 0,
    val voiceMatches: Int = 0,
    val voiceGroups: Int = 0,
    val nameSuggestions: Int = 0,
    val tags: Int = 0,
    val tagSuggestions: Int = 0,
    val topicPages: Int = 0,
    val events: Int = 0,
    val briefs: Int = 0,
    val conversations: Int = 0,
)

/** The answer of the forget call; [voiceprintsForgotten]: the transcription service dropped the voiceprints too. */
@Serializable
data class ForgetResult(
    val counts: ForgetCounts = ForgetCounts(),
    val voiceprintsForgotten: Boolean = false,
)

/** The people endpoints of v0.7; a server before them answers [FailureKind.NotFound] or [FailureKind.Unsupported]. */
interface PeopleClient {
    suspend fun people(): ApiResult<List<Person>>

    suspend fun voices(): ApiResult<List<UnnamedVoice>>

    /** [FailureKind.Conflict] when another person has [name]. */
    suspend fun renamePerson(
        id: String,
        name: String,
    ): ApiResult<Unit>

    /** A [name] that exists already joins the voice to that person. */
    suspend fun nameVoice(
        speakerId: String,
        name: String,
    ): ApiResult<Unit>

    /** Folds [id] into [intoId]. */
    suspend fun mergePerson(
        id: String,
        intoId: String,
    ): ApiResult<Unit>

    suspend fun deletePerson(id: String): ApiResult<Unit>

    /** Deletes the person and asks the transcription service to drop their voiceprints; true when it did. */
    suspend fun forgetPerson(id: String): ApiResult<Boolean>

    /** Removes the person and what is built from their lines (server 0.36.6 and later, admin token). */
    suspend fun forgetEverything(id: String): ApiResult<ForgetResult>
}

class PeopleApi(
    private val api: NytkaApi,
) : PeopleClient {
    override suspend fun people(): ApiResult<List<Person>> =
        api.request("GET", "api/v1/people") { api.json.decodeFromString<PersonList>(it).items }

    override suspend fun voices(): ApiResult<List<UnnamedVoice>> =
        api.request("GET", "api/v1/voices") { api.json.decodeFromString<VoiceList>(it).items }

    override suspend fun renamePerson(
        id: String,
        name: String,
    ): ApiResult<Unit> =
        api.request("PATCH", "api/v1/people/$id", body = buildJsonObject { put("name", name) }.toString()) { }

    override suspend fun nameVoice(
        speakerId: String,
        name: String,
    ): ApiResult<Unit> = api.nameVoice(speakerId, name)

    override suspend fun mergePerson(
        id: String,
        intoId: String,
    ): ApiResult<Unit> =
        api.request("POST", "api/v1/people/$id/merge", body = buildJsonObject { put("intoId", intoId) }.toString()) { }

    override suspend fun deletePerson(id: String): ApiResult<Unit> = api.request("DELETE", "api/v1/people/$id") { }

    override suspend fun forgetPerson(id: String): ApiResult<Boolean> =
        api.request("DELETE", "api/v1/people/$id", mapOf("forget" to "true")) {
            api.json.decodeFromString<ForgetAnswer>(it).forgotten
        }

    override suspend fun forgetEverything(id: String): ApiResult<ForgetResult> =
        api.request("POST", "api/v1/people/$id/forget") { api.json.decodeFromString<ForgetResult>(it) }
}
