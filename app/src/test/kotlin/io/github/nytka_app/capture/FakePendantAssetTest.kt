package io.github.nytka_app.capture

import io.github.nytka_app.core.chunks.ChunkReader
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FakePendantAssetTest {
    @Test
    fun `the bundled recording is valid chunks of consecutive frames`() {
        // Unit tests run in the module directory.
        val chunks = ChunkReader.readAll(File("src/main/assets/fake_pendant.nytk").readBytes())
        val frames = chunks.flatMap { it.frames }

        assertTrue("at least 5 s of audio", frames.size >= 250)
        assertTrue(frames.zipWithNext().all { (a, b) -> b.seq == a.seq + 1 && b.capturedAtMs - a.capturedAtMs == 20L })
    }
}
