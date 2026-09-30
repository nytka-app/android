package io.github.nytka_app.capture

import io.github.nytka_app.pendant.Pendant
import io.github.nytka_app.pendant.PendantConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the Device tab shows of the pendant's two settings. A value is null while the pendant does not report the
 * setting, has not answered yet, or is not connected; then the screen shows no slider for it.
 */
data class PendantSettingsState(
    /** LED brightness in percent. */
    val led: Int? = null,
    /** Microphone gain level, 0 to 8. */
    val gain: Int? = null,
    /** Counts failed writes, so a slider that moved can go back to the pendant's real value. */
    val failures: Int = 0,
    val error: String? = null,
)

/** What the Device tab asks of the pendant's settings; each commit is one finished slider gesture. */
interface PendantSettingsControls {
    val state: StateFlow<PendantSettingsState>

    fun commitLed(percent: Int)

    fun commitGain(level: Int)
}

/**
 * Turns finished slider gestures into pendant writes. The firmware saves each write to flash, so a control is
 * written at most once per [minIntervalMs]: a value committed inside that window waits for its end, and only the
 * last one is written. A failed write leaves the pendant's value as it was, says so, and never blocks the next one.
 */
class PendantSettingsController(
    private val pendant: Pendant,
    private val scope: CoroutineScope,
    private val minIntervalMs: Long = MIN_INTERVAL_MS,
) : PendantSettingsControls {
    private val settings = pendant.settings
    private val failure = MutableStateFlow(Failure())

    override val state: StateFlow<PendantSettingsState> =
        combine(pendant.connection, settings.support, settings.values, failure) { connection, support, values, failed ->
            val connected = connection is PendantConnection.Connected
            PendantSettingsState(
                led = values.led?.takeIf { connected && support.led },
                gain = values.gain?.takeIf { connected && support.gain },
                failures = failed.count,
                error = failed.message,
            )
        }.stateIn(scope, SharingStarted.Eagerly, PendantSettingsState())

    private val led = Debounced(LED_FAILED) { settings.setLed(it) }
    private val gain = Debounced(GAIN_FAILED) { settings.setGain(it) }

    override fun commitLed(percent: Int) = led.submit(percent)

    override fun commitGain(level: Int) = gain.submit(level)

    private data class Failure(
        val count: Int = 0,
        val message: String? = null,
    )

    private inner class Debounced(
        private val failedMessage: String,
        private val write: suspend (Int) -> Boolean,
    ) {
        private var wanted: Int? = null
        private var job: Job? = null

        fun submit(value: Int) {
            wanted = value
            if (job?.isActive == true) return
            job =
                scope.launch {
                    while (true) {
                        val next = wanted ?: break
                        wanted = null
                        if (write(next)) {
                            failure.update { it.copy(message = null) }
                        } else {
                            failure.update { Failure(it.count + 1, failedMessage) }
                        }
                        delay(minIntervalMs)
                    }
                }
        }
    }

    companion object {
        const val MIN_INTERVAL_MS = 2_000L
        const val LED_FAILED = "Could not save the LED brightness on the pendant."
        const val GAIN_FAILED = "Could not save the microphone gain on the pendant."
    }
}
