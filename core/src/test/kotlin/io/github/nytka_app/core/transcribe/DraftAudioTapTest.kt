package io.github.nytka_app.core.transcribe

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.nytka_app.core.queue.FrameQueue
import io.github.nytka_app.core.queue.QueueDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DraftAudioTapTest {
    private val database =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), QueueDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val queue = FrameQueue(database)
    private val tap = DraftAudioTap(database)
    private val dao = database.drafts()
    private val session = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @After
    fun close() = database.close()

    private suspend fun sealFrames(
        count: Int,
        from: Long = 0,
    ) {
        repeat(count) { queue.add(session, from + it, 1_000 + (from + it) * 20, ByteArray(40) { 1 }) }
        queue.seal()
    }

    @Test
    fun `while on, each sealed chunk is copied and outlives its upload`() =
        runTest {
            tap.sync(true)
            sealFrames(3)
            val chunk = queue.oldest()!!

            queue.remove(chunk.id)

            assertNull(queue.oldest())
            val copy = dao.oldestAudio(10).single()
            assertArrayEquals(chunk.body, copy.body)
            assertEquals(chunk.id, copy.chunkId)
            assertEquals(3, copy.frameCount)
        }

    @Test
    fun `while off, nothing is copied and the queue seals as before`() =
        runTest {
            sealFrames(3)

            assertEquals(0, dao.audioCount().first())
            assertEquals(3, queue.oldest()!!.frameCount)
        }

    @Test
    fun `switching off stops the copy and deletes the copies waiting`() =
        runTest {
            tap.sync(true)
            sealFrames(3)

            tap.sync(false)
            sealFrames(3, from = 3)

            assertEquals(0, dao.audioCount().first())
            assertEquals(2, database.queue().chunkCount())
        }

    @Test
    fun `switching on twice keeps one trigger`() =
        runTest {
            tap.sync(true)
            tap.sync(true)
            sealFrames(3)

            assertEquals(1, dao.audioCount().first())
        }

    @Test
    fun `the copy keeps the newest copies up to the cap`() =
        runTest {
            tap.sync(true)
            val db = database.openHelper.writableDatabase
            repeat(DraftAudioTap.MAX_COPIES + 5) {
                db.execSQL(
                    "insert into chunks (session, firstSeq, frameCount, createdAtMs, body) " +
                        "values ('s', $it, 1, 5, x'01')",
                )
            }

            val rows = dao.oldestAudio(DraftAudioTap.MAX_COPIES + 10)
            assertEquals(DraftAudioTap.MAX_COPIES, rows.size)
            assertEquals(5L, rows.first().firstSeq)
        }
}
