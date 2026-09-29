package io.github.nytka_app.core.diagnostics

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: DiagnosticRow)

    @Query("select * from diagnostic_samples where uploaded = 0 order by at_ms, id limit :limit")
    suspend fun oldestNotUploaded(limit: Int): List<DiagnosticRow>

    @Query("update diagnostic_samples set uploaded = 1 where id in (:ids)")
    suspend fun markUploaded(ids: List<String>)

    @Query(
        "select * from diagnostic_samples where kind = 'sample' and at_ms >= :sinceMs " +
            "order by at_ms, id limit :limit offset :offset",
    )
    suspend fun since(
        sinceMs: Long,
        limit: Int,
        offset: Int,
    ): List<DiagnosticRow>

    @Query("delete from diagnostic_samples where at_ms < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long)

    @Query(
        "delete from diagnostic_samples where kind = 'log' and id in (" +
            "select id from diagnostic_samples where kind = 'log' order by at_ms desc, id desc limit -1 offset :keep)",
    )
    suspend fun trimLogs(keep: Int)

    @Query("select count(*) from diagnostic_samples where kind = 'sample'")
    fun count(): Flow<Int>
}
