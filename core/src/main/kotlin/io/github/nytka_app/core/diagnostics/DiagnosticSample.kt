package io.github.nytka_app.core.diagnostics

import kotlinx.serialization.Serializable

/**
 * One reading of the link, the queue and the uploader, taken every 10 s while capture runs. The wire
 * format of `POST /api/v1/diagnostics`: counters are cumulative per pendant connection, so consumers
 * compute rates from differences. Nothing else goes in: no audio, no transcript, no server URL, no token.
 */
@Serializable
data class DiagnosticSample(
    val id: String,
    val at: String,
    val session: String?,
    val connection: String,
    val battery: Int?,
    val notifications: Long,
    val lostNotifications: Long,
    val droppedFrames: Long,
    val frames: Long,
    val framesQueued: Long,
    val queueBytes: Long,
    val queueChunks: Int,
    val queueFrames: Int,
    val uploadFailures: Int,
    val uploadPaused: String?,
    val lastUploadAt: String?,
    val lastResult: String?,
    val appVersion: String,
    val device: String,
)
