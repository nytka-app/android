package io.github.nytka_app.core.api

import kotlinx.serialization.Serializable

/** One side of a ledger: up to 100 open promises, newest first, and 100 done, last done first. */
@Serializable
data class LedgerSide(
    val open: List<NytkaTask> = emptyList(),
    val done: List<NytkaTask> = emptyList(),
)

/** What you owe a person ([owedByMe]) and what they owe you ([owedToMe]); server 0.20 (`ledger`). */
@Serializable
data class Ledger(
    val personId: String,
    val personName: String,
    val owedByMe: LedgerSide = LedgerSide(),
    val owedToMe: LedgerSide = LedgerSide(),
)

/** The promise ledger of a person. A server without it answers [FailureKind.NotFound] or [FailureKind.Unsupported]. */
interface LedgerClient {
    suspend fun ledger(personId: String): ApiResult<Ledger>
}

class LedgerApi(
    private val api: NytkaApi,
) : LedgerClient {
    override suspend fun ledger(personId: String): ApiResult<Ledger> =
        api.request("GET", "api/v1/people/$personId/ledger") { api.json.decodeFromString(it) }
}
