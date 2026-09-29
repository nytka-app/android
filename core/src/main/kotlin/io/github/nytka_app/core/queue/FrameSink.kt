package io.github.nytka_app.core.queue

import io.github.nytka_app.core.ring.MuteChange
import io.github.nytka_app.core.ring.RingPosition
import io.github.nytka_app.core.ring.StoredSink
import io.github.nytka_app.core.ring.TimedFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.util.UUID

/**
 * Where captured frames go. [FrameQueue] in the app. The [StoredSink] members have empty bodies, so a sink that
 * only takes live frames stays as it was.
 */
interface FrameSink : StoredSink {
    suspend fun add(
        session: UUID,
        seq: Long,
        capturedAtMs: Long,
        payload: ByteArray,
    )

    /**
     * Turns queued frames into chunks. With [includePartialStored] false, a stored run that could still grow
     * (the queue's newest, under the chunk limits) stays as frames; the end of a sync window seals it.
     */
    suspend fun seal(includePartialStored: Boolean = true): Int

    override val ackedThrough: Flow<Long> get() = emptyFlow()

    override suspend fun position(): RingPosition? = null

    override suspend fun commit(
        frames: List<TimedFrame>,
        position: RingPosition,
    ) = Unit

    override suspend fun markAdvanced(seq: Long) = Unit

    override suspend fun clearPosition() = Unit

    override suspend fun recordMute(
        atMs: Long,
        muted: Boolean,
    ) = Unit

    override suspend fun muteChanges(): List<MuteChange> = emptyList()
}
