package io.github.nytka_app.capture

import io.github.nytka_app.core.chunks.ChunkReader
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class FrameRecorderTest {
    private val session = UUID.randomUUID()

    private fun frame(
        seq: Long,
        session: UUID = this.session,
    ) = CapturedFrame(session, seq, 1_000 + seq * 20, byteArrayOf(seq.toByte()))

    @Test
    fun `records for the given time`() =
        runTest {
            val frames =
                flow {
                    var seq = 0L
                    while (true) {
                        emit(frame(seq++))
                        delay(20)
                    }
                }

            val recorded = FrameRecorder.record(frames, durationMs = 1_000)

            assertEquals(50, recorded.size)
        }

    @Test
    fun `writes chunks that split at 1500 frames and at a new session`() {
        val other = UUID.randomUUID()
        val frames = (0L until 1_600L).map { frame(it) } + (0L until 3L).map { frame(it, other) }

        val chunks = ChunkReader.readAll(FixtureWriter.write(frames))

        assertEquals(listOf(1500, 100, 3), chunks.map { it.frames.size })
        assertEquals(listOf(session, session, other), chunks.map { it.session })
    }
}
