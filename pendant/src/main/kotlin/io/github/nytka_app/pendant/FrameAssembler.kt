package io.github.nytka_app.pendant

import java.io.ByteArrayOutputStream

/** One reassembled Opus frame and the phone's wall-clock time when its last fragment arrived. */
class AudioFrame(
    val payload: ByteArray,
    val capturedAtMs: Long,
)

/**
 * Turns audio notifications back into Opus frames. Each notification starts with a u16
 * little-endian counter that rises by one per notification (65535 wraps to 0) and a fragment
 * index that restarts at 0 for each frame (firmware `push_to_gatt`). Fragment 0 completes the
 * previous frame. A jump in the counter or the fragment index means lost notifications: the
 * partial frame is dropped and assembly waits for the next fragment 0.
 *
 * Not thread-safe; the Bluetooth callback thread owns it.
 */
class FrameAssembler(
    private val now: () -> Long = System::currentTimeMillis,
) {
    var notifications = 0L
        private set
    var lostNotifications = 0L
        private set
    var droppedFrames = 0L
        private set

    private val partial = ByteArrayOutputStream()
    private var assembling = false
    private var lastFragmentAtMs = 0L
    private var lastCounter = NONE
    private var lastFragment = NONE

    fun accept(notification: ByteArray): AudioFrame? {
        if (notification.size < HEADER_SIZE) return null
        notifications++

        val counter = (notification[0].toInt() and 0xFF) or ((notification[1].toInt() and 0xFF) shl 8)
        val fragment = notification[2].toInt() and 0xFF
        val arrivedAtMs = now()

        val inStep = lastCounter == NONE || counter == (lastCounter + 1) and 0xFFFF
        if (!inStep) lostNotifications += (counter - lastCounter - 1) and 0xFFFF
        lastCounter = counter

        var completed: AudioFrame? = null
        when {
            fragment == 0 -> {
                if (inStep) completed = take() else drop()
                start(notification, arrivedAtMs)
            }

            inStep && assembling && fragment == lastFragment + 1 -> append(notification, arrivedAtMs)
            else -> drop()
        }
        lastFragment = fragment
        return completed
    }

    fun reset() {
        partial.reset()
        assembling = false
        lastCounter = NONE
        lastFragment = NONE
    }

    private fun start(
        notification: ByteArray,
        arrivedAtMs: Long,
    ) {
        partial.reset()
        assembling = true
        append(notification, arrivedAtMs)
    }

    private fun append(
        notification: ByteArray,
        arrivedAtMs: Long,
    ) {
        partial.write(notification, HEADER_SIZE, notification.size - HEADER_SIZE)
        lastFragmentAtMs = arrivedAtMs
    }

    private fun take(): AudioFrame? {
        if (!assembling || partial.size() == 0) return null
        val frame = AudioFrame(partial.toByteArray(), lastFragmentAtMs)
        partial.reset()
        assembling = false
        return frame
    }

    private fun drop() {
        if (assembling) droppedFrames++
        partial.reset()
        assembling = false
    }

    private companion object {
        const val HEADER_SIZE = 3
        const val NONE = -1
    }
}
