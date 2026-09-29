package io.github.nytka_app.ui.developer

import io.github.nytka_app.capture.CaptureStatus
import io.github.nytka_app.core.queue.QueueUsage
import io.github.nytka_app.core.settings.Settings
import io.github.nytka_app.core.upload.UploadState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugReportTest {
    @Test
    fun `holds versions and errors and never the token`() {
        val report =
            DebugReport.build(
                appVersion = "0.1.0 (oss)",
                android = "Android 16 (API 36)",
                device = "Google Pixel 9",
                settings =
                    Settings(
                        serverUrl = "https://nytka.example/",
                        token = "secret-token-value-0123456789abcdef",
                    ),
                capture = CaptureStatus(running = true),
                upload = UploadState(lastResult = "The server answered 503.", paused = null),
                usage = QueueUsage(chunks = 2),
                serverStatus = "3 pending, last error: none",
                diagnosticsSamples = 0,
            )

        assertTrue(report.contains("Nytka 0.1.0 (oss)"))
        assertTrue(report.contains("Android 16 (API 36)"))
        assertTrue(report.contains("The server answered 503."))
        assertTrue(report.contains("token=set"))
        assertFalse(report.contains("secret-token-value"))
    }

    private fun report(
        settings: Settings,
        samples: Int,
    ) = DebugReport.build(
        appVersion = "0.2.0 (oss)",
        android = "Android 16 (API 36)",
        device = "Google Pixel 9",
        settings = settings,
        capture = CaptureStatus(running = true),
        upload = UploadState(),
        usage = QueueUsage(),
        serverStatus = "not checked",
        diagnosticsSamples = samples,
    )

    @Test
    fun `says how many diagnostics samples are kept and whether they are sent`() {
        assertTrue(report(Settings(), samples = 4_320).lines().contains("diagnostics=4320 samples, upload=off"))
        assertTrue(
            report(
                Settings(diagnosticsUpload = true),
                samples = 0,
            ).lines().contains("diagnostics=0 samples, upload=on"),
        )
    }
}
