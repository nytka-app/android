package io.github.nytka_app.core.queue

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContextOutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: ContextRow)

    @Query("select * from context_outbox order by startMs, id limit :limit")
    suspend fun oldest(limit: Int): List<ContextRow>

    @Query("delete from context_outbox where id in (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("delete from context_outbox where endMs < :cutoffMs")
    suspend fun deleteEndedBefore(cutoffMs: Long)

    @Query("select count(*) from context_outbox")
    fun count(): Flow<Int>
}
