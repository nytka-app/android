package io.github.nytka_app.core.upload

import io.github.nytka_app.core.api.UploadResult
import io.github.nytka_app.core.queue.QueueUsage
import io.github.nytka_app.core.queue.SealedChunk
import io.github.nytka_app.core.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UploaderTest {
    private class FakeChunks(
        vararg firstSeqs: Long,
    ) : ChunkSource {
        val chunks =
            firstSeqs
                .map {
                    SealedChunk(
                        id = it,
                        session = "s",
                        firstSeq = it,
                        frameCount = 1,
                        createdAtMs = 0,
                        body = byteArrayOf(it.toByte()),
                    )
                }.toMutableList()
        override val usage: MutableStateFlow<QueueUsage> = MutableStateFlow(QueueUsage(chunks = chunks.size))

        override suspend fun oldest() = chunks.firstOrNull()

        override suspend fun remove(chunkId: Long) {
            chunks.removeAll { it.id == chunkId }
            usage.value = QueueUsage(chunks = chunks.size)
        }

        /** Makes the chunk with [id] a stored one, from ring sequence [id]. */
        fun store(id: Long) {
            val at = chunks.indexOfFirst { it.id == id }
            val chunk = chunks[at]
            chunks[at] =
                SealedChunk(
                    id = chunk.id,
                    session = chunk.session,
                    firstSeq = chunk.firstSeq,
                    frameCount = chunk.frameCount,
                    createdAtMs = chunk.createdAtMs,
                    body = chunk.body,
                    stored = true,
                    ringFirst = id,
                    ringLast = id,
                )
        }
    }

    private class ParkingChunks(
        vararg firstSeqs: Long,
    ) : ChunkSource by FakeChunks(*firstSeqs) {
        val inner = FakeChunks(*firstSeqs)
        val parked = mutableListOf<Triple<Long, Int, String>>()

        override val usage get() = inner.usage

        override suspend fun oldest() = inner.oldest()

        override suspend fun remove(chunkId: Long) = inner.remove(chunkId)

        override suspend fun park(
            chunkId: Long,
            code: Int,
            reason: String,
        ) {
            parked += Triple(chunkId, code, reason)
            inner.remove(chunkId)
        }
    }

    private val settings = MutableStateFlow(Settings(serverUrl = "https://nytka.example", token = "old"))
    private val sent = mutableListOf<Byte>()

    private fun TestScope.uploader(
        chunks: ChunkSource,
        answer: (ByteArray) -> UploadResult,
    ) = Uploader(chunks, { body ->
        sent += body[0]
        answer(body)
    }, settings, now = { testScheduler.currentTime })

    private val accepted = UploadResult.Accepted(0, duplicate = false)

    @Test
    fun `uploads oldest first and removes accepted chunks`() =
        runTest {
            val chunks = FakeChunks(1, 2)
            val uploader = uploader(chunks) { accepted }

            assertEquals(DrainResult.Empty, uploader.drain())

            assertEquals(listOf<Byte>(1, 2), sent)
            assertEquals(0, chunks.chunks.size)
            assertEquals(2L, uploader.state.value.uploadedChunks)
        }

    @Test
    fun `a dropped chunk leaves the queue and the rest carry on`() =
        runTest {
            val chunks = FakeChunks(1, 2)
            val uploader =
                uploader(chunks) {
                    if (it[0] ==
                        1.toByte()
                    ) {
                        UploadResult.Dropped(409, "overlap")
                    } else {
                        accepted
                    }
                }

            uploader.drain()

            assertEquals(0, chunks.chunks.size)
            assertEquals(1L, uploader.state.value.droppedChunks)
        }

    @Test
    fun `a stored chunk the server refuses is parked, not dropped`() =
        runTest {
            val chunks = ParkingChunks(1, 2)
            chunks.inner.store(1)
            val uploader =
                uploader(chunks) {
                    if (it[0] ==
                        1.toByte()
                    ) {
                        UploadResult.Dropped(409, "overlap")
                    } else {
                        accepted
                    }
                }

            uploader.drain()

            assertEquals(listOf(Triple(1L, 409, "overlap")), chunks.parked)
            assertEquals(0, chunks.inner.chunks.size)
            assertEquals(1L, uploader.state.value.parkedChunks)
            assertEquals(0L, uploader.state.value.droppedChunks)
        }

    @Test
    fun `a live chunk the server refuses is still removed`() =
        runTest {
            val chunks = ParkingChunks(1)
            val uploader = uploader(chunks) { UploadResult.Dropped(413, "too large") }

            uploader.drain()

            assertEquals(emptyList<Triple<Long, Int, String>>(), chunks.parked)
            assertEquals(1L, uploader.state.value.droppedChunks)
            assertEquals(0L, uploader.state.value.parkedChunks)
        }

    @Test
    fun `a source without a store of its own removes the chunk it is asked to park`() =
        runTest {
            val chunks = FakeChunks(1)
            chunks.store(1)
            val uploader = uploader(chunks) { UploadResult.Dropped(400, "bad") }

            uploader.drain()

            assertEquals(0, chunks.chunks.size)
        }

    @Test
    fun `only an accepted answer removes a stored chunk`() =
        runTest {
            val chunks = FakeChunks(1, 2)
            chunks.store(1)
            chunks.store(2)
            val answers = ArrayDeque(listOf<UploadResult>(UploadResult.Retry("down")))
            val uploader = uploader(chunks) { answers.removeFirstOrNull() ?: accepted }

            assertEquals(DrainResult.Failed(5_000), uploader.drain())
            assertEquals(2, chunks.chunks.size)

            assertEquals(DrainResult.Empty, uploader.drain())
            assertEquals(0, chunks.chunks.size)
        }

    @Test
    fun `unauthorized keeps a stored chunk in the queue`() =
        runTest {
            val chunks = FakeChunks(1)
            chunks.store(1)
            val uploader = uploader(chunks) { UploadResult.Unauthorized }

            uploader.drain()

            assertEquals(1, chunks.chunks.size)
        }

    @Test
    fun `backoff doubles from 5 seconds to 5 minutes`() {
        assertEquals(
            listOf(5_000L, 10_000L, 20_000L, 40_000L, 80_000L, 160_000L, 300_000L, 300_000L),
            (1..8).map(Backoff()::delayMs),
        )
    }

    @Test
    fun `retries after the backoff`() =
        runTest {
            val chunks = FakeChunks(1)
            var failuresLeft = 2
            val uploader = uploader(chunks) { if (failuresLeft-- > 0) UploadResult.Retry("down") else accepted }
            backgroundScope.launch { uploader.run() }

            runCurrent()
            assertEquals(1, sent.size)
            advanceTimeBy(4_999)
            assertEquals(1, sent.size)
            advanceTimeBy(2)
            assertEquals(2, sent.size)
            advanceTimeBy(10_000)

            assertEquals(3, sent.size)
            assertEquals(0, chunks.chunks.size)
        }

    @Test
    fun `unauthorized pauses without dropping`() =
        runTest {
            val chunks = FakeChunks(1)
            val uploader =
                uploader(chunks) {
                    if (settings.value.token ==
                        "old"
                    ) {
                        UploadResult.Unauthorized
                    } else {
                        accepted
                    }
                }
            backgroundScope.launch { uploader.run() }

            runCurrent()
            advanceTimeBy(3_600_000)

            assertEquals(1, sent.size)
            assertEquals(1, chunks.chunks.size)
            assertNotNull(uploader.state.value.paused)

            settings.value = settings.value.copy(token = "new")
            runCurrent()

            assertEquals(0, chunks.chunks.size)
            assertNull(uploader.state.value.paused)
        }

    @Test
    fun `unreachable since the first failure until a success`() =
        runTest {
            val chunks = FakeChunks(1)
            var failuresLeft = 2
            val uploader = uploader(chunks) { if (failuresLeft-- > 0) UploadResult.Retry("down") else accepted }
            backgroundScope.launch { uploader.run() }

            runCurrent()
            advanceTimeBy(5_001)
            assertEquals(0L, uploader.state.value.unreachableSinceMs)

            advanceTimeBy(10_000)
            assertNull(uploader.state.value.unreachableSinceMs)
        }
}
