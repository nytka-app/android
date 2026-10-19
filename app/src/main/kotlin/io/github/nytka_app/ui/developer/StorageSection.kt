package io.github.nytka_app.ui.developer

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.nytka_app.R
import io.github.nytka_app.capture.StorageSyncStatus
import io.github.nytka_app.capture.SyncState
import io.github.nytka_app.ui.SettingsBlock
import io.github.nytka_app.ui.device.serverUnsupported
import io.github.nytka_app.ui.theme.NytkaTheme
import java.util.Locale

/** Developer mode's lines about the offline sync, one string each, in the order they are shown. */
data class StorageSection(
    val lines: List<String>,
)

private const val PERCENT = 100

/** Null (no section) when the pendant cannot store or the server is too old: there is nothing to measure. */
fun storageDetails(status: StorageSyncStatus): StorageSection? {
    if (status.serverUnsupported() || status.state is SyncState.Unsupported) return null
    return StorageSection(
        buildList {
            status.ring?.let { ring ->
                val (read, write) = ring.readSeq to ring.writeSeq
                add("Ring: read $read, write $write, capacity ${ring.capacityPackets}, dropped ${ring.droppedPackets}")
            }
            add("Last DONE status: ${status.lastDoneStatus ?: "none"}")
            val rate = status.kbPerSecond?.let { String.format(Locale.ROOT, "%.1f KB/s", it) }
            add("Rate: ${rate ?: "not measured"}")
            add(
                "Live-stream loss during the last window: " +
                    (status.liveLoss?.let { String.format(Locale.ROOT, "%.1f%%", it * PERCENT) } ?: "not measured"),
            )
            add("Frames dropped for a mute: ${status.mutedFrames}")
            add("Records dropped for a bad stamp: ${status.badStampRecords}")
            add("Clock skew: ${status.skewS?.let { "$it s" } ?: "unknown"}")
            add("Clock segments: ${status.segments}")
        },
    )
}

@Composable
fun StorageSectionCard(section: StorageSection) {
    SettingsBlock(stringResource(R.string.pendant_storage)) {
        section.lines.forEach { Text(it, style = NytkaTheme.typography.code) }
    }
}
