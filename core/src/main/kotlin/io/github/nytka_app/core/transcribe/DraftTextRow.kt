package io.github.nytka_app.core.transcribe

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * What the phone heard in one stretch of speech: [startMs] to [endMs] on the phone's clock, the capture session and
 * its first frame, and the [model] that made [text]. Draft text stays on the phone; it never enters a log.
 */
@Entity(tableName = "draft_texts", indices = [Index(value = ["startMs"])])
data class DraftTextRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val session: String,
    val firstSeq: Long,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val model: String,
    val createdAtMs: Long,
) {
    override fun toString() =
        "DraftTextRow(id=$id, session=$session, firstSeq=$firstSeq, startMs=$startMs, endMs=$endMs, " +
            "${text.length} chars, model=$model)"
}
