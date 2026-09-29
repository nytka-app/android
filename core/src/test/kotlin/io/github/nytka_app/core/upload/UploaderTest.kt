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
