package io.github.nytka_app.core.queue

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.nytka_app.core.chunks.ChunkReader
import io.github.nytka_app.core.ring.ClockPair
import io.github.nytka_app.core.ring.MuteChange
import io.github.nytka_app.core.ring.RingPosition
import io.github.nytka_app.core.ring.TimedFrame
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoredQueueTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databases = mutableListOf<QueueDatabase>()
    private val live = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    private val stored = UUID.fromString("00000000-0000-0000-0000-00000000000b")

    private fun inMemory() =
        Room
            .inMemoryDatabaseBuilder(context, QueueDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also(databases::add)

    @After
    fun close() = databases.forEach { it.close() }

    private fun position(
        committedNext: Long,
        epoch: Long = 0,
        session: UUID? = stored,
        nextFrame: Long = 0,
    ) = RingPosition(
        pendant = "AA:BB",
        committedNext = committedNext,
        epoch = epoch,
        session = session,
        nextFrame = nextFrame,
    )

    /** One frame per ring sequence in [ringSeqs], numbered from [firstSeq] in [session], 20 ms apart. */
    private fun timed(
        ringSeqs: LongRange,
        session: UUID = stored,
        firstSeq: Long = 0,
        startMs: Long = 1_000_000,
    ) = ringSeqs.mapIndexed { i, ring ->
        TimedFrame(session, firstSeq + i, startMs + i * 20L, ring, byteArrayOf(ring.toByte(), 1))
    }

    @Test
    fun `a commit stores the frames and the position together`() =
        runTest {
            val queue = FrameQueue(inMemory())
            val next =
                position(committedNext = 13, nextFrame = 3)
                    .copy(
                        advanced = 10,
                        lastDropped = 4,
                        lastStampS = 1_800_000_000,
                        clock = ClockPair(10, -42),
                        staleClock = true,
                    )

            queue.commit(timed(10L..12L), next)

            assertEquals(next, queue.position())
            assertEquals(3, queue.usageNow().frames)
        }

    @Test
    fun `a commit that fails leaves neither frames nor position`() =
        runTest {
            val queue = FrameQueue(inMemory())
            queue.commit(timed(0L..2L), position(committedNext = 3, nextFrame = 3))

            try {
                queue.commit(timed(5L..6L, firstSeq = 2), position(committedNext = 7, nextFrame = 4)) // seq 2 again
                fail("the duplicate frame should be refused")
            } catch (_: Exception) {
                // expected
            }

            assertEquals(3L, queue.position()!!.committedNext)
            assertEquals(3, queue.usageNow().frames)
        }

    @Test
    fun `seals live and stored frames into separate chunks and records the ring range`() =
        runTest {
            val queue = FrameQueue(inMemory())
            queue.add(live, 0, 10, byteArrayOf(1))
            queue.commit(timed(40L..43L), position(committedNext = 44, epoch = 3, nextFrame = 4))
            queue.add(live, 1, 30, byteArrayOf(2))

            assertEquals(2, queue.seal())

            val chunks = drain(queue)
            assertEquals(listOf(false, true), chunks.map { it.stored }) // live ones go first
            val ring = chunks[1]
            assertEquals(40L, ring.ringFirst)
            assertEquals(43L, ring.ringLast)
            assertEquals(3L, ring.epoch)
            assertEquals(4, ring.frameCount)
            assertNull(chunks[0].ringFirst)
            assertEquals(stored, ChunkReader.read(ring.body).session)
        }

    @Test
    fun `stored frames of two sessions never share a chunk`() =
        runTest {
            val queue = FrameQueue(inMemory())
            val other = UUID.fromString("00000000-0000-0000-0000-00000000000c")
            queue.commit(timed(0L..1L), position(committedNext = 2, nextFrame = 2))
            queue.commit(timed(2L..3L, session = other), position(committedNext = 4, session = other, nextFrame = 2))

            assertEquals(2, queue.seal())

            assertEquals(listOf(stored.toString(), other.toString()), drain(queue).map { it.session })
        }

    @Test
    fun `stored frames of two ring epochs never share a chunk`() =
        runTest {
            val queue = FrameQueue(inMemory())
            queue.commit(timed(0L..1L), position(committedNext = 2, nextFrame = 2))
            queue.commit(timed(0L..1L, firstSeq = 2), position(committedNext = 2, epoch = 1, nextFrame = 4))

            assertEquals(2, queue.seal())

            assertEquals(
                listOf(0L to 1L, 0L to 1L),
                drain(queue).map { it.ringFirst to it.ringLast }.map {
                    it.first to
                        it.second
                },
            )
        }

    @Test
    fun `live chunks go before stored ones, each oldest first`() =
        runTest {
            val queue = FrameQueue(inMemory())
            queue.commit(timed(0L..1L), position(committedNext = 2, nextFrame = 2))
            queue.seal()
            queue.add(live, 0, 10, byteArrayOf(1))
            queue.seal()
            queue.commit(timed(2L..3L, firstSeq = 2, startMs = 2_000_000), position(committedNext = 4, nextFrame = 4))
            queue.add(live, 1, 20, byteArrayOf(2))
            queue.seal()

            val order = drain(queue).map { it.stored to it.firstSeq }

            assertEquals(listOf(false to 0L, false to 1L, true to 0L, true to 2L), order)
        }

    @Test
    fun `ackedThrough is the lowest ring sequence the queue still holds, or committedNext`() =
        runTest {
            val queue = FrameQueue(inMemory())
            queue.commit(emptyList(), position(committedNext = 100))
            assertEquals(100L, queue.ackedThrough.first())

            queue.commit(timed(90L..92L), position(committedNext = 93, nextFrame = 3))
            queue.commit(timed(93L..99L, firstSeq = 3), position(committedNext = 100, nextFrame = 10))
            assertEquals(90L, queue.ackedThrough.first()) // frames not yet sealed

            queue.seal()
            assertEquals(90L, queue.ackedThrough.first()) // sealed, not yet uploaded

            queue.remove(queue.oldest()!!.id)
            assertEquals(100L, queue.ackedThrough.first()) // the server has it
        }

    @Test
    fun `live frames and chunks do not hold back the ring`() =
        runTest {
            val queue = FrameQueue(inMemory())
            queue.commit(emptyList(), position(committedNext = 100))
            queue.add(live, 0, 10, byteArrayOf(1))
            queue.seal()

            assertEquals(100L, queue.ackedThrough.first())
        }

    @Test
    fun `frames and chunks of an earlier epoch do not hold back the new one`() =
        runTest {
            val queue = FrameQueue(inMemory())
            queue.commit(timed(3L..5L), position(committedNext = 6, epoch = 0, nextFrame = 3))
            queue.seal()

            queue.commit(emptyList(), position(committedNext = 50, epoch = 1))

            assertEquals(50L, queue.ackedThrough.first())
        }

    @Test
    fun `there is no ackedThrough without a position`() =
        runTest {
            val database = inMemory()
            val queue = FrameQueue(database)
            queue.add(live, 0, 10, byteArrayOf(1))

            assertNull(queue.position())
            assertNull(database.queue().ackedThrough().first())
        }

    @Test
    fun `a parked chunk leaves the upload queue but still holds back ackedThrough`() =
        runTest {
            val queue = FrameQueue(inMemory())
            queue.commit(timed(20L..22L), position(committedNext = 23, nextFrame = 3))
            queue.seal()
            val chunk = queue.oldest()!!

            queue.park(chunk.id, 409, "range differs")

            assertNull(queue.oldest())
            assertEquals(1, queue.usage.value.parkedChunks)
            assertEquals(0, queue.usage.value.chunks)
            assertEquals(20L, queue.ackedThrough.first())
        }

    @Test
    fun `a parked chunk keeps its body and range`() =
        runTest {
            val database = inMemory()
            val queue = FrameQueue(database, now = { 777 })
            queue.commit(timed(20L..22L), position(committedNext = 23, nextFrame = 3))
            queue.seal()
            val chunk = queue.oldest()!!

            queue.park(chunk.id, 400, "bad")

            val parked =
                database.openHelper.readableDatabase.query(
                    "select code, reason, ringFirst, ringLast, parkedAtMs, length(body) from parked_chunks",
                )
            parked.use {
                assertTrue(it.moveToFirst())
                assertEquals(400, it.getInt(0))
                assertEquals("bad", it.getString(1))
                assertEquals(20L, it.getLong(2))
                assertEquals(22L, it.getLong(3))
                assertEquals(777L, it.getLong(4))
                assertEquals(chunk.body.size, it.getInt(5))
            }
        }

    @Test
    fun `parking a chunk that is gone does nothing`() =
        runTest {
            val queue = FrameQueue(inMemory())

            queue.park(99, 409, "gone")

            assertEquals(0, queue.usageNow().parkedChunks)
        }

    @Test
    fun `the cap drops live chunks and never deletes a stored one`() =
        runTest {
            // Each chunk: 38 + 10 records of 6 + 2 bytes = 118 bytes.
            val queue = FrameQueue(inMemory(), capBytes = 150)
            queue.commit(timed(20L..29L), position(committedNext = 30, nextFrame = 10))
            queue.seal()
            repeat(3) { queue.add(live, it.toLong(), 10L + it, byteArrayOf(1)) }
            queue.seal()

            assertEquals(20L, queue.ackedThrough.first())
            assertEquals(true, queue.oldest()!!.stored)
            assertEquals(1L, queue.usageNow().droppedChunks)
        }

    @Test
    fun `over the cap with only stored chunks left, they are parked, not deleted`() =
        runTest {
            val queue = FrameQueue(inMemory(), capBytes = 100)
            queue.commit(timed(20L..29L), position(committedNext = 40, nextFrame = 20))
            queue.commit(timed(30L..39L, firstSeq = 10), position(committedNext = 40, nextFrame = 20))
            queue.seal()

            val usage = queue.usageNow()

            assertEquals(1, usage.parkedChunks)
            assertEquals(0L, usage.droppedChunks)
            assertEquals(20L, queue.ackedThrough.first())
        }

    @Test
    fun `markAdvanced moves only the advanced point`() =
        runTest {
            val queue = FrameQueue(inMemory())
            queue.commit(emptyList(), position(committedNext = 100, nextFrame = 5))

            queue.markAdvanced(90)

            val stored = queue.position()!!
            assertEquals(90L, stored.advanced)
            assertEquals(100L, stored.committedNext)
            assertEquals(5L, stored.nextFrame)
        }

    @Test
    fun `clearPosition forgets the position and ackedThrough goes quiet`() =
        runTest {
            val queue = FrameQueue(inMemory())
            queue.commit(emptyList(), position(committedNext = 100))

            queue.clearPosition()

            assertNull(queue.position())
        }

    @Test
    fun `the mute log comes back in time order`() =
        runTest {
            val queue = FrameQueue(inMemory())
            queue.recordMute(2_000, false)
            queue.recordMute(1_000, true)

            assertEquals(listOf(MuteChange(1_000, true), MuteChange(2_000, false)), queue.muteChanges())
        }

    @Test
    fun `stored data survives a restart`() =
        runTest {
            val file = java.io.File(context.cacheDir, "stored-restart.db").also { it.delete() }
            val first = QueueDatabase.open(context, file.absolutePath)
            FrameQueue(first).commit(timed(0L..2L), position(committedNext = 3, nextFrame = 3))
            first.close()

            val queue = FrameQueue(QueueDatabase.open(context, file.absolutePath).also(databases::add))

            assertEquals(3L, queue.position()!!.committedNext)
            assertEquals(1, queue.seal())
            assertEquals(0L, queue.ackedThrough.first())
        }

    /** Commits [count] ring records (one frame each) in batches of [batch], like the sync window does. */
    private suspend fun FrameQueue.commitBatches(
        count: Int,
        batch: Int = 20,
        onBatch: suspend (Int) -> Unit = {},
    ) {
        var from = 0
        while (from < count) {
            val to = minOf(from + batch, count)
            commit(
                timed(from.toLong() until to.toLong(), firstSeq = from.toLong()),
                position(committedNext = to.toLong(), nextFrame = to.toLong()),
            )
            onBatch(to)
            from = to
        }
    }

    @Test
    fun `stored frames committed in small batches seal into full chunks`() =
        runTest {
            val queue = FrameQueue(inMemory())
            queue.commitBatches(2_000)

            queue.seal()

            val chunks = drain(queue)
            assertEquals(listOf(1_500, 500), chunks.map { it.frameCount })
            assertTrue(chunks.all { it.stored })
        }

    @Test
    fun `live frames arriving between stored batches do not cut stored chunks`() =
        runTest {
            val queue = FrameQueue(inMemory())
            var liveSeq = 0L
            queue.commitBatches(2_000) {
                queue.add(live, liveSeq, 5_000_000 + liveSeq * 20, byteArrayOf(1))
                liveSeq++
            }

            queue.seal()

            val chunks = drain(queue)
            assertEquals(listOf(false, true, true), chunks.map { it.stored })
            assertEquals(listOf(100, 1_500, 500), chunks.map { it.frameCount })
        }

    @Test
    fun `a periodic seal keeps a partial stored run and the window end seals it`() =
        runTest {
            val queue = FrameQueue(inMemory())
            var sealed = 0
            queue.commitBatches(2_000) { sealed += queue.seal(includePartialStored = false) }

            assertEquals(1, sealed) // only the full 1500-frame chunk
            assertEquals(1, queue.seal())
            assertEquals(listOf(1_500, 500), drain(queue).map { it.frameCount })
        }

    @Test
    fun `a periodic seal cuts a stored run that a later session ends`() =
        runTest {
            val queue = FrameQueue(inMemory())
            val other = UUID.fromString("00000000-0000-0000-0000-00000000000c")
            queue.commit(timed(0L..9L), position(committedNext = 10, nextFrame = 10))
            queue.commit(timed(10L..14L, session = other), position(committedNext = 15, session = other, nextFrame = 5))

            assertEquals(1, queue.seal(includePartialStored = false))

            assertEquals(listOf(10), drain(queue).map { it.frameCount })
            assertEquals(1, queue.seal())
        }

    private suspend fun FrameQueue.usageNow(): QueueUsage {
        refreshUsage()
        return usage.value
    }

    private suspend fun drain(queue: FrameQueue): List<SealedChunk> =
        buildList {
            while (true) {
                val chunk = queue.oldest() ?: break
                add(chunk)
                queue.remove(chunk.id)
            }
        }
}
