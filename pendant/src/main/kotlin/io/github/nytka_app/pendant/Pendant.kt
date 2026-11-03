package io.github.nytka_app.pendant

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface PendantConnection {
    data object Disconnected : PendantConnection

    data object Connecting : PendantConnection

    data class Connected(
        val info: PendantInfo,
    ) : PendantConnection

    /** Connected, but Nytka will not capture from it; [reason] is shown to the user as is. */
    data class Refused(
        val reason: String,
    ) : PendantConnection
}

data class PendantInfo(
    val name: String,
    val model: String? = null,
    val firmware: String? = null,
    val hardware: String? = null,
)

/** What a recorder's button can say. Each driver maps its own codes to these; Omi's are in [OmiDriver]. */
enum class ButtonEvent {
    SingleTap,
    DoubleTap,
    Release,
}

/** A buzz the phone may ask for; each driver maps it to its own command, or ignores it without a haptic motor. */
enum class Haptic(
    val durationMs: Long,
) {
    Short(100),
    Medium(300),
    Long(500),
}

data class LinkStats(
    val notifications: Long = 0,
    val lostNotifications: Long = 0,
    val droppedFrames: Long = 0,
    val frames: Long = 0,
    /** Signal strength in dBm, read every 10 s while connected; null while unknown or disconnected. */
    val rssi: Int? = null,
    /** The status of the last link loss Android reported, null before the first; 147 is a link that went silent. */
    val lastDisconnectStatus: Int? = null,
) {
    /** Share of audio notifications that never arrived; spec "done when" 2 wants it under 1%. */
    val lossFraction: Double
        get() =
            if (notifications + lostNotifications ==
                0L
            ) {
                0.0
            } else {
                lostNotifications.toDouble() / (notifications + lostNotifications)
            }
}

/** Whether the pendant's offline storage can be used; only [Supported] lets [RecorderStorage] write anything. */
sealed interface StorageSupport {
    /** Not known yet: before the firmware and the features are read, and after the link is lost. */
    data object Unknown : StorageSupport

    data object Supported : StorageSupport

    /** [reason] is shown to the user as is. */
    data class Unsupported(
        val reason: String,
    ) : StorageSupport
}

/**
 * A recorder: an Omi pendant, or something that behaves like one. A lost link loses every subscription, but
 * not the caller's intent: after a reconnect the pendant subscribes to audio again by itself if the
 * last [setAudio] said true. [disconnect] clears the intent.
 */
interface Pendant {
    val model: RecorderModel

    /** What this recorder can do; the app offers and uses nothing outside it. */
    val capabilities: Capabilities

    val connection: StateFlow<PendantConnection>

    /** Percent, or null while unknown. */
    val battery: StateFlow<Int?>

    val stats: StateFlow<LinkStats>

    /** Reassembled frames while audio is on. */
    val frames: Flow<AudioFrame>

    val buttons: Flow<ButtonEvent>

    val storage: RecorderStorage

    /** LED brightness and microphone gain; nothing is offered until the pendant reports the feature. */
    val settings: PendantSettings

    /** Connects and keeps reconnecting until [disconnect]. */
    fun connect(address: String)

    fun disconnect()

    suspend fun setAudio(enabled: Boolean)

    suspend fun buzz(haptic: Haptic)
}
