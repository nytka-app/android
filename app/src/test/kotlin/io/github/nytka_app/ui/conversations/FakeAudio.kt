package io.github.nytka_app.ui.conversations

import io.github.nytka_app.core.api.ApiResult
import io.github.nytka_app.core.api.AudioClient
import io.github.nytka_app.core.api.AudioIndex
import io.github.nytka_app.core.api.AudioRequest
import io.github.nytka_app.core.api.FailureKind
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAudio : AudioClient {
    var index: ApiResult<AudioIndex> = ApiResult.Failure(FailureKind.NotFound, "Not found.")

    override suspend fun audioIndex(id: String) = index

    override suspend fun audioRequest(id: String) = AudioRequest("https://nytka.test/audio", "Bearer x")
}

class FakePlayer : AudioPlayer {
    override val state = MutableStateFlow(PlayerState())
    val calls = mutableListOf<String>()
    var prepares = 0
    var canPrepare = true
    val released get() = calls.contains("release")

    override suspend fun prepare(conversationId: String): Boolean {
        prepares++
        return canPrepare
    }

    override fun play() {
        calls += "play"
        state.value = state.value.copy(playing = true)
    }

    override fun pause() {
        calls += "pause"
        state.value = state.value.copy(playing = false)
    }

    override fun seekTo(positionMs: Long) {
        calls += "seek $positionMs"
        state.value = state.value.copy(positionMs = positionMs)
    }

    override fun release() {
        calls += "release"
    }
}
