package io.github.nytka_app.core.queue

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A chunk in the wire format, ready to upload. A [stored] chunk holds frames from the pendant's ring and records
 * their range ([ringFirst] to [ringLast], in ring epoch [epoch]): the pendant may free that range only once the
 * server has the chunk.
 */
@Entity(tableName = "chunks")
class SealedChunk(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val session: String,
    val firstSeq: Long,
    val frameCount: Int,
    val createdAtMs: Long,
    val body: ByteArray,
    @ColumnInfo(defaultValue = "0") val stored: Boolean = false,
    val ringFirst: Long? = null,
    val ringLast: Long? = null,
    @ColumnInfo(defaultValue = "0") val epoch: Long = 0,
)
