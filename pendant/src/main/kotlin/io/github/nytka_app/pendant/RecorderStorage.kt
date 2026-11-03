package io.github.nytka_app.pendant

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The unit of [StoredBlock.stamp]: Omi stamps a record in whole seconds, other recorders may use milliseconds. */
enum class StampUnit(
    val perSecond: Long,
) {
    Seconds(1),
    Milliseconds(1_000),
}

/**
 * What a recorder holds, in positions: a block sits at one position, [first] is the oldest it still has and [end]
 * the next one it will write, so `end - first` blocks are unread. [dropped] counts blocks it freed or overwrote
 * before anyone read them, and [blockBytes] is the size of one block, for the transfer rate.
 */
data class StoredRange(
    val first: Long,
    val end: Long,
    val capacity: Long,
    val dropped: Long,
    val blockBytes: Int,
)

/**
 * One block of stored audio: its [position], the recorder's clock in [stampUnit]s when it was written, and the
 * frames in the recorder's codec.
 */
class StoredBlock(
    val position: Long,
    val stamp: Long,
    val stampUnit: StampUnit,
    val frames: List<ByteArray>,
) {
    val stampS: Long get() = stamp / stampUnit.perSecond
}

/** What one [RecorderStorage.read] window yields; [Done] also reports a read the recorder refused. */
sealed interface StoredEvent {
    class Begin(
        val start: Long,
        val count: Long,
    ) : StoredEvent

    class Blocks(
        val blocks: List<StoredBlock>,
    ) : StoredEvent

    /** The window ended with [status]; [next] is the position after the last block it delivered. */
    class Done(
        val status: Int,
        val next: Long,
    ) : StoredEvent
}

/** The outcomes of a storage operation: the recorder's own (0 and up) and the ones this app produces (negative). */
object StorageStatus {
    const val OK = 0

    /** The recorder's storage is still starting; ask again soon. */
    const val NOT_READY = 9

    /** [RecorderStorage.advance] named a position the recorder is already past, or one it does not have. */
    const val OUT_OF_RANGE = 10

    /** Nothing answered in time. */
    const val TIMEOUT = -1

    /** The link dropped while waiting. */
    const val LINK_LOST = -2

    /** Nothing was sent: storage is unsupported or could not be opened. */
    const val UNAVAILABLE = -3
}

/**
 * A recorder's stored audio, read and freed by position. A driver writes nothing to the recorder's storage unless
 * [support] is [StorageSupport.Supported] and never sends a command that erases audio the server has not accepted.
 * One operation at a time; do not call another from inside a collector of [read].
 */
interface RecorderStorage {
    val support: StateFlow<StorageSupport>

    /** Recorder clock minus phone clock in seconds, taken when the driver set the recorder's clock; null if unread. */
    val clockSkew: StateFlow<Long?>

    /** The [StorageStatus] of the last [status] that returned null, null after one that worked. */
    val lastStatus: StateFlow<Int?>

    /**
     * The range the recorder holds; null when it failed, with [lastStatus] telling why (the caller retries
     * [StorageStatus.NOT_READY]). A recorder whose blocks Nytka cannot read turns [support] to Unsupported instead.
     */
    suspend fun status(): StoredRange?

    /**
     * Reads up to [count] blocks from [from]. Every window ends with one [StoredEvent.Done]: the recorder's own, or
     * one built by the driver for a refused read, a silence ([StorageStatus.TIMEOUT]) or a lost link
     * ([StorageStatus.LINK_LOST]). A block that cannot be parsed ends the window there. Cancelling the collector
     * frees nothing.
     */
    fun read(
        from: Long,
        count: Int,
    ): Flow<StoredEvent>

    /** Frees the positions below [through]; returns a [StorageStatus] or the recorder's own status. */
    suspend fun advance(through: Long): Int
}
