package io.github.nytka_app.capture

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import io.github.nytka_app.FakeDiagnostics
import io.github.nytka_app.sample
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidDeviceActionsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    // One test: FileProvider caches its roots per authority for the whole JVM, and each test has its own cache dir.
    @Test
    fun `shares the csv through the file provider with read access only and keeps one file in the cache`() =
        runTest {
            val diagnostics = FakeDiagnostics()
            val actions = AndroidDeviceActions(app, diagnostics)
            diagnostics.add(sample(9))
            assertEquals(1, actions.shareDiagnostics())
            shadowOf(app).nextStartedActivity // the first share is not the one under test
            File(app.cacheDir, "diagnostics").listFiles()!!.single().setLastModified(0)

            diagnostics.stored.clear()
            diagnostics.add(*Array(4_500) { sample(it) })
            assertEquals(4_500, actions.shareDiagnostics())
            val chooser = shadowOf(app).nextStartedActivity
            val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
            val uri = send.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)!!

            assertEquals(Intent.ACTION_SEND, send.action)
            assertEquals("text/csv", send.type)
            assertEquals("io.github.nytka_app.diagnostics", uri.authority)
            assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, send.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            assertEquals(0, chooser.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val csv = app.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            assertEquals(4_501, csv.trimEnd().lines().size) // the header and every row, from three pages
            assertEquals(
                sample(4_499).id,
                csv
                    .trimEnd()
                    .lines()
                    .last()
                    .substringBefore(','),
            )
            assertTrue(csv.startsWith("id,at,session,connection,"))
            assertEquals(
                listOf(uri.lastPathSegment),
                File(app.cacheDir, "diagnostics").listFiles()!!.map(File::getName),
            )
        }

    @Test
    fun `with nothing recorded nothing is written or shared`() =
        runTest {
            assertEquals(0, AndroidDeviceActions(app, FakeDiagnostics()).shareDiagnostics())

            assertNull(shadowOf(app).nextStartedActivity)
        }

    @Test
    fun `opens the app info of this app in the system settings`() {
        AndroidDeviceActions(app, FakeDiagnostics()).openAppSettings()

        val intent = shadowOf(app).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:io.github.nytka_app", intent.dataString)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `before Android 17 the local network needs no permission`() {
        assertTrue(AndroidDeviceActions(app, FakeDiagnostics()).localNetworkGranted())
    }
}
