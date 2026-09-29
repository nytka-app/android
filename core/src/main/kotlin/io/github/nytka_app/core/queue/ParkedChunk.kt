package io.github.nytka_app.core.queue

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A stored chunk the server refused for good (400, 409 or 413). It leaves the upload queue but stays here, and
 * its ring range still holds back `ADVANCE`: it may be the only copy of that audio.
 */
@Entity(tableName = "parked_chunks")
class ParkedChunk(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val session: String,
    val firstSeq: Long,
    val frameCount: Int,
    val createdAtMs: Long,
    val body: ByteArray,
    val ringFirst: Long?,
    val ringLast: Long?,
    val epoch: Long,
    val code: Int,
    val reason: String,
    val parkedAtMs: Long,
)
