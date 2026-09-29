package io.github.nytka_app.core.queue

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.nytka_app.core.chunks.ChunkReader
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FrameQueueTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databases = mutableListOf<QueueDatabase>()
    private val session = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private fun inMemory() =
        Room
            .inMemoryDatabaseBuilder(context, QueueDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also(databases::add)

    @After
    fun close() = databases.forEach { it.close() }

    private suspend fun FrameQueue.addFrames(
        count: Int,
        from: Long = 0,
        session: UUID = this@FrameQueueTest.session,
        startMs: Long = 0,
    ) = repeat(count) { add(session, from + it, startMs + (from + it) * 20, byteArrayOf(it.toByte(), 1, 2)) }

    @Test
    fun `seals queued frames into a chunk the server accepts`() =
        runTest {
            val queue = FrameQueue(inMemory())
            queue.addFrames(3)

            assertEquals(1, queue.seal())

            val chunk = ChunkReader.read(queue.oldest()!!.body)
            assertEquals(session, chunk.session)
            assertEquals(listOf(0L, 1L, 2L), chunk.frames.map { it.seq })
            assertEquals(listOf(0L, 20L, 40L), chunk.frames.map { it.capturedAtMs })
            assertEquals(0, queue.usage.value.frames)
        }

    @Test
    fun `splits at 1500 frames and at a new session`() =
        runTest {
            val queue = FrameQueue(inMemory())
            queue.addFrames(1600)
            queue.addFrames(5, session = UUID.randomUUID())

            assertEquals(3, queue.seal())
            val sizes = drain(queue).map { it.frameCount }
            assertEquals(listOf(1500, 100, 5), sizes)
        }

    @Test
    fun `splits where the clock stepped back`() =
        runTest {
            val queue = FrameQueue(inMemory())
            queue.add(session, 0, 1_000, byteArrayOf(1))
            queue.add(session, 1, 1_020, byteArrayOf(2))
            queue.add(session, 2, 900, byteArrayOf(3))

            assertEquals(2, queue.seal())
        }

    @Test
    fun `unsealed frames survive a restart`() =
        runTest {
            val file = File(context.cacheDir, "restart.db").also { it.delete() }
            val first = QueueDatabase.open(context, file.absolutePath)
            FrameQueue(first).addFrames(10)
            first.close()

            val second = QueueDatabase.open(context, file.absolutePath).also(databases::add)
            val queue = FrameQueue(second)

            assertEquals(1, queue.seal())
            assertEquals(10, queue.oldest()!!.frameCount)
        }

    @Test
    fun `cap drops the oldest chunk`() =
        runTest {
            // Each chunk: 38 + 10 records of 6 + 3 bytes = 128 bytes, so three take 384.
            val queue = FrameQueue(inMemory(), capBytes = 300)
            val sessions = List(3) { UUID.randomUUID() }
            sessions.forEach { queue.addFrames(10, session = it) }

            queue.seal()

            assertEquals(1L, queue.usage.value.droppedChunks)
            assertEquals(256L, queue.usage.value.bytes)
            val remaining = drain(queue).map { it.session }
            assertEquals(sessions.drop(1).map { it.toString() }, remaining)
        }

    @Test
    fun `oldest and remove walk the queue in order`() =
        runTest {
            val queue = FrameQueue(inMemory())
            queue.addFrames(3)
            queue.seal()
            queue.addFrames(2, from = 3)
            queue.seal()

            val first = queue.oldest()!!
            queue.remove(first.id)
            val second = queue.oldest()!!
            queue.remove(second.id)

            assertEquals(listOf(0L, 3L), listOf(first.firstSeq, second.firstSeq))
            assertNull(queue.oldest())
            assertEquals(0, queue.usage.value.chunks)
        }

    /** Takes every chunk off the queue, oldest first. */
    private suspend fun drain(queue: FrameQueue): List<SealedChunk> =
        buildList {
            while (true) {
                val chunk = queue.oldest() ?: break
                add(chunk)
                queue.remove(chunk.id)
            }
        }
}
