package io.github.nytka_app.core.queue

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A chunk in the wire format, ready to upload. */
@Entity(tableName = "chunks")
class SealedChunk(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val session: String,
    val firstSeq: Long,
    val frameCount: Int,
    val createdAtMs: Long,
    val body: ByteArray,
)
