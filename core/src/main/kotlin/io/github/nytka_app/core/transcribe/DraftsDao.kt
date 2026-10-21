package io.github.nytka_app.core.transcribe

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftsDao {
    @Query("select * from draft_audio order by id limit :limit")
    suspend fun oldestAudio(limit: Int): List<DraftAudioRow>

    @Query("select count(*) from draft_audio")
    fun audioCount(): Flow<Int>

    @Query("select coalesce(sum(frameCount), 0) from draft_audio")
    fun audioFrames(): Flow<Long>

    @Query("delete from draft_audio")
    suspend fun deleteAllAudio()

    @Insert
    suspend fun insertTexts(rows: List<DraftTextRow>)

    @Query("delete from draft_audio where id in (:ids)")
    suspend fun deleteAudio(ids: List<Long>)

    /** Saves what [audioIds] said and drops those copies in one go, so a crash in between repeats nothing. */
    @Transaction
    suspend fun finish(
        texts: List<DraftTextRow>,
        audioIds: List<Long>,
    ) {
        if (texts.isNotEmpty()) insertTexts(texts)
        deleteAudio(audioIds)
    }

    @Query("select * from draft_texts order by startMs desc limit :limit")
    fun newestTexts(limit: Int): Flow<List<DraftTextRow>>

    @Query("delete from draft_texts where startMs < :cutoffMs")
    suspend fun deleteTextsBefore(cutoffMs: Long)

    @Query("delete from draft_texts")
    suspend fun deleteAllTexts()
}
