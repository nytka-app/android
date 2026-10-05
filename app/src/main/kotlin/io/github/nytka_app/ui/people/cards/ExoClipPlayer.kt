package io.github.nytka_app.ui.people.cards

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.github.nytka_app.core.api.CardsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * ExoPlayer over the app's OkHttp client with the bearer header, for the clip of a card (`GET /people/cards/...`);
 * no cache, so the clip is never written to disk. The ExoPlayer setup repeats `ExoAudioPlayer.prepare` on purpose:
 * that class is bound to conversations and stays unchanged. Used on the main thread only. Nothing about the clip
 * or the token is logged.
 */
@OptIn(UnstableApi::class)
class ExoClipPlayer(
    private val context: Context,
    private val client: OkHttpClient,
    private val cards: CardsClient,
) : ClipPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(ClipState())
    override val state: StateFlow<ClipState> = mutableState.asStateFlow()
    private var player: ExoPlayer? = null
    private var starting: Job? = null

    override fun play(
        kind: String,
        id: String,
    ) {
        closePlayer()
        val key = clipKey(kind, id)
        mutableState.value = ClipState(key)
        starting =
            scope.launch {
                val request = cards.clipRequest(kind, id)
                if (request == null) {
                    mutableState.value = ClipState(key, failure = ClipFailure.Other)
                    return@launch
                }
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
                exo.addListener(listenerFor(key))
                exo.setMediaItem(MediaItem.fromUri(request.url))
                exo.prepare()
                exo.play()
                player = exo
            }
    }

    private fun listenerFor(key: String) =
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (mutableState.value.key == key && mutableState.value.failure == null) {
                    mutableState.value = ClipState(key, playing = isPlaying)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && mutableState.value.key == key) stop()
            }

            override fun onPlayerError(error: PlaybackException) {
                val gone = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode == 404
                mutableState.value = ClipState(key, failure = if (gone) ClipFailure.Gone else ClipFailure.Other)
            }
        }

    override fun stop() {
        closePlayer()
        mutableState.value = ClipState()
    }

    private fun closePlayer() {
        starting?.cancel()
        starting = null
        player?.release()
        player = null
    }

    override fun release() {
        closePlayer()
        scope.cancel()
    }
}
