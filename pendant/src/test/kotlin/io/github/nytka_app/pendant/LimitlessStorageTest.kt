package io.github.nytka_app.pendant

import io.github.nytka_app.pendant.LimitlessFixtures.TEN_MS
import io.github.nytka_app.pendant.LimitlessFixtures.opus
import io.github.nytka_app.pendant.LimitlessFixtures.pageStamp
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitlessStorageTest {
    private class Setup(
        val pendant: FakeLimitless,
        val storage: LimitlessStorage,
    )

    private var resting = LimitlessMode.Live

    private fun TestScope.setup(
        evaluated: Boolean = true,
        configure: FakeLimitless.() -> Unit = {},
    ): Setup {
        val pendant = FakeLimitless(backgroundScope).apply(configure)
        val storage = LimitlessStorage(pendant, LimitlessCommands(), { resting })
        pendant.listener = { storage.onNotification(it) }
        if (evaluated) storage.evaluate()
        return Setup(pendant, storage)
    }

    private fun FakeLimitless.fill(pages: Int) = repeat(pages) { store(pageStamp(it), listOf(opus(fill = it + 1))) }

    private fun List<StoredEvent>.blocks() = filterIsInstance<StoredEvent.Blocks>().flatMap { it.blocks }

    @Test
    fun `storage is unavailable until the link is evaluated and again after it is lost`() =
        runTest {
            val rig = setup(evaluated = false)
            assertEquals(StorageSupport.Unknown, rig.storage.support.value)
            assertNull(rig.storage.status())
            assertEquals(StorageStatus.UNAVAILABLE, rig.storage.lastStatus.value)
            rig.storage.evaluate()
            assertEquals(StorageSupport.Supported, rig.storage.support.value)
            rig.storage.linkLost()
            assertEquals(StorageSupport.Unknown, rig.storage.support.value)
        }

    @Test
    fun `an empty flash is an empty range and the clock skew is unknown`() =
        runTest {
            val rig = setup()
            val range = rig.storage.status()!!
            assertEquals(range.first, range.end)
            assertNull(rig.storage.clockSkew.value)
        }

    @Test
    fun `a pendant that does not answer is a timeout`() =
        runTest {
            val rig = setup { silent = true }
            assertNull(rig.storage.status())
            assertEquals(StorageStatus.TIMEOUT, rig.storage.lastStatus.value)
            val events = rig.storage.read(0, 10).toList()
            assertEquals(StorageStatus.TIMEOUT, (events.single() as StoredEvent.Done).status)
        }

    @Test
    fun `a block carries the page clock in milliseconds and its frames`() =
        runTest {
            val rig = setup()
            rig.pendant.store(pageStamp(0), listOf(opus(fill = 7)))
            val block =
                rig.storage
                    .read(0, 10)
                    .toList()
                    .blocks()
                    .single()
            assertEquals(0L, block.position)
            assertEquals(pageStamp(0), block.stamp)
            assertEquals(StampUnit.Milliseconds, block.stampUnit)
            assertArrayEquals(opus(fill = 7), block.frames.single())
        }

    @Test
    fun `10 ms packets of a page come out as 20 ms packets`() =
        runTest {
            val rig = setup()
            rig.pendant.store(pageStamp(0), List(4) { opus(TEN_MS, fill = it) })
            val block =
                rig.storage
                    .read(0, 10)
                    .toList()
                    .blocks()
                    .single()
            assertEquals(2, block.frames.size)
        }

    @Test
    fun `a drain puts the pendant in download mode and back in the mode the app wants`() =
        runTest {
            val rig = setup()
            rig.pendant.fill(2)
            rig.storage.read(0, 10).toList()
            assertEquals(listOf(LimitlessMode.Download, LimitlessMode.Live), rig.pendant.modes)
            assertFalse(rig.storage.downloading)

            resting = LimitlessMode.RecordOnly
            rig.storage.read(0, 10).toList()
            assertEquals(LimitlessMode.RecordOnly, rig.pendant.mode)
        }

    @Test
    fun `a window takes only the pages asked for`() =
        runTest {
            val rig = setup()
            rig.pendant.fill(5)
            val events = rig.storage.read(1, 2).toList()
            assertEquals(listOf(1L, 2L), events.blocks().map { it.position })
            val begin = events.first() as StoredEvent.Begin
            assertEquals(1L, begin.start)
            assertEquals(2L, begin.count)
            assertEquals(3L, (events.last() as StoredEvent.Done).next)
            assertTrue(rig.pendant.acknowledged.isEmpty())
        }

    @Test
    fun `a read from before the oldest page starts at the oldest`() =
        runTest {
            val rig = setup()
            rig.pendant.fill(4)
            rig.storage.advance(2)
            val events = rig.storage.read(0, 10).toList()
            assertEquals(listOf(2L, 3L), events.blocks().map { it.position })
        }

    @Test
    fun `advance acknowledges the page before the position it is given`() =
        runTest {
            val rig = setup()
            rig.pendant.fill(4)
            assertEquals(StorageStatus.OK, rig.storage.advance(3))
            assertEquals(listOf(2L), rig.pendant.acknowledged)
            assertEquals(3L, rig.pendant.first)
            assertEquals(StorageStatus.OUT_OF_RANGE, rig.storage.advance(3))
            assertEquals(StorageStatus.OUT_OF_RANGE, rig.storage.advance(0))
        }

    @Test
    fun `pages are read whole from a message in fragments holding several pages`() =
        runTest {
            val rig = setup { fragmentBytes = 16 }
            rig.pendant.pagesPerMessage = 2
            rig.pendant.fill(5)
            val events = rig.storage.read(0, 10).toList()
            assertEquals(listOf(0L, 1L, 2L, 3L, 4L), events.blocks().map { it.position })
            assertEquals(StorageStatus.OK, (events.last() as StoredEvent.Done).status)
        }

    @Test
    fun `a page that cannot be read ends the window before it and frees nothing`() =
        runTest {
            val rig = setup()
            rig.pendant.fill(1)
            rig.pendant.storeRaw(LimitlessMessages.page(pageStamp(1), listOf(ByteArray(12))))
            rig.pendant.fill(1)
            val events = rig.storage.read(0, 10).toList()
            assertEquals(listOf(0L), events.blocks().map { it.position })
            val done = events.last() as StoredEvent.Done
            assertEquals(StorageStatus.UNAVAILABLE, done.status)
            assertEquals(1L, done.next)
            assertTrue(rig.pendant.acknowledged.isEmpty())
            assertEquals(LimitlessMode.Live, rig.pendant.mode)
        }

    @Test
    fun `a page without audio is passed as a block with no frames`() =
        runTest {
            val rig = setup()
            rig.pendant.storeRaw(LimitlessMessages.page(pageStamp(0), emptyList()))
            val events = rig.storage.read(0, 10).toList()
            assertTrue(
                events
                    .blocks()
                    .single()
                    .frames
                    .isEmpty(),
            )
            assertEquals(StorageStatus.OK, (events.last() as StoredEvent.Done).status)
        }

    @Test
    fun `a stalled drain times out with what it got and puts the mode back`() =
        runTest {
            val rig = setup { stallAfterPages = 2 }
            rig.pendant.fill(5)
            val events = rig.storage.read(0, 10).toList()
            assertEquals(listOf(0L, 1L), events.blocks().map { it.position })
            val done = events.last() as StoredEvent.Done
            assertEquals(StorageStatus.TIMEOUT, done.status)
            assertEquals(2L, done.next)
            assertEquals(LimitlessMode.Live, rig.pendant.mode)
            assertFalse(rig.storage.downloading)
        }

    @Test
    fun `a failed mode write is a timeout and frees nothing`() =
        runTest {
            val rig = setup()
            rig.pendant.fill(2)
            val pending = rig.storage.status()
            assertEquals(2L, pending!!.end)
            rig.pendant.failWrites = true
            val events = rig.storage.read(0, 10).toList()
            assertEquals(StorageStatus.TIMEOUT, (events.last() as StoredEvent.Done).status)
            assertEquals(0L, rig.pendant.first)
        }

    @Test
    fun `pages that arrive after the window are not taken for live audio`() =
        runTest {
            val rig = setup()
            rig.pendant.fill(1)
            val late =
                LimitlessMessages.notifications(
                    9,
                    LimitlessMessages.pages(
                        1,
                        listOf(
                            8L to LimitlessMessages.page(0, listOf(opus())),
                        ),
                    ),
                    200,
                )
            assertFalse(rig.storage.onNotification(late.single()))
        }
}
