package io.github.nytka_app.capture

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.nytka_app.core.diagnostics.DiagnosticSample
import io.github.nytka_app.core.diagnostics.DiagnosticsCsv
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

    /** Writes [samples] as CSV to the app's cache and opens the share sheet for it. */
    suspend fun shareDiagnostics(samples: List<DiagnosticSample>)
}

class AndroidDeviceActions
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : DeviceActions {
        override fun forgetPendant(address: String) = CompanionPairing(context).forget(address)

        override fun startCapture() = CaptureService.start(context)

        override fun restartCapture() = CaptureService.restart(context)

        override fun stopCapture() = CaptureService.stop(context)

        override suspend fun shareDiagnostics(samples: List<DiagnosticSample>) {
            val file =
                withContext(Dispatchers.IO) {
                    val directory = File(context.cacheDir, DIAGNOSTICS_DIRECTORY).apply { mkdirs() }
                    // Only the file being shared stays in the cache.
                    directory.listFiles()?.forEach(File::delete)
                    File(directory, "nytka-diagnostics-${Instant.now().toString().replace(':', '-')}.csv")
                        .also { it.writeText(DiagnosticsCsv.write(samples)) }
                }
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
        }

        private companion object {
            const val DIAGNOSTICS_DIRECTORY = "diagnostics"
        }
    }
