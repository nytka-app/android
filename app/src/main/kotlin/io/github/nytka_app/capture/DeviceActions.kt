package io.github.nytka_app.capture

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.nytka_app.core.diagnostics.DiagnosticsCsv
import io.github.nytka_app.core.diagnostics.DiagnosticsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject

/** What the screens ask of the system; a fake in tests. */
interface DeviceActions {
    fun forgetPendant(address: String)

    fun startCapture()

    fun restartCapture()

    fun stopCapture()

    /**
     * Opens Nytka's app info in the system settings. A permission Android has stopped asking for can only be allowed
     * there.
     */
    fun openAppSettings()

    /**
     * Writes the last 7 days of diagnostics as CSV to the app's cache, page by page off the main thread, and opens
     * the share sheet for it. Returns how many samples it holds; with none, nothing is written or shared.
     */
    suspend fun shareDiagnostics(): Int
}

class AndroidDeviceActions
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val diagnostics: DiagnosticsSource,
    ) : DeviceActions {
        override fun forgetPendant(address: String) = CompanionPairing(context).forget(address)

        override fun startCapture() = CaptureService.start(context)

        override fun restartCapture() = CaptureService.restart(context)

        override fun stopCapture() = CaptureService.stop(context)

        override fun openAppSettings() =
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )

        override suspend fun shareDiagnostics(): Int {
            val (file, count) = withContext(Dispatchers.IO) { writeDiagnostics() }
            if (file == null) return 0
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.diagnostics", file)
            // Read access only, for the app the user picks; nothing else of ours is exposed.
            val send =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("Nytka diagnostics", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            context.startActivity(
                Intent
                    .createChooser(send, "Export diagnostics")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
            return count
        }

        /** Only the file being shared stays in the cache; a failed write leaves none. */
        private suspend fun writeDiagnostics(): Pair<File?, Int> {
            var page = diagnostics.recent(PAGE, 0)
            if (page.isEmpty()) return null to 0
            val directory = File(context.cacheDir, DIAGNOSTICS_DIRECTORY).apply { mkdirs() }
            directory.listFiles()?.forEach(File::delete)
            val file = File(directory, "nytka-diagnostics-${Instant.now().toString().replace(':', '-')}.csv")
            var count = 0
            var written = false
            try {
                file.bufferedWriter().use { out ->
                    out.write(DiagnosticsCsv.header())
                    while (page.isNotEmpty()) {
                        page.forEach { out.write(DiagnosticsCsv.row(it)) }
                        count += page.size
                        page = diagnostics.recent(PAGE, count)
                    }
                }
                written = true
            } finally {
                if (!written) file.delete()
            }
            return file to count
        }

        private companion object {
            const val DIAGNOSTICS_DIRECTORY = "diagnostics"
            const val PAGE = 2_000
        }
    }
