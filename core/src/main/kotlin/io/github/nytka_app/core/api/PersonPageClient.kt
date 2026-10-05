package io.github.nytka_app.core.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** What an update does to a person's note: leave it, clear it (`note: null`) or replace it. */
sealed interface NoteChange {
    data object Keep : NoteChange

    data object Clear : NoteChange

    data class Set(
        val text: String,
    ) : NoteChange
}

@Serializable
private data class FactTextBody(
    val text: String,
)

/**
 * The person page and its facts (server 0.14). A server without them answers [FailureKind.NotFound] or
 * [FailureKind.Unsupported]; a `404` on one person with the person still in the list means the same.
 */
interface PersonPageClient {
    suspend fun person(id: String): ApiResult<PersonPage>

    /** Sends only what changes; [FailureKind.Conflict] when another person has [name]. Answers the person as stored. */
    suspend fun update(
        id: String,
        name: String?,
        note: NoteChange,
    ): ApiResult<Person>

    /** Newest first; [before] is the previous page's `nextBefore`. */
    suspend fun facts(
        id: String,
        before: String?,
        limit: Int,
    ): ApiResult<FactPage>

    /** [FailureKind.Conflict] when a live fact of the person holds [text]. */
    suspend fun addFact(
        id: String,
        text: String,
    ): ApiResult<PersonFact>

    suspend fun editFact(
        id: String,
        factId: String,
        text: String,
    ): ApiResult<PersonFact>

    suspend fun deleteFact(
        id: String,
        factId: String,
    ): ApiResult<Unit>
}

class PersonPageApi(
    private val api: NytkaApi,
) : PersonPageClient {
    override suspend fun person(id: String): ApiResult<PersonPage> =
        api.request("GET", "api/v1/people/$id") { api.json.decodeFromString(it) }

    override suspend fun update(
        id: String,
        name: String?,
        note: NoteChange,
    ): ApiResult<Person> =
        api.request(
            "PATCH",
            "api/v1/people/$id",
            body =
                buildJsonObject {
                    name?.let { put("name", it) }
                    when (note) {
                        NoteChange.Keep -> Unit
                        NoteChange.Clear -> put("note", JsonNull)
                        is NoteChange.Set -> put("note", JsonPrimitive(note.text))
                    }
                }.toString(),
        ) { api.json.decodeFromString(it) }

    override suspend fun facts(
        id: String,
        before: String?,
        limit: Int,
    ): ApiResult<FactPage> =
        api.request("GET", "api/v1/people/$id/facts", mapOf("before" to before, "limit" to limit.toString())) {
            api.json.decodeFromString(it)
        }

    override suspend fun addFact(
        id: String,
        text: String,
    ): ApiResult<PersonFact> =
        api.request("POST", "api/v1/people/$id/facts", body = api.json.encodeToString(FactTextBody(text))) {
            api.json.decodeFromString(it)
        }

    override suspend fun editFact(
        id: String,
        factId: String,
        text: String,
    ): ApiResult<PersonFact> =
        api.request("PATCH", "api/v1/people/$id/facts/$factId", body = api.json.encodeToString(FactTextBody(text))) {
            api.json.decodeFromString(it)
        }

    override suspend fun deleteFact(
        id: String,
        factId: String,
    ): ApiResult<Unit> = api.request("DELETE", "api/v1/people/$id/facts/$factId") { }
}
