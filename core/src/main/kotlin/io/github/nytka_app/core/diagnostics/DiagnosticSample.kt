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
    // Offline sync (v0.3). Additive: every field has a default, so older samples still read, and with the
    // default encoder a field at its default is left out of the JSON.
    val syncState: String? = null,
    val ringReadSeq: Long? = null,
    val ringWriteSeq: Long? = null,
    val ringCapacity: Long? = null,
    val ringDropped: Long? = null,
    val lastDoneStatus: Int? = null,
    val syncedPackets: Long = 0,
    val lostPackets: Long = 0,
    val syncKbPerSecond: Double? = null,
    val syncLiveLoss: Double? = null,
    val mutedFrames: Long = 0,
    val badStampRecords: Long = 0,
    val clockSkewS: Long? = null,
    val segments: Long = 0,
    val parkedChunks: Int = 0,
    // The link (v0.13): signal strength in dBm and the status of the last link loss, to set drops against signal.
    val rssi: Int? = null,
    val lastDisconnectStatus: Int? = null,
)
