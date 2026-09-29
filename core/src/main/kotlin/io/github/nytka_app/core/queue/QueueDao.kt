package io.github.nytka_app.core.queue

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class QueueDao {
    @Query("select max(id) from frames")
    abstract suspend fun lastFrameId(): Long?

    @Query("select * from frames where id <= :throughId order by id limit :limit")
    abstract suspend fun oldestFrames(
        throughId: Long,
        limit: Int,
    ): List<QueuedFrame>

    @Query("delete from frames where id <= :lastId")
    abstract suspend fun deleteFramesThrough(lastId: Long)

    @Insert
    abstract suspend fun insertChunk(chunk: SealedChunk): Long

    /** Sealing a chunk and deleting its frames happen together or not at all. */
    @Transaction
    open suspend fun replaceFramesWithChunk(
        lastFrameId: Long,
        chunk: SealedChunk,
    ) {
        insertChunk(chunk)
        deleteFramesThrough(lastFrameId)
    }

    /** Live chunks before stored ones, each oldest first, so a backlog never delays new speech. */
    @Query("select * from chunks order by stored, id limit 1")
    abstract suspend fun oldestChunk(): SealedChunk?

    /** The chunk of one source sealed first: live is all the cap may delete, stored it may only park. */
    @Query("select * from chunks where stored = :stored order by id limit 1")
    abstract suspend fun firstChunk(stored: Boolean): SealedChunk?

    @Query("select * from chunks where id = :id")
    abstract suspend fun chunk(id: Long): SealedChunk?

    @Query("delete from chunks where id = :id")
    abstract suspend fun deleteChunk(id: Long)

    @Query("select count(*) from chunks")
    abstract suspend fun chunkCount(): Int

    @Query("select coalesce(sum(length(body)), 0) from chunks")
    abstract suspend fun chunkBytes(): Long

    @Query("select count(*) from frames")
    abstract suspend fun frameCount(): Int

    /** Payload plus the six-byte record header each frame will take in its chunk. */
    @Query("select coalesce(sum(length(payload)) + 6 * count(*), 0) from frames")
    abstract suspend fun frameBytes(): Long

    @Insert
    abstract suspend fun insertFrames(frames: List<QueuedFrame>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putPosition(position: RingPositionRow)

    @Query("select * from ring_position where id = ${RingPositionRow.ROW_ID}")
    abstract suspend fun position(): RingPositionRow?

    @Query("delete from ring_position")
    abstract suspend fun deletePosition()

    @Query("update ring_position set advanced = :seq")
    abstract suspend fun setAdvanced(seq: Long)

    /** The frames and the position that covers them are stored together or not at all. */
    @Transaction
    open suspend fun commitStored(
        frames: List<QueuedFrame>,
        position: RingPositionRow,
    ) {
        insertFrames(frames)
        putPosition(position)
    }

    @Insert
    abstract suspend fun insertParked(chunk: ParkedChunk)

    /** The chunk moves to the parked table in one step, so its ring range is never unaccounted for. */
    @Transaction
    open suspend fun park(
        chunkId: Long,
        code: Int,
        reason: String,
        nowMs: Long,
    ) {
        val chunk = chunk(chunkId) ?: return
        insertParked(
            ParkedChunk(
                session = chunk.session,
                firstSeq = chunk.firstSeq,
                frameCount = chunk.frameCount,
                createdAtMs = chunk.createdAtMs,
                body = chunk.body,
                ringFirst = chunk.ringFirst,
                ringLast = chunk.ringLast,
                epoch = chunk.epoch,
                code = code,
                reason = reason,
                parkedAtMs = nowMs,
            ),
        )
        deleteChunk(chunkId)
    }

    @Query("select count(*) from parked_chunks")
    abstract suspend fun parkedCount(): Int

    /**
     * The lowest ring sequence still held by a stored frame, a stored chunk or a parked chunk of the current
     * epoch, or the position's `committedNext` when the queue holds none. Empty while there is no position.
     */
    @Query(
        "select min(v) from (" +
            "select min(f.ringSeq) as v from frames f, ring_position p where f.stored = 1 and f.epoch = p.epoch " +
            "union all select min(c.ringFirst) from chunks c, ring_position p " +
            "where c.stored = 1 and c.epoch = p.epoch " +
            "union all select min(k.ringFirst) from parked_chunks k, ring_position p where k.epoch = p.epoch " +
            "union all select committedNext from ring_position)",
    )
    abstract fun ackedThrough(): Flow<Long?>

    @Insert
    abstract suspend fun insertMute(change: MuteLogRow)

    @Query("select * from mute_log order by atMs, id")
    abstract suspend fun muteChanges(): List<MuteLogRow>
}
