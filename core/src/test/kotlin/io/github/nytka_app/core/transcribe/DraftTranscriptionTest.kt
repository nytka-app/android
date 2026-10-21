package io.github.nytka_app.core.transcribe

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.nytka_app.core.queue.FrameQueue
import io.github.nytka_app.core.queue.QueueDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DraftTranscriptionTest {
    private val database =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), QueueDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val queue = FrameQueue(database)
    private val dao = database.drafts()
    private val session = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private var now = 10_000_000_000L
    private val drafts = DraftTranscription(dao, now = { now })

    @After
    fun close() = database.close()

    /** Frames by [pattern], `s` speech and `.` silence, sealed and copied as the queue does it. */
    private suspend fun record(
        pattern: String,
        from: Long = 0,
    ) {
        DraftAudioTap(database).sync(true)
        pattern.forEachIndexed { i, c ->
            val seq = from + i
            queue.add(session, seq, 1_000 + seq * 20, ByteArray(if (c == 's') 60 else 8) { seq.toByte() })
        }
        queue.seal()
    }

    private class FakeDecoder : OpusDecoder {
        val decoded = mutableListOf<Byte>()
        var resets = 0

        override fun decode(frame: ByteArray): FloatArray {
            decoded += frame[0]
            return FloatArray(320)
        }

        override fun reset() {
            resets++
        }

        override fun close() = Unit
    }

    private class FakeTranscriber(
        private val answer: (Int) -> String = { "words" },
    ) : OnDeviceTranscriber {
        val lengths = mutableListOf<Int>()

        override fun transcribe(samples: FloatArray): String {
            lengths += samples.size
            return answer(lengths.size)
        }

        override fun close() = Unit
    }

    @Test
    fun `speech becomes one draft with its times and the copy is gone`() =
        runTest {
            record(".".repeat(50) + "s".repeat(100) + ".".repeat(100))
            val transcriber = FakeTranscriber { " hello there " }
            val decoder = FakeDecoder()

            val outcome = drafts.run(transcriber, decoder, "parakeet") { true }

            val text = dao.newestTexts(10).first().single()
            assertEquals("hello there", text.text)
            assertEquals("parakeet", text.model)
            // Speech is frames 50 to 149, padded to 40 to 159; a frame's time is the end of its 20 ms.
            assertEquals(1_000 + 40 * 20L - 20, text.startMs)
            assertEquals(1_000 + 159 * 20L, text.endMs)
            assertEquals(40L, text.firstSeq)
            assertEquals(0, dao.audioCount().first())
            assertEquals(1, outcome.segments)
            assertEquals(250 * 20L, outcome.audioMs)
            assertEquals(120 * 20L, outcome.speechMs)
            assertEquals(listOf(120 * 320), transcriber.lengths)
        }

    @Test
    fun `silence is never decoded, apart from the frames before a segment`() =
        runTest {
            record(".".repeat(200) + "s".repeat(30) + ".".repeat(200))
            val decoder = FakeDecoder()

            drafts.run(FakeTranscriber(), decoder, "m") { true }

            // 4 frames to settle the decoder, then the 50-frame segment (190 to 239).
            assertEquals((186..239).map { it.toByte() }, decoder.decoded)
            assertEquals(1, decoder.resets)
        }

    @Test
    fun `nothing runs when it may not go on, and the copies stay`() =
        runTest {
            record("s".repeat(100))

            val outcome = drafts.run(FakeTranscriber(), FakeDecoder(), "m") { false }

            assertEquals(0, outcome.chunks)
            assertEquals(1, dao.audioCount().first())
        }

    @Test
    fun `a segment the engine fails on is skipped, the rest is kept`() =
        runTest {
            record("s".repeat(30) + ".".repeat(60) + "s".repeat(30))
            val transcriber = FakeTranscriber { if (it == 1) error("engine") else "second" }

            val outcome = drafts.run(transcriber, FakeDecoder(), "m") { true }

            assertEquals(1, outcome.failedSegments)
            assertEquals(listOf("second"), dao.newestTexts(10).first().map { it.text })
            assertEquals(0, dao.audioCount().first())
        }

    @Test
    fun `empty text saves no draft`() =
        runTest {
            record("s".repeat(100))

            drafts.run(FakeTranscriber { "  " }, FakeDecoder(), "m") { true }

            assertTrue(dao.newestTexts(10).first().isEmpty())
            assertEquals(0, dao.audioCount().first())
        }

    @Test
    fun `contiguous chunks of one session are one group, so speech across them is one segment`() =
        runTest {
            record(".".repeat(20) + "s".repeat(80))
            record("s".repeat(80) + ".".repeat(60), from = 100)
            val transcriber = FakeTranscriber()

            val outcome = drafts.run(transcriber, FakeDecoder(), "m") { true }

            assertEquals(2, outcome.chunks)
            assertEquals(1, transcriber.lengths.size)
        }

    @Test
    fun `prune drops drafts older than a week`() =
        runTest {
            dao.insertTexts(
                listOf(
                    DraftTextRow(
                        session = "s",
                        firstSeq = 0,
                        startMs = now - DraftTranscription.KEEP_TEXT_MS - 1,
                        endMs = 0,
                        text = "old",
                        model = "m",
                        createdAtMs = 0,
                    ),
                    DraftTextRow(
                        session = "s",
                        firstSeq = 0,
                        startMs = now - 1,
                        endMs = 0,
                        text = "new",
                        model = "m",
                        createdAtMs = 0,
                    ),
                ),
            )

            drafts.prune()

            assertEquals(listOf("new"), dao.newestTexts(10).first().map { it.text })
        }

    @Test
    fun `a draft's description never carries its text`() {
        val row =
            DraftTextRow(
                session = "s",
                firstSeq = 0,
                startMs = 0,
                endMs = 0,
                text = "secret words",
                model = "m",
                createdAtMs = 0,
            )

        assertTrue("secret" !in row.toString())
    }
}
