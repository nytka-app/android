package io.github.nytka_app.core.queue

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A bookmark not yet on the server. [id] is made here, so a retry after a lost answer is a no-op there. */
@Entity(tableName = "bookmark_outbox")
data class BookmarkRow(
    @PrimaryKey val id: String,
    val atMs: Long,
    val source: String,
)
