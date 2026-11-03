package io.github.nytka_app.pendant

import io.github.nytka_app.pendant.RingFixtures.frame
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** What every [RecorderStorage] driver must do, whatever its recorder: a driver adds a subclass with its own rig. */
abstract class RecorderStorageContractTest {
    /** A storage with a recorder behind it that the test can fill, stall and cut off. */
    interface Rig {
        val storage: RecorderStorage

        /** Puts [blocks] more blocks in the recorder, each one with a frame. */
        fun fill(blocks: Int)

        /** The recorder stops sending after [blocks] blocks of a window, like a stalled link. */
        fun stallAfter(blocks: Int)

        /** The link drops. */
        fun cutLink()

        /** The first position the recorder still holds, read from the recorder itself. */
        fun first(): Long
    }

    protected abstract fun TestScope.rig(): Rig

    @Test
    fun `status reports what the recorder holds`() =
        runTest {
            val rig = rig().apply { fill(3) }
            val range = rig.storage.status()
            assertNotNull(range)
            assertEquals(0L, range!!.first)
            assertEquals(3L, range.end)
            assertNull(rig.storage.lastStatus.value)
        }

    @Test
    fun `a window delivers every block once in position order and ends with Done`() =
        runTest {
            val rig = rig().apply { fill(3) }
            val events = rig.storage.read(0, 10).toList()
            val blocks = events.filterIsInstance<StoredEvent.Blocks>().flatMap { it.blocks }
            assertEquals(listOf(0L, 1L, 2L), blocks.map { it.position })
            val done = events.last() as StoredEvent.Done
            assertEquals(StorageStatus.OK, done.status)
            assertEquals(3L, done.next)
        }

    @Test
    fun `cancelling a read frees nothing`() =
        runTest {
            val rig = rig().apply { fill(5) }
            rig.stallAfter(2)
            val job = backgroundScope.launch { rig.storage.read(0, 10).collect { } }
            runCurrent()
            job.cancel()
            runCurrent()
            assertEquals(0L, rig.first())
        }

    @Test
    fun `advance frees below the position and refuses one the recorder does not have`() =
        runTest {
            val rig = rig().apply { fill(3) }
            assertEquals(StorageStatus.OK, rig.storage.advance(2))
            assertEquals(2L, rig.first())
            assertEquals(StorageStatus.OUT_OF_RANGE, rig.storage.advance(9))
            assertEquals(2L, rig.first())
        }

    @Test
    fun `a lost link ends the window with LINK_LOST`() =
        runTest {
            val rig = rig().apply { fill(5) }
            rig.stallAfter(2)
            val events = mutableListOf<StoredEvent>()
            val job = backgroundScope.launch { rig.storage.read(0, 10).collect { events += it } }
            runCurrent()
            rig.cutLink()
            runCurrent()
            assertEquals(true, job.isCompleted)
            assertEquals(StorageStatus.LINK_LOST, (events.last() as StoredEvent.Done).status)
        }
}

/** The Omi driver over [FakeRing], firmware 3.0.20 so that only [RecorderStorage.advance] frees audio. */
class OmiRecorderStorageContractTest : RecorderStorageContractTest() {
    override fun TestScope.rig(): Rig {
        val ring = FakeRing(backgroundScope, firmware = "3.0.20", autoAdvance = false)
        val omi = OmiStorage(ring).also { ring.listener = it::onNotification }
        omi.evaluate(ring.firmware, ring.features)
        return object : Rig {
            override val storage: RecorderStorage = omi

            override fun fill(blocks: Int) =
                ring.store(1_800_000_000L, List(blocks * FRAMES_PER_RECORD) { frame(97, 1) })

            override fun stallAfter(blocks: Int) {
                ring.stallAfterBytes = RingProtocol.RECORD_BYTES * blocks + 10L
            }

            override fun cutLink() {
                ring.disconnect()
                omi.linkLost()
            }

            override fun first() = ring.readSeq
        }
    }

    private companion object {
        const val FRAMES_PER_RECORD = 4
    }
}
