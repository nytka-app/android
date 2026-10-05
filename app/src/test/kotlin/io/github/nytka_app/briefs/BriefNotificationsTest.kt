package io.github.nytka_app.briefs

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import io.github.nytka_app.alerts.Notifier
import io.github.nytka_app.core.api.BriefAttendee
import io.github.nytka_app.core.api.BriefText
import io.github.nytka_app.core.api.UpcomingBrief
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BriefNotificationsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val brief =
        UpcomingBrief(
            uid = "u1",
            title = "Lunch with Olena",
            startsAt = "2026-10-05T12:30:00Z",
            endsAt = "2026-10-05T13:30:00Z",
            attendees = listOf(BriefAttendee("Olena", "p1")),
            brief = BriefText("b1", "Olena runs the choir. Open: send her the photos."),
        )

    private fun Notification.extra(key: String) = extras.getCharSequence(key)?.toString()

    @Test
    fun `shows the event title, the start time and the brief`() {
        val notification = BriefNotifications.build(app, brief, ZoneOffset.UTC)

        assertEquals("Brief: Lunch with Olena", notification.extra(Notification.EXTRA_TITLE))
        assertEquals("Starts at 12:30", notification.extra(Notification.EXTRA_TEXT))
        assertEquals(
            "Olena runs the choir. Open: send her the photos.",
            notification.extra(Notification.EXTRA_BIG_TEXT),
        )
        assertEquals(Notifier.BRIEFS_CHANNEL, notification.channelId)
    }

    @Test
    fun `the lock screen version carries no title, name or fact`() {
        val notification = BriefNotifications.build(app, brief, ZoneOffset.UTC)

        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        val public = notification.publicVersion
        assertNotNull(public)
        assertEquals("A meeting brief is ready", public.extra(Notification.EXTRA_TITLE))
        val shown = listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT)
        shown.mapNotNull { public.extra(it) }.forEach {
            assertFalse(it, it.contains("Olena") || it.contains("Lunch") || it.contains("photos"))
        }
    }

    @Test
    fun `a tap opens the app`() {
        val notification = BriefNotifications.build(app, brief, ZoneOffset.UTC)

        assertTrue(
            shadowOf(notification.contentIntent)
                .savedIntent.component!!
                .className
                .endsWith("MainActivity"),
        )
    }

    @Test
    fun `the id comes from the brief id`() {
        val other = brief.copy(brief = BriefText("b2", "x"))

        assertEquals(BriefNotifications.id(brief), BriefNotifications.id(brief.copy(title = "Renamed")))
        assertNotEquals(BriefNotifications.id(brief), BriefNotifications.id(other))
    }

    @Test
    fun `posts through the briefs channel only with the permission`() {
        Notifier.createChannels(app)
        val manager = app.getSystemService(NotificationManager::class.java)
        assertEquals("Meeting briefs", manager.getNotificationChannel(Notifier.BRIEFS_CHANNEL).name.toString())

        shadowOf(app).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(BriefNotifications.post(app, brief, ZoneOffset.UTC))
        assertEquals(0, shadowOf(manager).size())

        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(BriefNotifications.post(app, brief, ZoneOffset.UTC))
        assertEquals(1, shadowOf(manager).size())
    }
}
