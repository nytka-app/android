package io.github.nytka_app.core.queue

import io.github.nytka_app.core.chunks.Chunk
import io.github.nytka_app.core.chunks.ChunkFormat
import io.github.nytka_app.core.chunks.ChunkFrame
import io.github.nytka_app.core.chunks.ChunkWriter
import io.github.nytka_app.core.ring.MuteChange
import io.github.nytka_app.core.ring.RingPosition
import io.github.nytka_app.core.ring.TimedFrame
import io.github.nytka_app.core.upload.ChunkSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class QueueUsage(
    val bytes: Long = 0,
    val chunks: Int = 0,
    val frames: Int = 0,
    val capBytes: Long = FrameQueue.CAP_BYTES,
    val droppedChunks: Long = 0,
    val parkedChunks: Int = 0,
) {
    val fraction: Double get() = bytes.toDouble() / capBytes
}

/**
 * The durable queue between the pendant and the server. Frames are stored as they arrive;
 * [seal] turns them into wire-format chunks; the uploader takes live chunks before stored ones, each oldest
 * first. Frames read from the pendant's ring arrive through [commit], with the sync position.
 */
class FrameQueue(
    database: QueueDatabase,
    private val capBytes: Long = CAP_BYTES,
    private val now: () -> Long = System::currentTimeMillis,
) : ChunkSource,
    FrameSink {
    private val dao = database.queue()
    private val sealing = Mutex()
    private val state = MutableStateFlow(QueueUsage(capBytes = capBytes))
    private var droppedChunks = 0L

    override val usage: StateFlow<QueueUsage> = state.asStateFlow()

    override val ackedThrough: Flow<Long> = dao.ackedThrough().filterNotNull().distinctUntilChanged()

    override suspend fun add(
        session: UUID,
        seq: Long,
        capturedAtMs: Long,
        payload: ByteArray,
    ) {
        dao.insertFrames(
            listOf(
                QueuedFrame(session = session.toString(), seq = seq, capturedAtMs = capturedAtMs, payload = payload),
            ),
        )
    }

    override suspend fun commit(
        frames: List<TimedFrame>,
        position: RingPosition,
    ) {
        dao.commitStored(
            frames.map {
                QueuedFrame(
                    session = it.session.toString(),
                    seq = it.seq,
                    capturedAtMs = it.capturedAtMs,
                    payload = it.payload,
                    stored = true,
                    ringSeq = it.ringSeq,
                    epoch = position.epoch,
                )
            },
            RingPositionRow.of(position),
        )
    }

    override suspend fun position(): RingPosition? = dao.position()?.toPosition()

    override suspend fun markAdvanced(seq: Long) = dao.setAdvanced(seq)

    override suspend fun clearPosition() = dao.deletePosition()

    override suspend fun recordMute(
        atMs: Long,
        muted: Boolean,
    ) = dao.insertMute(MuteLogRow(atMs = atMs, muted = muted))

    override suspend fun muteChanges(): List<MuteChange> = dao.muteChanges().map { MuteChange(it.atMs, it.muted) }

    /** Seals every frame queued before the call; returns how many chunks it made. */
    override suspend fun seal(): Int =
        sealing.withLock {
            val throughId = dao.lastFrameId() ?: return@withLock 0.also { refreshUsage() }
            var sealed = 0
            while (true) {
                val frames = dao.oldestFrames(throughId, ChunkFormat.MAX_FRAMES)
                if (frames.isEmpty()) break
                val run = takeRun(frames)
                dao.replaceFramesWithChunk(run.last().id, toChunk(run))
                sealed++
            }
            enforceCap()
            refreshUsage()
            sealed
        }

    override suspend fun oldest(): SealedChunk? = dao.oldestChunk()

    override suspend fun remove(chunkId: Long) {
        dao.deleteChunk(chunkId)
        refreshUsage()
    }

    override suspend fun park(
        chunkId: Long,
        code: Int,
        reason: String,
    ) {
        dao.park(chunkId, code, reason, now())
        refreshUsage()
    }

    suspend fun refreshUsage() {
        state.value =
            QueueUsage(
                bytes = dao.chunkBytes() + dao.frameBytes(),
                chunks = dao.chunkCount(),
                frames = dao.frameCount(),
                capBytes = capBytes,
                droppedChunks = droppedChunks,
                parkedChunks = dao.parkedCount(),
            )
    }

    /** The longest prefix of [frames] that forms one valid chunk: one source, one session, one ring epoch. */
    private fun takeRun(frames: List<QueuedFrame>): List<QueuedFrame> {
        val first = frames.first()
        var size = ChunkFormat.HEADER_SIZE + ChunkFormat.recordSize(first.payload.size)
        var end = 1
        while (end < frames.size) {
            val previous = frames[end - 1]
            val next = frames[end]
            val offset = next.capturedAtMs - first.capturedAtMs
            val fits =
                next.session == first.session &&
                    next.stored == first.stored &&
                    next.epoch == first.epoch &&
                    next.seq == previous.seq + 1 &&
                    offset in 0..U32_MAX &&
                    size + ChunkFormat.recordSize(next.payload.size) <= ChunkFormat.MAX_BYTES
            if (!fits) break
            size += ChunkFormat.recordSize(next.payload.size)
            end++
        }
        return frames.subList(0, end)
    }

    private fun toChunk(run: List<QueuedFrame>): SealedChunk {
        val first = run.first()
        val body =
            ChunkWriter.write(
                Chunk(
                    session = UUID.fromString(first.session),
                    firstSeq = first.seq,
                    baseTimeMs = first.capturedAtMs,
                    frames = run.map { ChunkFrame(it.seq, it.capturedAtMs, it.payload) },
                ),
            )
        val ringSeqs = run.mapNotNull { it.ringSeq }
        return SealedChunk(
            session = first.session,
            firstSeq = first.seq,
            frameCount = run.size,
            createdAtMs = now(),
            body = body,
            stored = first.stored,
            ringFirst = ringSeqs.minOrNull(),
            ringLast = ringSeqs.maxOrNull(),
            epoch = first.epoch,
        )
    }

    /** Drops the oldest chunks until the queue fits its cap (open question 2). */
    private suspend fun enforceCap() {
        while (dao.chunkBytes() + dao.frameBytes() > capBytes) {
            val oldest = dao.firstChunk() ?: return
            dao.deleteChunk(oldest.id)
            droppedChunks++
        }
    }

    companion object {
        const val CAP_BYTES = 1L shl 30
        const val ALERT_FRACTION = 0.8
        private const val U32_MAX = 0xFFFF_FFFFL
    }
}
