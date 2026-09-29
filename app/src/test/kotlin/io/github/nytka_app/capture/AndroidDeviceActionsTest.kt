package io.github.nytka_app.capture

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.github.nytka_app.sample
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
            val actions = AndroidDeviceActions(app)
            actions.shareDiagnostics(listOf(sample(9)))
            shadowOf(app).nextStartedActivity // the first share is not the one under test
            File(app.cacheDir, "diagnostics").listFiles()!!.single().setLastModified(0)

            actions.shareDiagnostics(listOf(sample(1), sample(2)))
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
            assertEquals(3, csv.trimEnd().lines().size)
            assertTrue(csv.startsWith("id,at,session,connection,"))
            assertEquals(
                listOf(uri.lastPathSegment),
                File(app.cacheDir, "diagnostics").listFiles()!!.map(File::getName),
            )
        }
}
