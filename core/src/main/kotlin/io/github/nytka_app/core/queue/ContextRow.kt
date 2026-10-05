package io.github.nytka_app.core.queue

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A phone-context range not yet on the server. [id] is made here, so a retry after a lost answer is a no-op there. */
@Entity(tableName = "context_outbox")
data class ContextRow(
    @PrimaryKey val id: String,
    val kind: String,
    val route: String,
    val startMs: Long,
    val endMs: Long,
)
