package io.github.nytka_app.ui.people.cards

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Plays at once and records each call; [fail] stands for a failure ExoPlayer reports. */
class FakeClipPlayer : ClipPlayer {
    val mutable = MutableStateFlow(ClipState())
    override val state: StateFlow<ClipState> = mutable
    val calls = mutableListOf<String>()
    var released = false

    override fun play(
        kind: String,
        id: String,
    ) {
        calls += "play $kind/$id"
        mutable.value = ClipState(clipKey(kind, id), playing = true)
    }

    override fun stop() {
        calls += "stop"
        mutable.value = ClipState()
    }

    override fun release() {
        released = true
    }

    fun fail(failure: ClipFailure) {
        mutable.value = mutable.value.copy(playing = false, failure = failure)
    }
}
