package io.github.nytka_app.pendant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Replays Opus payloads in a loop, one frame every 20 ms, stamped with the current time. Drives
 * the capture path in tests and in developer mode ("fake pendant").
 */
class FakePendant(
    private val payloads: List<ByteArray>,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) : Pendant {
    private val mutableConnection = MutableStateFlow<PendantConnection>(PendantConnection.Disconnected)
    private val mutableBattery = MutableStateFlow<Int?>(null)
    private val mutableStats = MutableStateFlow(LinkStats())
    private val mutableFrames = MutableSharedFlow<AudioFrame>(extraBufferCapacity = 64)
    private val mutableButtons = MutableSharedFlow<ButtonEvent>(extraBufferCapacity = 8)
    private val recordedHaptics = mutableListOf<Haptic>()
    private var refusal: String? = null
    private var streaming: Job? = null
    private var next = 0
    private var audioWanted = false

    override val connection: StateFlow<PendantConnection> = mutableConnection
    override val battery: StateFlow<Int?> = mutableBattery
    override val stats: StateFlow<LinkStats> = mutableStats
    override val frames: Flow<AudioFrame> = mutableFrames
    override val buttons: Flow<ButtonEvent> = mutableButtons

    val haptics: List<Haptic> get() = recordedHaptics.toList()

    var audioEnabled = false
        private set

    override fun connect(address: String) {
        mutableConnection.value = refusal?.let { PendantConnection.Refused(it) }
            ?: PendantConnection.Connected(PendantInfo(name = "Fake pendant", model = "Fake", firmware = "fake"))
        if (mutableBattery.value == null) mutableBattery.value = 82
        applyAudio() // like OmiPendant: the caller's audio intent survives a lost link
    }

    override fun disconnect() {
        audioWanted = false
        dropLink()
    }

    override suspend fun setAudio(enabled: Boolean) {
        audioWanted = enabled
        applyAudio()
    }

    private fun applyAudio() {
        audioEnabled = audioWanted && mutableConnection.value is PendantConnection.Connected
        streaming?.cancel()
        streaming = if (audioEnabled) scope.launch { stream() } else null
    }

    override suspend fun buzz(haptic: Haptic) {
        recordedHaptics += haptic
    }

    fun press(event: ButtonEvent) {
        mutableButtons.tryEmit(event)
    }

    /** The pendant walked out of range: the link and its subscriptions are gone, the audio intent is not. */
    fun dropLink() {
        streaming?.cancel()
        streaming = null
        audioEnabled = false
        mutableConnection.value = PendantConnection.Disconnected
    }

    fun refuse(reason: String) {
        refusal = reason
    }

    fun setBattery(percent: Int) {
        mutableBattery.value = percent
    }

    private suspend fun stream() {
        while (true) { // ends when setAudio or dropLink cancels the job
            val frame = AudioFrame(payloads[next++ % payloads.size], now())
            mutableFrames.emit(frame)
            mutableStats.value =
                mutableStats.value.let { it.copy(notifications = it.notifications + 1, frames = it.frames + 1) }
            delay(FRAME_MS)
        }
    }

    private companion object {
        const val FRAME_MS = 20L
    }
}
