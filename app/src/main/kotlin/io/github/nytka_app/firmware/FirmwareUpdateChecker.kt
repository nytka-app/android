package io.github.nytka_app.firmware

import io.github.nytka_app.core.settings.SettingsSource
import io.github.nytka_app.pendant.FirmwareStream
import io.github.nytka_app.pendant.FirmwareVersion
import io.github.nytka_app.pendant.PendantInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration

/** A published firmware newer than the one the pendant runs. Nytka only tells; it never downloads or flashes. */
data class FirmwareNotice(
    val current: FirmwareVersion,
    val latest: FirmwareVersion,
    /** Omi's own instructions for updating the pendant. */
    val instructionsUrl: String = FirmwareUpdateChecker.INSTRUCTIONS_URL,
)

/** What the Device tab asks for once a pendant is connected; null means nothing to tell. */
fun interface FirmwareNotices {
    suspend fun notice(info: PendantInfo): FirmwareNotice?
}

/**
 * Asks [releases] for the newest firmware at most once every [CHECK_INTERVAL], and only while the setting is on and
 * the pendant maps to a [FirmwareStream]. The newest known tag is kept in the settings, so a notice shows without a
 * request in between. An answer from GitHub, even an error, uses up the day (a rate limit must not be hammered); a
 * failure to reach it does not, but is not retried within [RETRY_AFTER_UNREACHABLE].
 */
class FirmwareUpdateChecker(
    private val settings: SettingsSource,
    private val releases: FirmwareReleases,
    private val clock: Clock,
) : FirmwareNotices {
    private val lock = Mutex()
    private var unreachableAt: Long? = null

    override suspend fun notice(info: PendantInfo): FirmwareNotice? =
        lock.withLock {
            val saved = settings.current()
            if (!saved.firmwareCheck) return null
            val stream = FirmwareStream.of(info.model, info.firmware) ?: return null
            val current = FirmwareVersion.parse(info.firmware) ?: return null
            val now = clock.millis()
            if (isDue(saved.firmwareCheckedAt, now) && !recentlyUnreachable(now)) {
                when (val answer = releases.latest(stream)) {
                    is ReleaseAnswer.Latest -> {
                        unreachableAt = null
                        settings.update {
                            it.copy(firmwareCheckedAt = now, firmwareLatest = stream.tagPrefix + answer.version)
                        }
                    }

                    ReleaseAnswer.NoRelease -> {
                        unreachableAt = null
                        settings.update { it.copy(firmwareCheckedAt = now) }
                    }

                    ReleaseAnswer.Unreachable -> unreachableAt = now
                }
            }
            val latest = settings.current().firmwareLatest?.let(stream::versionOf)
            latest?.takeIf { it > current }?.let { FirmwareNotice(current, it) }
        }

    private fun recentlyUnreachable(now: Long) =
        unreachableAt?.let { now - it in 0 until RETRY_AFTER_UNREACHABLE.toMillis() } ?: false

    companion object {
        const val INSTRUCTIONS_URL = "https://docs.omi.me/doc/get_started/Flash_device"
        val CHECK_INTERVAL: Duration = Duration.ofHours(24)
        val RETRY_AFTER_UNREACHABLE: Duration = Duration.ofHours(1)

        /** Due when never checked, a day has passed, or the clock moved back past the last check. */
        fun isDue(
            checkedAt: Long,
            now: Long,
        ) = checkedAt <= 0 || now < checkedAt || now - checkedAt >= CHECK_INTERVAL.toMillis()
    }
}
