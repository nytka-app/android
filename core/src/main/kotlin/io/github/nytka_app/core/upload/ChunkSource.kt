package io.github.nytka_app.core.upload

import io.github.nytka_app.core.queue.QueueUsage
import io.github.nytka_app.core.queue.SealedChunk
import kotlinx.coroutines.flow.StateFlow

/** Where the uploader takes chunks from. [FrameQueue][io.github.nytka_app.core.queue.FrameQueue] in the app. */
interface ChunkSource {
    val usage: StateFlow<QueueUsage>

    suspend fun oldest(): SealedChunk?

    suspend fun remove(chunkId: Long)
}
