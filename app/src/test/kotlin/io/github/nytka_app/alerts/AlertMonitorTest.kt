package io.github.nytka_app.alerts

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertMonitorTest {
    private val monitor = AlertMonitor()
    private val minute = 60_000L

    private fun inputs(
        running: Boolean = true,
        disconnectedSinceMs: Long? = null,
        unreachableSinceMs: Long? = null,
        battery: Int? = 80,
        queueFraction: Double = 0.0,
        disconnectedAfterMinutes: Int = 5,
    ) = AlertInputs(
        running,
        disconnectedSinceMs,
        unreachableSinceMs,
        battery,
        queueFraction,
        disconnectedAfterMinutes,
        15,
        20,
    )

    private fun started(
        inputs: AlertInputs,
        now: Long,
    ) = monitor.evaluate(inputs, now).started

    @Test
    fun `pendant away fires once after five minutes and again after it came back`() {
        assertEquals(emptyList<Alert>(), started(inputs(disconnectedSinceMs = 0), 5 * minute - 1))
        assertEquals(listOf(Alert.PendantAway), started(inputs(disconnectedSinceMs = 0), 5 * minute))
        assertEquals(emptyList<Alert>(), started(inputs(disconnectedSinceMs = 0), 60 * minute))

        assertEquals(listOf(Alert.PendantAway), monitor.evaluate(inputs(), 61 * minute).cleared)
        assertEquals(listOf(Alert.PendantAway), started(inputs(disconnectedSinceMs = 70 * minute), 75 * minute))
    }

    @Test
    fun `server unreachable after fifteen minutes`() {
        assertEquals(emptyList<Alert>(), started(inputs(unreachableSinceMs = 0), 14 * minute))
        assertEquals(listOf(Alert.ServerUnreachable), started(inputs(unreachableSinceMs = 0), 15 * minute))
    }

    @Test
    fun `battery alert at twenty percent, once per discharge`() {
        val fired = listOf(21, 20, 15, 22, 26, 20).map { started(inputs(battery = it), 0) }

        assertEquals(
            listOf(
                emptyList(),
                listOf(Alert.BatteryLow),
                emptyList(),
                emptyList(),
                emptyList(),
                listOf(Alert.BatteryLow),
            ),
            fired,
        )
    }

    @Test
    fun `queue alert fires once past 80 percent`() {
        val fired = listOf(0.79, 0.8, 0.95, 0.77, 0.7, 0.85).map { started(inputs(queueFraction = it), 0) }

        assertEquals(
            listOf(
                emptyList(),
                listOf(Alert.QueueFilling),
                emptyList(),
                emptyList(),
                emptyList(),
                listOf(Alert.QueueFilling),
            ),
            fired,
        )
    }

    @Test
    fun `thresholds come from the settings`() {
        assertEquals(
            listOf(Alert.PendantAway),
            started(inputs(disconnectedSinceMs = 0, disconnectedAfterMinutes = 1), minute),
        )
    }

    @Test
    fun `no pendant alert while capture is not running`() {
        assertEquals(emptyList<Alert>(), started(inputs(running = false, disconnectedSinceMs = 0), 60 * minute))
    }

    @Test
    fun `texts name the threshold`() {
        assertEquals(
            "The pendant has been disconnected for 5 minutes.",
            AlertNotifications.text(Alert.PendantAway, inputs()),
        )
        assertEquals("The pendant battery is at 18%.", AlertNotifications.text(Alert.BatteryLow, inputs(battery = 18)))
    }
}
