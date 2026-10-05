package io.github.nytka_app.core.queue

import kotlinx.coroutines.flow.Flow

/** Where a closed range goes the moment the tracker closes it. */
fun interface ContextSink {
    suspend fun add(row: ContextRow)
}

/** The ranges waiting for the server, oldest first. */
interface ContextSource {
    /** How many wait; changes as ranges are added and removed. */
    val count: Flow<Int>

    suspend fun oldest(limit: Int): List<ContextRow>

    suspend fun remove(ids: List<String>)

    /** Ranges that ended before [cutoffMs] and never went out are no use to the server any more. */
    suspend fun removeEndedBefore(cutoffMs: Long)
}

/** The Room table behind [ContextSink] and [ContextSource]. */
class ContextOutbox(
    private val dao: ContextOutboxDao,
) : ContextSink,
    ContextSource {
    override val count: Flow<Int> = dao.count()

    override suspend fun add(row: ContextRow) = dao.insert(row)

    override suspend fun oldest(limit: Int): List<ContextRow> = dao.oldest(limit)

    override suspend fun remove(ids: List<String>) = dao.delete(ids)

    override suspend fun removeEndedBefore(cutoffMs: Long) = dao.deleteEndedBefore(cutoffMs)
}
