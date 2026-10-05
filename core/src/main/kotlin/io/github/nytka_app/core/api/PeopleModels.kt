package io.github.nytka_app.core.api

import kotlinx.serialization.Serializable

/**
 * The person page of `GET /people/{id}`. Every field but [id] has a default, so a field a server lacks still decodes.
 * The server never sends a voiceprint: [hasVoiceprint] and [voiceprintSamples] are all the phone learns of it.
 */
@Serializable
data class PersonPage(
    val id: String,
    val name: String = "",
    val note: String? = null,
    val createdAt: String = "",
    val lastSeenAt: String? = null,
    val voices: List<String> = emptyList(),
    val hasVoiceprint: Boolean = false,
    val voiceprintSamples: Int = 0,
    val conversations: List<PersonConversation> = emptyList(),
    val facts: List<PersonFact> = emptyList(),
    val openTasks: List<NytkaTask> = emptyList(),
    val tags: List<String> = emptyList(),
    /** False for a person known only by a role. */
    val named: Boolean = true,
)

/** A conversation the person spoke in. */
@Serializable
data class PersonConversation(
    val id: String,
    val title: String? = null,
    val startedAt: String = "",
)

/**
 * A lasting fact about a person. [source] is `ai` or `user`; [basis] is `said`, `about`, `mentioned`, or null for a
 * fact added by hand, which also has no conversation or segment.
 */
@Serializable
data class PersonFact(
    val id: String,
    val personId: String = "",
    val text: String = "",
    val source: String = "",
    val basis: String? = null,
    val conversationId: String? = null,
    val conversationTitle: String? = null,
    val segmentId: Long? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
)

/** [nextBefore] is the id to pass as `before` for the next page, null on the last one. */
@Serializable
data class FactPage(
    val items: List<PersonFact> = emptyList(),
    val nextBefore: String? = null,
)

/** The line that carries a suggested name. */
@Serializable
data class SuggestionEvidence(
    val segmentId: Long = 0,
    val startedAt: String = "",
    val text: String = "",
)

/** A name the model proposed. [target] is `speaker`, `label` or `group`; [personId] is set for a known person. */
@Serializable
data class NameSuggestion(
    val id: String,
    val conversationId: String = "",
    val target: String = "",
    val speakerId: String? = null,
    val groupId: String? = null,
    val name: String = "",
    val personId: String? = null,
    val confidence: Double = 0.0,
    val evidence: SuggestionEvidence? = null,
    /**
     * A role the voice has ("repairman"). With [named] false the suggestion has no name yet and [name] holds the
     * role in display form, so an older app reads it sensibly.
     */
    val role: String? = null,
    val named: Boolean = true,
    /**
     * Pending suggestions with this name in any case, this one included (server 0.21 and later); 1 where the server
     * sends none.
     */
    val sameName: Int = 1,
)

/** What accepting every suggestion of a name did: [personId] is the one person; [skipped] no longer applied. */
data class AcceptedByName(
    val personId: String?,
    val accepted: Int,
    val skipped: Int,
)

/** The stretch of capture a card's clip covers; the app asks for the audio, not for these times. */
@Serializable
data class CardClip(
    val from: String = "",
    val until: String = "",
)

@Serializable
data class CardLine(
    val segmentId: Long = 0,
    val startedAt: String = "",
    val text: String = "",
)

/**
 * "Who is this?" ([kind] `group`, no person) or "Is this Olena?" ([kind] `match`, with [personId], [personName] and
 * [similarity]). [id] with [kind] names the card in its routes.
 */
@Serializable
data class VoiceCard(
    val kind: String,
    val id: String,
    val conversationId: String = "",
    val conversationTitle: String? = null,
    val personId: String? = null,
    val personName: String? = null,
    val similarity: Double? = null,
    val clip: CardClip? = null,
    val lines: List<CardLine> = emptyList(),
) {
    companion object {
        const val KIND_GROUP = "group"
        const val KIND_MATCH = "match"
    }
}

/** What Nytka proposes for a review item; which fields are set depends on the item's kind. */
@Serializable
data class ReviewProposal(
    val name: String? = null,
    val personId: String? = null,
    val confidence: Double? = null,
    val similarity: Double? = null,
    val isUser: Boolean? = null,
    /** Kind `tag`: the proposed tag. Kind `name`: [role] and [named] as on [NameSuggestion]. */
    val tag: String? = null,
    val role: String? = null,
    val named: Boolean = true,
    /** Kind `speech`: the guess ("media", "call" or "unsure") and the lines of the stretch. */
    val speechKind: String? = null,
    val lines: List<SpeechLine> = emptyList(),
)

/** One line of a `speech` item's stretch. */
@Serializable
data class SpeechLine(
    val segmentId: Long = 0,
    val startedAt: String = "",
    val text: String = "",
)

/** One thing waiting for an answer. [id] is a guid, or a segment number for a `label`. */
@Serializable
data class ReviewItem(
    val kind: String,
    val id: String,
    val conversationId: String = "",
    val conversationTitle: String? = null,
    val at: String = "",
    val text: String = "",
    val proposal: ReviewProposal = ReviewProposal(),
) {
    companion object {
        const val KIND_NAME = "name"
        const val KIND_VOICE = "voice"
        const val KIND_LABEL = "label"
        const val KIND_TAG = "tag"
        const val KIND_SPEECH = "speech"
    }
}

/** An attendee of an event; [personId] is the person whose name equals theirs, else null. */
@Serializable
data class BriefAttendee(
    val name: String = "",
    val personId: String? = null,
)

@Serializable
data class BriefText(
    val id: String,
    val text: String = "",
    val createdAt: String = "",
)

/** A calendar event not over yet; [brief] is null before it is made or when nobody matches. */
@Serializable
data class UpcomingBrief(
    val uid: String,
    val title: String = "",
    val startsAt: String = "",
    val endsAt: String = "",
    val attendees: List<BriefAttendee> = emptyList(),
    val brief: BriefText? = null,
)
