package io.github.nytka_app.core.diagnostics

/** RFC 4180: a header row, one row per sample, CRLF line ends, quotes around fields that need them. */
object DiagnosticsCsv {
    private val columns: List<Pair<String, (DiagnosticSample) -> Any?>> =
        listOf(
            "id" to { it.id },
            "at" to { it.at },
            "session" to { it.session },
            "connection" to { it.connection },
            "battery" to { it.battery },
            "notifications" to { it.notifications },
            "lostNotifications" to { it.lostNotifications },
            "droppedFrames" to { it.droppedFrames },
            "frames" to { it.frames },
            "framesQueued" to { it.framesQueued },
            "queueBytes" to { it.queueBytes },
            "queueChunks" to { it.queueChunks },
            "queueFrames" to { it.queueFrames },
            "uploadFailures" to { it.uploadFailures },
            "uploadPaused" to { it.uploadPaused },
            "lastUploadAt" to { it.lastUploadAt },
            "lastResult" to { it.lastResult },
            "appVersion" to { it.appVersion },
            "device" to { it.device },
        )

    private const val NEEDS_QUOTES = ",\"\r\n"

    fun header(): String = columns.joinToString(",") { it.first } + "\r\n"

    fun row(sample: DiagnosticSample): String =
        columns.joinToString(",") { escape(it.second(sample)?.toString().orEmpty()) } + "\r\n"

    fun write(samples: List<DiagnosticSample>): String =
        buildString {
            append(header())
            samples.forEach { append(row(it)) }
        }

    private fun escape(field: String): String =
        if (field.any { it in NEEDS_QUOTES }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
