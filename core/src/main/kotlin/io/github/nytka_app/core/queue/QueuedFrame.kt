package io.github.nytka_app.core.queue

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A frame waiting to be sealed. Plain class: a data class would compare the payload by reference. A [stored]
 * frame came from the pendant's ring: [ringSeq] is its record's sequence number and [epoch] the ring's. A phone
 * microphone frame is live, with [source] [CaptureSource.PHONE_MIC].
 */
@Entity(tableName = "frames", indices = [Index(value = ["session", "seq"], unique = true)])
class QueuedFrame(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val session: String,
    val seq: Long,
    val capturedAtMs: Long,
    val payload: ByteArray,
    @ColumnInfo(defaultValue = "0") val stored: Boolean = false,
    val ringSeq: Long? = null,
    @ColumnInfo(defaultValue = "0") val epoch: Long = 0,
    /** [CaptureSource.PENDANT] or [CaptureSource.PHONE_MIC]. */
    @ColumnInfo(defaultValue = CaptureSource.PENDANT) val source: String = CaptureSource.PENDANT,
)
