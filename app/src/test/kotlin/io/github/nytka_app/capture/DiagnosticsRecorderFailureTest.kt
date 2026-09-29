package io.github.nytka_app.capture

import io.github.nytka_app.core.diagnostics.DiagnosticSample
import io.github.nytka_app.core.diagnostics.DiagnosticsSink
import io.github.nytka_app.core.queue.QueueUsage
import io.github.nytka_app.core.upload.UploadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric only for `android.util.Log`, which the recorder calls when a write fails. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiagnosticsRecorderFailureTest {
    private class BrokenSink : DiagnosticsSink {
        var attempts = 0

        override suspend fun add(sample: DiagnosticSample) {
            attempts++
            error("disk on fire")
        }

        override suspend fun prune() = throw IllegalStateException("no such table")
    }

    @Test
    fun `no failure of the log ends the sampling, the scope or the shutdown`() =
        runTest {
            val sink = BrokenSink()
            val recorder =
                DiagnosticsRecorder(
                    MutableStateFlow(CaptureStatus()),
                    MutableStateFlow(QueueUsage()),
                    MutableStateFlow(UploadState()),
                    sink,
                    backgroundScope,
                    appVersion = "0.2.0",
                    device = "Pixel 8 / Android 16",
                    now = { testScheduler.currentTime },
                )
            recorder.start()

            runCurrent()
            advanceTimeBy(20_001)
            recorder.stop()

            assertEquals(4, sink.attempts) // t = 0, 10 s, 20 s and the last one on stop
        }
}
