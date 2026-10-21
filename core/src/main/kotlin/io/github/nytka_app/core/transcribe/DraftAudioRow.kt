package io.github.nytka_app.core.transcribe

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A copy of one sealed chunk, waiting for the phone to transcribe it. [DraftAudioTap]'s trigger writes it in the
 * transaction that inserts the chunk, so the upload queue never waits for it and never sees it; transcribing or
 * switching off deletes it. Plain class: a data class would compare the body by reference.
 */
@Entity(tableName = "draft_audio", indices = [Index(value = ["chunkId"], unique = true)])
class DraftAudioRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chunkId: Long,
    val session: String,
    val firstSeq: Long,
    val frameCount: Int,
    val body: ByteArray,
    val createdAtMs: Long,
)
