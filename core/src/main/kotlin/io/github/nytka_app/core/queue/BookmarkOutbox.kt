package io.github.nytka_app.core.queue

import kotlinx.coroutines.flow.Flow

/** Where a bookmark goes the moment the pendant's tap is kept. */
interface BookmarkSink {
    suspend fun add(
        id: String,
        atMs: Long,
        source: String,
    )
}

/** The bookmarks waiting for the server, oldest first. */
interface BookmarkSource {
    /** How many wait; changes as bookmarks are added and removed. */
    val count: Flow<Int>

    suspend fun oldest(): BookmarkRow?

    suspend fun remove(id: String)
}

/** The Room table behind [BookmarkSink] and [BookmarkSource]. A bookmark leaves it only when the server has it. */
class BookmarkOutbox(
    private val dao: BookmarksDao,
) : BookmarkSink,
    BookmarkSource {
    override val count: Flow<Int> = dao.count()

    override suspend fun add(
        id: String,
        atMs: Long,
        source: String,
    ) = dao.insert(BookmarkRow(id, atMs, source))

    override suspend fun oldest(): BookmarkRow? = dao.oldest()

    override suspend fun remove(id: String) = dao.delete(id)

    companion object {
        const val SOURCE_PENDANT = "pendant"
        const val SOURCE_APP = "app"
    }
}
