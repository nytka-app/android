package io.github.nytka_app.ui.conversations

import kotlinx.coroutines.flow.StateFlow

/** What the play bar shows. [failed] is set when the stream could not be played. */
data class PlayerState(
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val failed: Boolean = false,
)

/** The player of one conversation's audio, apart from ExoPlayer so the view model can be tested with a fake. */
interface AudioPlayer {
    val state: StateFlow<PlayerState>

    /** Points the player at the audio of [conversationId]; false when there is no server address or token. */
    suspend fun prepare(conversationId: String): Boolean

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    /** Stops and frees the player; nothing works afterwards. */
    fun release()
}
