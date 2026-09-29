package io.github.nytka_app.core.chunks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.UUID

class ChunkFormatTest {
    // nytka-app/server tests/Nytka.Audio.Tests/Frames/ChunkFormatTests.cs holds the same bytes.
    private val goldenHex =
        "4e59544b011500112233445566778899aabbccddeeff0700000000d7879299010000020000000000000003000102031400000002000405"

    private val golden =
        Chunk(
            session = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"),
            firstSeq = 7,
            baseTimeMs = 1_759_100_000_000,
            frames =
                listOf(
                    ChunkFrame(7, 1_759_100_000_000, byteArrayOf(1, 2, 3)),
                    ChunkFrame(8, 1_759_100_000_020, byteArrayOf(4, 5)),
                ),
        )

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private fun unhex(text: String) = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `writes the golden chunk`() = assertEquals(goldenHex, hex(ChunkWriter.write(golden)))

    @Test
    fun `reads the golden chunk`() = assertEquals(golden, ChunkReader.read(unhex(goldenHex)))

    @Test
    fun `reads chunks written back to back`() {
        val second =
            golden.copy(
                firstSeq = 9,
                frames = listOf(ChunkFrame(9, 1_759_100_000_040, byteArrayOf(6))),
            )

        val all = ChunkReader.readAll(ChunkWriter.write(golden) + ChunkWriter.write(second))

        assertEquals(listOf(golden, second), all)
        assertEquals(9L, all[1].lastSeq)
    }

    @Test
    fun `refuses frames out of sequence`() {
        val broken = golden.copy(frames = listOf(golden.frames[0], ChunkFrame(9, 1_759_100_000_020, byteArrayOf(4))))

        assertThrows(IllegalArgumentException::class.java) { ChunkWriter.write(broken) }
    }

    @Test
    fun `refuses a frame captured before the base time`() {
        assertThrows(IllegalArgumentException::class.java) {
            ChunkWriter.write(golden.copy(baseTimeMs = golden.baseTimeMs + 1))
        }
    }

    @Test
    fun `refuses more than 1500 frames`() {
        val frames = (0 until 1501).map { ChunkFrame(it.toLong(), it * 20L, byteArrayOf(1)) }

        assertThrows(IllegalArgumentException::class.java) {
            ChunkWriter.write(Chunk(golden.session, 0, 0, frames))
        }
    }

    @Test
    fun `rejects a bad magic and a truncated chunk`() {
        val bytes = unhex(goldenHex)
        val badMagic = bytes.copyOf().also { it[0] = 'X'.code.toByte() }

        assertThrows(IllegalArgumentException::class.java) { ChunkReader.read(badMagic) }
        assertThrows(IllegalArgumentException::class.java) { ChunkReader.read(bytes.copyOf(bytes.size - 1)) }
    }

    @Test
    fun `record size counts the six byte record header`() =
        assertEquals(
            unhex(goldenHex).size,
            ChunkFormat.HEADER_SIZE + ChunkFormat.recordSize(3) + ChunkFormat.recordSize(2),
        )
}
