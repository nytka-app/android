package io.github.nytka_app.capture

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import io.github.nytka_app.core.queue.QueueUsage
import io.github.nytka_app.pendant.PendantConnection
import io.github.nytka_app.pendant.PendantInfo
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CaptureWidgetTest {
    private val connected = PendantConnection.Connected(PendantInfo("Omi"))

    private fun shows(
        status: CaptureStatus,
        usage: QueueUsage,
        vararg texts: String,
    ) = runTest {
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { CaptureWidgetContent(status, usage) }
            texts.forEach { onNode(hasText(it)).assertExists() }
        }
    }

    @Test
    fun `shows recording, battery and queue length with the mute action`() =
        shows(
            CaptureStatus(running = true, connection = connected, battery = 82),
            QueueUsage(chunks = 3),
            "Recording",
            "Pendant battery: 82%",
            "Queued chunks: 3",
            "Mute",
        )

    @Test
    fun `shows muted with the unmute action`() =
        shows(
            CaptureStatus(running = true, connection = connected, muted = true, battery = 50),
            QueueUsage(),
            "Muted",
            "Unmute",
        )

    @Test
    fun `not running reads recording off, with an unknown battery`() =
        shows(CaptureStatus(), QueueUsage(), "Recording off", "Pendant battery: Unknown")

    @Test
    fun `running without the pendant reads waiting`() =
        shows(CaptureStatus(running = true), QueueUsage(), "Waiting for pendant")
}
