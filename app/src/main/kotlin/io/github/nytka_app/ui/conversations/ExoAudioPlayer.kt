package io.github.nytka_app.ui.conversations

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.github.nytka_app.core.api.AudioClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * ExoPlayer over the app's OkHttp client, with the bearer header on every request (the client never follows a
 * redirect, so the token stays with the server). Used on the main thread only. The player is made on [prepare], so a
 * conversation nobody plays costs nothing. Nothing about the audio or the token is logged.
 */
@OptIn(UnstableApi::class)
class ExoAudioPlayer(
    private val context: Context,
    private val client: OkHttpClient,
    private val audio: AudioClient,
) : AudioPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = mutableState.asStateFlow()
    private var player: ExoPlayer? = null
    private var ticker: Job? = null

    override suspend fun prepare(conversationId: String): Boolean {
        val request = audio.audioRequest(conversationId) ?: return false
        val factory =
            OkHttpDataSource
                .Factory(client)
                .setDefaultRequestProperties(mapOf("Authorization" to request.authorization))
        val exo =
            ExoPlayer
                .Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(factory))
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .build(),
                    true,
                ).setHandleAudioBecomingNoisy(true)
                .build()
        exo.addListener(listener)
        exo.setMediaItem(MediaItem.fromUri(request.url))
        exo.prepare()
        player = exo
        return true
    }

    private val listener =
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                publish()
                ticker?.cancel()
                if (isPlaying) ticker = scope.launch { while (true) publishAfterDelay() }
            }

            override fun onPlaybackStateChanged(playbackState: Int) = publish()

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) = publish()

            override fun onPlayerError(error: PlaybackException) {
                mutableState.value = mutableState.value.copy(playing = false, failed = true)
            }
        }

    private suspend fun publishAfterDelay() {
        delay(TICK_MS)
        publish()
    }

    private fun publish() {
        val exo = player ?: return
        mutableState.value = mutableState.value.copy(playing = exo.isPlaying, positionMs = exo.currentPosition)
    }

    override fun play() {
        val exo = player ?: return
        if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0)
        mutableState.value = mutableState.value.copy(failed = false)
        exo.play()
    }

    override fun pause() {
        player?.pause()
    }

    override fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
        publish()
    }

    override fun release() {
        scope.cancel()
        player?.release()
        player = null
    }

    private companion object {
        const val TICK_MS = 250L
    }
}
