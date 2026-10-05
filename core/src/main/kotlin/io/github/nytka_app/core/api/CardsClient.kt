package io.github.nytka_app.core.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
private data class CardList(
    val items: List<VoiceCard> = emptyList(),
)

/** How the owner answers a card; exactly one goes to the server. */
sealed interface CardAnswer {
    /** Names a group after an existing person, or confirms a match (the card's own person). */
    data class Person(
        val id: String,
    ) : CardAnswer

    /** Names a group; the server reuses a person of that name. */
    data class Name(
        val text: String,
    ) : CardAnswer

    /** Hides the card for 7 days. */
    data object Skip : CardAnswer

    /** "Not a person" for a group, "not them" for a match. */
    data object Reject : CardAnswer
}

/**
 * The "Who is this?" cards (server 0.14); all three routes need an admin token. A server without them answers
 * [FailureKind.NotFound] or [FailureKind.Unsupported].
 */
interface CardsClient {
    suspend fun cards(): ApiResult<List<VoiceCard>>

    /** Naming or confirming answers `Ok`, like skipping and rejecting; the lists are read again by the caller. */
    suspend fun answer(
        card: VoiceCard,
        answer: CardAnswer,
    ): ApiResult<Unit>

    /** The clip of a card, for the player to fetch with range requests; null when no server address or token is set. */
    suspend fun clipRequest(
        kind: String,
        id: String,
    ): AudioRequest?
}

class CardsApi(
    private val api: NytkaApi,
) : CardsClient {
    override suspend fun cards(): ApiResult<List<VoiceCard>> =
        api.request("GET", "api/v1/people/cards") { api.json.decodeFromString<CardList>(it).items }

    override suspend fun answer(
        card: VoiceCard,
        answer: CardAnswer,
    ): ApiResult<Unit> =
        api.request(
            "POST",
            "api/v1/people/cards/${card.kind}/${card.id}",
            body =
                buildJsonObject {
                    when (answer) {
                        is CardAnswer.Person -> put("personId", answer.id)
                        is CardAnswer.Name -> put("name", answer.text)
                        CardAnswer.Skip -> put("skip", true)
                        CardAnswer.Reject -> put("reject", true)
                    }
                }.toString(),
        ) { }

    override suspend fun clipRequest(
        kind: String,
        id: String,
    ): AudioRequest? = api.authorized("api/v1/people/cards/$kind/$id/clip")
}
