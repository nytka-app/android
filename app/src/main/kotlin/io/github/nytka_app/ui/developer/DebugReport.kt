package io.github.nytka_app.ui.developer

import io.github.nytka_app.capture.CaptureStatus
import io.github.nytka_app.core.queue.QueueUsage
import io.github.nytka_app.core.settings.Settings
import io.github.nytka_app.core.upload.UploadState
import io.github.nytka_app.pendant.PendantConnection
import java.util.Locale

/** What a bug report needs, and nothing that identifies the server's token or anyone's words. */
object DebugReport {
    fun build(
        appVersion: String,
        android: String,
        device: String,
        settings: Settings,
        capture: CaptureStatus,
        upload: UploadState,
        usage: QueueUsage,
        serverStatus: String,
        diagnosticsSamples: Int,
    ): String =
        buildString {
            appendLine("Nytka $appVersion")
            appendLine(android)
            appendLine(device)
            appendLine(settings.toString())
            appendLine(
                "Capture: running=${capture.running}, connection=${capture.connection::class.simpleName}, " +
                    "muted=${capture.muted}, battery=${capture.battery}, frames=${capture.stats.frames}, " +
                    "loss=${"%.2f".format(Locale.ROOT, capture.stats.lossFraction * 100)}%",
            )
            appendLine(
                "Queue: ${usage.chunks} chunks, ${usage.frames} frames, ${usage.bytes} bytes, " +
                    "${usage.droppedChunks} dropped at the cap",
            )
            appendLine(
                "Upload: ${upload.uploadedChunks} uploaded, ${upload.droppedChunks} dropped, " +
                    "${upload.failures} failures in a row",
            )
            appendLine(
                "diagnostics=$diagnosticsSamples samples, upload=${if (settings.diagnosticsUpload) "on" else "off"}",
            )
            appendLine("Last upload result: ${upload.lastResult ?: "none"}")
            appendLine("Server status: $serverStatus")
            val errors = listOfNotNull(upload.paused, (capture.connection as? PendantConnection.Refused)?.reason)
            appendLine("Recent errors: ${errors.ifEmpty { listOf("none") }.joinToString("; ")}")
        }
}
