package io.github.nytka_app.alerts

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlertNotificationsTest {
    private val resources = ApplicationProvider.getApplicationContext<android.app.Application>().resources

    private fun inputs(
        battery: Int? = 80,
        disconnectedAfterMinutes: Int = 5,
        unreachableAfterMinutes: Int = 15,
    ) = AlertInputs(true, null, null, battery, 0.0, disconnectedAfterMinutes, unreachableAfterMinutes, 20)

    @Test
    fun `texts name the threshold`() {
        assertEquals(
            "The pendant has been disconnected for 5 minutes.",
            AlertNotifications.text(resources, Alert.PendantAway, inputs()),
        )
        assertEquals(
            "The pendant has been disconnected for 1 minute.",
            AlertNotifications.text(resources, Alert.PendantAway, inputs(disconnectedAfterMinutes = 1)),
        )
        assertEquals(
            "The server has been unreachable for 15 minutes. Audio waits in the queue.",
            AlertNotifications.text(resources, Alert.ServerUnreachable, inputs()),
        )
        assertEquals(
            "The pendant battery is at 18%.",
            AlertNotifications.text(resources, Alert.BatteryLow, inputs(battery = 18)),
        )
    }
}
