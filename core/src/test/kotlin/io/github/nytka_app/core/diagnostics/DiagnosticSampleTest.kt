package io.github.nytka_app.core.diagnostics

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticSampleTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a sample from before the offline sync still reads`() {
        val old = json.encodeToString(DiagnosticSample.serializer(), sample(1))

        val read = json.decodeFromString(DiagnosticSample.serializer(), old)

        assertEquals(sample(1), read)
        assertNull(read.syncState)
        assertEquals(0L, read.syncedPackets)
    }

    @Test
    fun `the sync counters round trip`() {
        val counted =
            sample(1).copy(
                syncState = "syncing",
                ringReadSeq = 10,
                ringWriteSeq = 5_000,
                ringCapacity = 1_150_000,
                ringDropped = 3,
                lastDoneStatus = 0,
                syncedPackets = 2_000,
                lostPackets = 12,
                syncKbPerSecond = 38.5,
                syncLiveLoss = 0.02,
                mutedFrames = 40,
                badStampRecords = 2,
                clockSkewS = -360,
                segments = 1,
                parkedChunks = 1,
            )

        val text = json.encodeToString(DiagnosticSample.serializer(), counted)

        assertEquals(counted, json.decodeFromString(DiagnosticSample.serializer(), text))
        assertTrue(text.contains("\"ringWriteSeq\":5000"))
    }

    @Test
    fun `carries no audio, transcript, url or token`() {
        val descriptor = DiagnosticSample.serializer().descriptor
        val fields = (0 until descriptor.elementsCount).map(descriptor::getElementName)

        assertTrue(fields.none { it.contains("token", true) || it.contains("url", true) || it.contains("text", true) })
    }
}
