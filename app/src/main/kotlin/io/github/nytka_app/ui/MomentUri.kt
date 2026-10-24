package io.github.nytka_app.ui

import io.github.nytka_app.ui.conversations.MomentLink
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** A moment of a conversation that a link from outside the app asks for; [atMs] and [segmentId] are optional. */
data class Moment(
    val conversationId: String,
    val atMs: Long? = null,
    val segmentId: Long? = null,
)

/**
 * Links the server writes elsewhere, such as a commitment in the calendar feed:
 * `nytka://conversation/{id}?at=<ISO instant or epoch ms>&segment=<id>`. Anything else, including an id that is not
 * a plain id, reads as null, so a link from another app can only open a conversation of this server.
 */
object MomentUri {
    private val id = Regex("^[A-Za-z0-9-]{1,64}$")

    fun parse(text: String?): Moment? {
        val uri = text?.let { runCatching { URI(it) }.getOrNull() } ?: return null
        if (uri.scheme != SCHEME || uri.host != HOST) return null
        val conversation = uri.path?.trim('/')?.takeIf(id::matches) ?: return null
        val query = queryOf(uri.rawQuery)
        return Moment(
            conversation,
            atMs = query["at"]?.let(MomentLink::instantOf)?.toEpochMilli(),
            segmentId = query["segment"]?.toLongOrNull(),
        )
    }

    private fun queryOf(raw: String?): Map<String, String> =
        raw
            ?.split('&')
            ?.mapNotNull { part ->
                val (name, value) = part.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
                name to URLDecoder.decode(value, StandardCharsets.UTF_8)
            }?.toMap()
            .orEmpty()

    const val SCHEME = "nytka"
    const val HOST = "conversation"
}
