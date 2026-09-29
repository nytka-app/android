package io.github.nytka_app

import io.github.nytka_app.core.diagnostics.DiagnosticRow
import io.github.nytka_app.core.diagnostics.DiagnosticSample
import io.github.nytka_app.core.diagnostics.DiagnosticsSource
import kotlinx.coroutines.flow.MutableStateFlow

fun sample(n: Int = 0) =
    DiagnosticSample(
        id = "00000000-0000-7000-8000-%012d".format(n),
        at = "2026-09-29T09:15:%02d.123Z".format(n),
        session = null,
        connection = "connected",
        battery = 82,
        notifications = 10,
        lostNotifications = 0,
        droppedFrames = 0,
        frames = 9,
        framesQueued = 9,
        queueBytes = 100,
        queueChunks = 1,
        queueFrames = 2,
        uploadFailures = 0,
        uploadPaused = null,
        lastUploadAt = null,
        lastResult = null,
        appVersion = "0.2.0",
        device = "Pixel 8 / Android 16",
    )

/** The diagnostics log without Room. */
class FakeDiagnostics(
    samples: List<DiagnosticSample> = emptyList(),
) : DiagnosticsSource {
    val stored = samples.toMutableList()
    val uploaded = mutableSetOf<String>()
    override val count = MutableStateFlow(stored.size)

    fun add(vararg samples: DiagnosticSample) {
        stored += samples
        count.value = stored.size
    }

    override suspend fun pending(limit: Int) =
        stored.filter { it.id !in uploaded }.take(limit).map { DiagnosticRow(it.id, 0, "{\"id\":\"${it.id}\"}") }

    override suspend fun markUploaded(ids: List<String>) {
        uploaded += ids
    }

    override suspend fun recent(): List<DiagnosticSample> = stored
}
