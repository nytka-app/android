package io.github.nytka_app.core.diagnostics

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.nytka_app.core.queue.QueueDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiagnosticsLogTest {
    private val database =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), QueueDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private var nowMs = 1_800_000_000_000
    private val log = DiagnosticsLog(database.diagnostics(), now = { nowMs })

    @After
    fun close() = database.close()

    @Test
    fun `pending samples come oldest first and leave once marked uploaded`() =
        runTest {
            log.add(sample(2))
            log.add(sample(1))
            log.add(sample(3))

            assertEquals(listOf(sample(1).id, sample(2).id), log.pending(2).map { it.id })
            log.markUploaded(log.pending(2).map { it.id })

            assertEquals(listOf(sample(3).id), log.pending(500).map { it.id })
            assertEquals(3, log.count.first())
        }

    @Test
    fun `a sample with a known id is not stored twice`() =
        runTest {
            log.add(sample(1))
            log.add(sample(1))

            assertEquals(1, log.count.first())
        }

    @Test
    fun `prune forgets samples older than seven days and keeps the rest`() =
        runTest {
            val day = 24 * 60 * 60 * 1000L
            log.add(sample(1, atMs = nowMs - 8 * day))
            log.add(sample(2, atMs = nowMs - 6 * day))
            log.add(sample(3, atMs = nowMs))

            log.prune()

            assertEquals(listOf(sample(2).id, sample(3).id).sorted(), log.recent(100, 0).map { it.id }.sorted())
            assertEquals(2, log.count.first())
        }

    @Test
    fun `recent returns the stored samples for the last seven days, oldest first`() =
        runTest {
            val day = 24 * 60 * 60 * 1000L
            log.add(sample(2, atMs = nowMs - day))
            log.add(sample(1, atMs = nowMs - 2 * day))
            log.add(sample(3, atMs = nowMs - 9 * day))

            assertEquals(listOf(sample(1, atMs = nowMs - 2 * day), sample(2, atMs = nowMs - day)), log.recent(100, 0))
        }

    @Test
    fun `recent reads in pages that add up to the whole week`() =
        runTest {
            repeat(5) { log.add(sample(it)) }

            val pages = listOf(0, 2, 4).map { log.recent(limit = 2, offset = it).map(DiagnosticSample::id) }

            assertEquals(List(5) { sample(it).id }, pages.flatten())
            assertEquals(listOf(2, 2, 1), pages.map { it.size })
        }
}
