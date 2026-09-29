package io.github.nytka_app.capture

import io.github.nytka_app.core.queue.FrameSink
import io.github.nytka_app.core.ring.MuteChange
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class MuteLogRecorderTest {
    class FakeMuteSink(
        val changes: MutableList<MuteChange> = mutableListOf(),
    ) : FrameSink {
        override suspend fun add(
            session: UUID,
            seq: Long,
            capturedAtMs: Long,
            payload: ByteArray,
        ) = Unit

        override suspend fun seal(): Int = 0

        override suspend fun recordMute(
            atMs: Long,
            muted: Boolean,
        ) {
            changes += MuteChange(atMs, muted)
        }

        override suspend fun muteChanges(): List<MuteChange> = changes.toList()
    }

    @Test
    fun `logs a change with the time it was made and skips a repeat`() =
        runTest {
            val sink = FakeMuteSink()
            var clock = 100L
            val recorder = MuteLogRecorder(sink, { clock })

            recorder.record(false) // the default state: not a change
            recorder.record(true)
            clock = 200
            recorder.record(true)
            clock = 300
            recorder.record(false)

            assertEquals(listOf(MuteChange(100, true), MuteChange(300, false)), sink.changes)
        }

    @Test
    fun `a start that finds the app muted with an empty log opens the mute now`() =
        runTest {
            val sink = FakeMuteSink()

            MuteLogRecorder(sink, { 42 }).record(true)

            assertEquals(listOf(MuteChange(42, true)), sink.changes)
        }

    @Test
    fun `remembers the log across recorders, so a restart does not log the same mute twice`() =
        runTest {
            val sink = FakeMuteSink(mutableListOf(MuteChange(10, true)))

            MuteLogRecorder(sink, { 99 }).record(true)

            assertEquals(listOf(MuteChange(10, true)), sink.changes)
            assertTrue(sink.changes.none { it.atMs == 99L })
        }
}
