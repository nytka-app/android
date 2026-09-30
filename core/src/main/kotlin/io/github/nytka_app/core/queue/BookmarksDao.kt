package io.github.nytka_app.core.queue

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarksDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: BookmarkRow)

    @Query("select * from bookmark_outbox order by atMs, id limit 1")
    suspend fun oldest(): BookmarkRow?

    @Query("delete from bookmark_outbox where id = :id")
    suspend fun delete(id: String)

    @Query("select count(*) from bookmark_outbox")
    fun count(): Flow<Int>
}
