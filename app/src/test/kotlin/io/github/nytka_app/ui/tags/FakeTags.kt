package io.github.nytka_app.ui.tags

import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.ConversationPage
import io.github.nytka_app.core.api.FailureKind
import io.github.nytka_app.core.api.InfoClient
import io.github.nytka_app.core.api.Person
import io.github.nytka_app.core.api.ServerInfo
import io.github.nytka_app.core.api.Tag
import io.github.nytka_app.core.api.TagSuggestion
import io.github.nytka_app.core.api.TagsClient
import kotlinx.coroutines.CompletableDeferred

/** `/info` of a server with these [features] and an [scope] token. */
fun fakeInfo(
    vararg features: String,
    scope: String = ServerInfo.SCOPE_ADMIN,
): InfoClient = InfoClient { ApiResult.Ok(ServerInfo("0.18.0", 1, scope, features.toList())) }

/** Writes are recorded as `"add conversations c1 work"`; `answer` is what every add and remove returns. */
class FakeTags : TagsClient {
    var inUse: List<Tag> = emptyList()
    var answer: ApiResult<List<String>> = ApiResult.Ok(emptyList())
    val queries = mutableListOf<String?>()
    val writes = mutableListOf<String>()

    /** Holds back the answer of the next write until it completes. */
    var gate: CompletableDeferred<Unit>? = null

    val calls get() = queries.size + writes.size

    /** The pending proposals. An answer removes one, except a `409` set to [stayPending] (the item is full). */
    var pending: ApiResult<List<TagSuggestion>> = ApiResult.Ok(emptyList())
    var suggestionAnswer: ApiResult<Unit> = ApiResult.Ok(Unit)
    var stayPending = false

    /** Holds back the next answer to a proposal until it completes. */
    var suggestionGate: CompletableDeferred<Unit>? = null
    var listed = 0
    val answered = mutableListOf<Pair<String, Boolean>>()

    override suspend fun tags(q: String?): ApiResult<List<Tag>> {
        queries += q
        return ApiResult.Ok(inUse.filter { q == null || it.name.startsWith(q) })
    }

    override suspend fun addConversationTag(
        id: String,
        name: String,
    ) = write("add", "conversations", id, name)

    override suspend fun removeConversationTag(
        id: String,
        name: String,
    ) = write("remove", "conversations", id, name)

    override suspend fun addPersonTag(
        id: String,
        name: String,
    ) = write("add", "people", id, name)

    override suspend fun removePersonTag(
        id: String,
        name: String,
    ) = write("remove", "people", id, name)

    private suspend fun write(
        verb: String,
        area: String,
        id: String,
        name: String,
    ): ApiResult<List<String>> {
        writes += "$verb $area $id $name"
        gate?.await()
        return answer
    }

    override suspend fun conversations(
        tag: String,
        before: String?,
        limit: Int,
    ): ApiResult<ConversationPage> = error("Not used by the chips.")

    override suspend fun people(tag: String): ApiResult<List<Person>> = error("Not used by the chips.")

    override suspend fun suggestions(): ApiResult<List<TagSuggestion>> = pending.also { listed++ }

    override suspend fun answerSuggestion(
        id: String,
        accept: Boolean,
    ): ApiResult<Unit> {
        answered += id to accept
        suggestionGate?.await()
        val result = suggestionAnswer
        val answeredByServer =
            result is ApiResult.Ok ||
                (
                    (result as? ApiResult.Failure)?.kind in listOf(FailureKind.Conflict, FailureKind.NotFound) &&
                        !stayPending
                )
        if (answeredByServer) {
            (pending as? ApiResult.Ok)?.let { p ->
                pending =
                    ApiResult.Ok(p.value.filterNot { it.id == id })
            }
        }
        return result
    }
}

fun proposal(
    id: String,
    name: String = "work",
    conversationId: String = "c1",
    personId: String? = null,
    personName: String? = null,
) = TagSuggestion(id, conversationId, personId, personName, name, "2026-10-05T10:00:00Z")
