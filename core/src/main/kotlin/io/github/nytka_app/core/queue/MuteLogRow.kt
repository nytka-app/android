package io.github.nytka_app.core.queue

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One change of the mute setting, kept so stored audio recorded during a mute can be dropped. */
@Entity(tableName = "mute_log")
data class MuteLogRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMs: Long,
    val muted: Boolean,
)
