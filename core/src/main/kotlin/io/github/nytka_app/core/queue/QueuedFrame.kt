package io.github.nytka_app.core.queue

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A frame waiting to be sealed. Plain class: a data class would compare the payload by reference. */
@Entity(tableName = "frames", indices = [Index(value = ["session", "seq"], unique = true)])
class QueuedFrame(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val session: String,
    val seq: Long,
    val capturedAtMs: Long,
    val payload: ByteArray,
)
