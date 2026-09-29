package io.github.nytka_app.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DiagnosticsCsvTest {
    private val header =
        "id,at,session,connection,battery,notifications,lostNotifications,droppedFrames,frames,framesQueued," +
            "queueBytes,queueChunks,queueFrames,uploadFailures,uploadPaused,lastUploadAt,lastResult,appVersion,device"

    @Test
    fun `a header row and one row per sample`() {
        val lines = DiagnosticsCsv.write(listOf(sample(1), sample(2))).split("\r\n")

        assertEquals(header, lines[0])
        assertEquals(4, lines.size) // header, two rows, and the empty piece after the last CRLF
        assertEquals("", lines[3])
        assertEquals(
            "00000000-0000-7000-8000-000000000001,2027-01-15T08:00:10Z,11111111-1111-4111-8111-111111111111," +
                "connected,82,1001,1,2,901,891,4096,2,30,0,,,202,0.2.0,Pixel 8 / Android 16",
            lines[1],
        )
    }

    @Test
    fun `an empty log is only the header`() {
        assertEquals("$header\r\n", DiagnosticsCsv.write(emptyList()))
    }

    @Test
    fun `quotes fields with commas, quotes and line breaks`() {
        val csv = DiagnosticsCsv.write(listOf(sample(device = "Maker, \"Pro\"", lastResult = "a\nb")))

        assertEquals(true, csv.contains("\"Maker, \"\"Pro\"\"\""))
        assertEquals(true, csv.contains("\"a\nb\""))
    }

    @Test
    fun `has exactly the columns of the contract`() {
        val columns = header.split(",")

        assertEquals(19, columns.size)
        assertFalse(columns.any { it.contains("token", ignoreCase = true) || it.contains("url", ignoreCase = true) })
    }

    @Test
    fun `header and rows written one at a time make the same file`() {
        val samples = listOf(sample(1), sample(2))

        assertEquals(
            DiagnosticsCsv.write(samples),
            DiagnosticsCsv.header() + samples.joinToString("") { DiagnosticsCsv.row(it) },
        )
    }
}
