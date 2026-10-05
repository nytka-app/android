package io.github.nytka_app.ui.people.cards

import kotlinx.coroutines.flow.StateFlow

/** Why a clip stopped: its audio is gone (`404`), or anything else. */
enum class ClipFailure { Gone, Other }

/** What the cards show of the clip being played; [key] is a [clipKey], null when nothing plays. */
data class ClipState(
    val key: String? = null,
    val playing: Boolean = false,
    val failure: ClipFailure? = null,
)

fun clipKey(
    kind: String,
    id: String,
) = "$kind/$id"

/** Plays the clip of one card at a time; apart from ExoPlayer so the view model can be tested with a fake. */
interface ClipPlayer {
    val state: StateFlow<ClipState>

    /** Starts the clip of a card, replacing any other. Failures arrive in [state]. */
    fun play(
        kind: String,
        id: String,
    )

    /** Stops and forgets the current clip; [state] goes back to the default. */
    fun stop()

    fun release()
}
