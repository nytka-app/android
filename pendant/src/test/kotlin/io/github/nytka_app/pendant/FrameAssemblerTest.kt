package io.github.nytka_app.pendant

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameAssemblerTest {
    private var clock = 1_000L
    private val assembler = FrameAssembler { clock }

    /** A notification: counter (u16 LE), fragment index, payload. */
    private fun packet(
        counter: Int,
        fragment: Int,
        vararg payload: Int,
    ) = byteArrayOf((counter and 0xFF).toByte(), (counter shr 8 and 0xFF).toByte(), fragment.toByte()) +
        payload.map { it.toByte() }.toByteArray()

    private fun feed(vararg packets: ByteArray): List<AudioFrame> =
        packets.mapNotNull {
            clock += 10
            assembler.accept(it)
        }

    @Test
    fun `a frame completes when the next one starts`() {
        val frames = feed(packet(0, 0, 1, 2), packet(1, 0, 3), packet(2, 0, 4))

        assertEquals(2, frames.size)
        assertArrayEquals(byteArrayOf(1, 2), frames[0].payload)
        assertArrayEquals(byteArrayOf(3), frames[1].payload)
    }

    @Test
    fun `fragments join into one frame`() {
        val frames = feed(packet(10, 0, 1), packet(11, 1, 2), packet(12, 2, 3), packet(13, 0, 9))

        assertArrayEquals(byteArrayOf(1, 2, 3), frames.single().payload)
    }

    @Test
    fun `capture time is when the last fragment arrived`() {
        val frames = feed(packet(0, 0, 1), packet(1, 1, 2), packet(2, 0, 3))

        assertEquals(1_020L, frames.single().capturedAtMs)
    }

    @Test
    fun `counter wrap is not loss`() {
        val frames = feed(packet(65_534, 0, 1), packet(65_535, 0, 2), packet(0, 0, 3), packet(1, 0, 4))

        assertEquals(3, frames.size)
        assertEquals(0L, assembler.lostNotifications)
        assertEquals(0L, assembler.droppedFrames)
    }

    @Test
    fun `gap drops only the partial frame`() {
        // Frame A is whole; frame B loses its second fragment (counter 3); frame C is whole.
        val frames =
            feed(
                packet(0, 0, 1),
                packet(1, 0, 2),
                packet(2, 1, 2), // 3 lost
                packet(4, 0, 3),
                packet(5, 0, 4),
            )

        assertEquals(listOf(1, 3), frames.map { it.payload.first().toInt() })
        assertEquals(1L, assembler.lostNotifications)
        assertEquals(1L, assembler.droppedFrames)
    }

    @Test
    fun `a skipped fragment index drops the partial frame`() {
        val frames = feed(packet(0, 0, 1), packet(1, 2, 2), packet(2, 0, 3), packet(3, 0, 4))

        assertEquals(listOf(3), frames.map { it.payload.first().toInt() })
        assertEquals(0L, assembler.lostNotifications)
    }

    @Test
    fun `reset forgets the partial frame and the counter`() {
        feed(packet(100, 0, 1))
        assembler.reset()

        val frames = feed(packet(7, 0, 2), packet(8, 0, 3))

        assertEquals(listOf(2), frames.map { it.payload.first().toInt() })
        assertEquals(0L, assembler.lostNotifications)
    }

    @Test
    fun `notifications shorter than the header are ignored`() {
        assertNull(assembler.accept(byteArrayOf(1, 2)))
        assertEquals(0L, assembler.notifications)
    }
}
