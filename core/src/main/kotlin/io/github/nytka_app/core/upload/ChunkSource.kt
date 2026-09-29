package io.github.nytka_app.core.upload

import io.github.nytka_app.core.queue.QueueUsage
import io.github.nytka_app.core.queue.SealedChunk
import kotlinx.coroutines.flow.StateFlow

/** Where the uploader takes chunks from. [FrameQueue][io.github.nytka_app.core.queue.FrameQueue] in the app. */
interface ChunkSource {
    val usage: StateFlow<QueueUsage>

    /** The next chunk to upload: live chunks before stored ones, each oldest first. */
    suspend fun oldest(): SealedChunk?

    suspend fun remove(chunkId: Long)

    /**
     * Takes a stored chunk out of the upload queue after the server refused it for good ([code] 400, 409 or 413).
     * The chunk is kept, and its ring range still holds back `ADVANCE`. Without a store of its own a source
     * has no better choice than to [remove] it.
     */
    suspend fun park(
        chunkId: Long,
        code: Int,
        reason: String,
    ) = remove(chunkId)
}
