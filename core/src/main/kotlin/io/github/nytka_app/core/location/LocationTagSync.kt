package io.github.nytka_app.core.location

import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.ConversationsClient
import io.github.nytka_app.core.api.TagsClient
import io.github.nytka_app.core.settings.SettingsSource
import java.time.Instant

enum class LocationTagResult { OFF, EMPTY, EXPIRED, TAGGED, NO_MATCH, FAILED }

/**
 * Matches the place a session recorded at its start to the conversation it became, by time, and adds it as a tag
 * through [TagsClient.addConversationTag] — the same write a person uses to tag a conversation by hand. Nothing
 * links a session to a conversation id, so a pending place older than [MAX_AGE_MS] is dropped instead of landing
 * on whatever conversation happens to be newest by then.
 */
class LocationTagSync(
    private val settings: SettingsSource,
    private val conversations: ConversationsClient,
    private val tags: TagsClient,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun sync(): LocationTagResult {
        val current = settings.current()
        if (!current.locationTags) return LocationTagResult.OFF
        val place = current.pendingLocationPlace ?: return LocationTagResult.EMPTY
        if (clock() - current.pendingLocationAt > MAX_AGE_MS) {
            clearPending()
            return LocationTagResult.EXPIRED
        }
        return when (val page = conversations.conversations(before = null, limit = PAGE_SIZE)) {
            is ApiResult.Failure -> LocationTagResult.FAILED
            is ApiResult.Ok -> {
                val match =
                    page.value.items.firstOrNull { startedNear(it.startedAt, current.pendingLocationAt) }
                        ?: return LocationTagResult.NO_MATCH
                tag(match.id, place)
            }
        }
    }

    private suspend fun tag(
        conversationId: String,
        place: String,
    ): LocationTagResult =
        when (tags.addConversationTag(conversationId, place)) {
            is ApiResult.Ok -> {
                clearPending()
                LocationTagResult.TAGGED
            }
            is ApiResult.Failure -> LocationTagResult.FAILED
        }

    private fun startedNear(
        startedAt: String,
        pendingAtMs: Long,
    ): Boolean {
        val started = runCatching { Instant.parse(startedAt).toEpochMilli() }.getOrNull() ?: return false
        return started in (pendingAtMs - TOLERANCE_MS)..(pendingAtMs + MAX_AGE_MS)
    }

    private suspend fun clearPending() = settings.update { it.copy(pendingLocationPlace = null, pendingLocationAt = 0) }

    private companion object {
        const val PAGE_SIZE = 20
        const val TOLERANCE_MS = 5 * 60_000L
        const val MAX_AGE_MS = 24 * 60 * 60_000L
    }
}
