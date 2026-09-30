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

/** The events the consumer firmware sends (`src/lib/core/button.c`); 3 and 4 are defined but never sent. */
enum class ButtonEvent {
    SingleTap,
    DoubleTap,
    Release,
    ;

    companion object {
        fun fromCode(code: Int): ButtonEvent? =
            when (code) {
                1 -> SingleTap
                2 -> DoubleTap
                5 -> Release
                else -> null
            }
    }
}

/** The values the haptic characteristic takes (`src/haptic.c`). */
enum class Haptic(
    val code: Byte,
    val durationMs: Long,
) {
    Short(1, 100),
    Medium(2, 300),
    Long(3, 500),
}

data class LinkStats(
    val notifications: Long = 0,
    val lostNotifications: Long = 0,
    val droppedFrames: Long = 0,
    val frames: Long = 0,
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

/** Whether the pendant's offline storage can be used; only [Supported] lets [PendantStorage] write anything. */
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
 * The pendant's ring of stored audio (firmware 3.0.20 and later). Nothing here writes to the storage service
 * unless [support] is [StorageSupport.Supported]. One operation at a time; do not call another from inside a
 * collector of [read].
 */
interface PendantStorage {
    val support: StateFlow<StorageSupport>

    /** Pendant clock minus phone clock in seconds, read by `setUp` before it writes the phone's; null if unread. */
    val clockSkew: StateFlow<Long?>

    /** The status of the last [info] that returned null (a [RingStatus] value), null after one that worked. */
    val lastStatus: StateFlow<Int?>

    /** Writes the phone's time to the pendant clock. */
    suspend fun setTime(epochS: Long): Boolean

    /** Sends stop, then INFO; null when it failed, with [lastStatus] telling why (the caller retries status 9). */
    suspend fun info(): RingInfo?

    /**
     * Reads up to [count] packets from [fromSeq]. Every window ends with one [RingEvent.Done]: the firmware's, or
     * one built here for a refused READ (its ACK status), a silence ([RingStatus.TIMEOUT]) or a lost link
     * ([RingStatus.LINK_LOST]). Cancelling the collector sends stop.
     */
    fun read(
        fromSeq: Long,
        count: Int,
    ): Flow<RingEvent>

    /** Frees the ring below [seq]; returns the ACK status, or a negative [RingStatus] value if nothing answered. */
    suspend fun advance(seq: Long): Int
}

/**
 * An Omi pendant, or something that behaves like one. A lost link loses every subscription, but
 * not the caller's intent: after a reconnect the pendant subscribes to audio again by itself if the
 * last [setAudio] said true. [disconnect] clears the intent.
 */
interface Pendant {
    val connection: StateFlow<PendantConnection>

    /** Percent, or null while unknown. */
    val battery: StateFlow<Int?>

    val stats: StateFlow<LinkStats>

    /** Reassembled frames while audio is on. */
    val frames: Flow<AudioFrame>

    val buttons: Flow<ButtonEvent>

    val storage: PendantStorage

    /** LED brightness and microphone gain; nothing is offered until the pendant reports the feature. */
    val settings: PendantSettings

    /** Connects and keeps reconnecting until [disconnect]. */
    fun connect(address: String)

    fun disconnect()

    suspend fun setAudio(enabled: Boolean)

    suspend fun buzz(haptic: Haptic)
}
