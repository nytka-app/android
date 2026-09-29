package io.github.nytka_app.core.queue

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class QueueDao {
    @Insert
    abstract suspend fun insertFrame(frame: QueuedFrame)

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

    @Query("select * from chunks order by id limit 1")
    abstract suspend fun oldestChunk(): SealedChunk?

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
}
