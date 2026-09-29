package io.github.nytka_app.core.diagnostics

import java.time.Instant

fun sample(
    n: Int = 0,
    atMs: Long = 1_800_000_000_000 + n * 10_000L,
    device: String = "Pixel 8 / Android 16",
    lastResult: String? = "202",
) = DiagnosticSample(
    id = "00000000-0000-7000-8000-%012d".format(n),
    at = Instant.ofEpochMilli(atMs).toString(),
    session = "11111111-1111-4111-8111-111111111111",
    connection = "connected",
    battery = 82,
    notifications = 1000L + n,
    lostNotifications = 1,
    droppedFrames = 2,
    frames = 900L + n,
    framesQueued = 890L + n,
    queueBytes = 4096,
    queueChunks = 2,
    queueFrames = 30,
    uploadFailures = 0,
    uploadPaused = null,
    lastUploadAt = null,
    lastResult = lastResult,
    appVersion = "0.2.0",
    device = device,
)
