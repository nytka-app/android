package io.github.nytka_app.capture

import android.app.Application
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.github.nytka_app.R
import io.github.nytka_app.core.queue.QueueUsage
import io.github.nytka_app.pendant.PendantConnection
import io.github.nytka_app.pendant.PendantInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CaptureWidgetTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val connected = PendantConnection.Connected(PendantInfo("Omi"))

    private fun inflate(
        status: CaptureStatus,
        usage: QueueUsage,
    ): FrameLayout {
        val parent = FrameLayout(app)
        CaptureWidget.remoteViews(app, status, usage).apply(app, parent)
        return parent
    }

    @Test
    fun `shows recording, battery and queue length`() {
        val root = inflate(CaptureStatus(running = true, connection = connected, battery = 82), QueueUsage(chunks = 3))

        assertEquals("Recording", root.findViewById<TextView>(R.id.widget_status).text.toString())
        assertEquals("Pendant battery: 82%", root.findViewById<TextView>(R.id.widget_battery).text.toString())
        assertEquals("Queued chunks: 3", root.findViewById<TextView>(R.id.widget_queue).text.toString())
        assertEquals("Mute", root.findViewById<Button>(R.id.widget_mute_button).text.toString())
    }

    @Test
    fun `shows muted with the unmute action`() {
        val root =
            inflate(CaptureStatus(running = true, connection = connected, muted = true, battery = 50), QueueUsage())

        assertEquals("Muted", root.findViewById<TextView>(R.id.widget_status).text.toString())
        assertEquals("Unmute", root.findViewById<Button>(R.id.widget_mute_button).text.toString())
    }

    @Test
    fun `not running reads not recording, with an unknown battery`() {
        val root = inflate(CaptureStatus(), QueueUsage())

        assertEquals("Not recording", root.findViewById<TextView>(R.id.widget_status).text.toString())
        assertEquals("Pendant battery: Unknown", root.findViewById<TextView>(R.id.widget_battery).text.toString())
    }
}
