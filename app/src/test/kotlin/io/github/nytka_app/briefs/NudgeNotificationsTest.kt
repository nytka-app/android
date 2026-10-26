package io.github.nytka_app.briefs

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import io.github.nytka_app.alerts.Notifier
import io.github.nytka_app.core.api.Nudge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NudgeNotificationsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val nudge =
        Nudge("n1", "commitment-due", "Send the photos", "Due 18:00", "nytka://conversation/c1", "2026-10-26T11:00:00Z")

    private fun Notification.extra(key: String) = extras.getCharSequence(key)?.toString()

    @Test
    fun `shows the server's title and body on the Nudges channel and opens the link`() {
        val notification = NudgeNotifications.build(app, nudge)

        assertEquals("Send the photos", notification.extra(Notification.EXTRA_TITLE))
        assertEquals("Due 18:00", notification.extra(Notification.EXTRA_TEXT))
        assertEquals(Notifier.NUDGES_CHANNEL, notification.channelId)
        assertEquals("A nudge from Nytka", notification.publicVersion.extra(Notification.EXTRA_TITLE))
        val intent = shadowOf(notification.contentIntent).savedIntent
        assertEquals("nytka://conversation/c1", intent.dataString)
    }

    @Test
    fun `a link of another scheme opens the app without data`() {
        val notification = NudgeNotifications.build(app, nudge.copy(deepLink = "https://example.com"))

        assertEquals(null, shadowOf(notification.contentIntent).savedIntent.data)
    }

    @Test
    fun `posting the same nudge twice leaves one notification`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertTrue(NudgeNotifications.post(app, nudge))
        assertTrue(NudgeNotifications.post(app, nudge))

        assertEquals(1, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun `without the permission nothing is posted`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertFalse(NudgeNotifications.post(app, nudge))
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }
}
