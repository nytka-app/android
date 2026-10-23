package io.github.nytka_app.phonemic

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the phone microphone is doing. It is [Off] whenever the service is not running: nothing is kept on disk. */
sealed interface PhoneMicStatus {
    data object Off : PhoneMicStatus

    data object Recording : PhoneMicStatus

    /** The mute switch or a mute window holds it; it reads nothing until that ends. */
    data object Paused : PhoneMicStatus

    /** It stopped by itself; [reason] is a fixed sentence or the system's own message. */
    data class Failed(
        val reason: String,
    ) : PhoneMicStatus
}

/** The one place the service reports to, so the Device screen follows it. */
object PhoneMicState {
    private val mutable = MutableStateFlow<PhoneMicStatus>(PhoneMicStatus.Off)
    val status: StateFlow<PhoneMicStatus> = mutable.asStateFlow()

    internal fun set(status: PhoneMicStatus) {
        mutable.value = status
    }
}

/** What the Device screen asks of the phone microphone; a fake in tests. */
interface PhoneMicControls {
    val status: StateFlow<PhoneMicStatus>

    /** Whether this phone has the Opus encoder the chunk format needs. */
    fun available(): Boolean

    /** Only from a visible screen, after the permission: Android refuses a microphone service from the background. */
    fun start()

    fun stop()
}

class AndroidPhoneMicControls(
    private val context: Context,
) : PhoneMicControls {
    override val status = PhoneMicState.status

    override fun available() = OpusEncoder.available()

    override fun start() {
        ContextCompat.startForegroundService(context, Intent(context, PhoneMicService::class.java))
    }

    override fun stop() {
        context.stopService(Intent(context, PhoneMicService::class.java))
    }
}
