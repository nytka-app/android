package io.github.nytka_app.pendant

/** What the Limitless classes need from the Bluetooth link; [LimitlessPendant] and [FakeLimitless] implement it. */
interface LimitlessLink {
    /** Writes one command to the TX characteristic (`632de002`), with response; false if the write failed. */
    suspend fun write(value: ByteArray): Boolean
}

/** Numbers the commands of one app run: each wrapper carries a message index and each request an id. */
class LimitlessCommands {
    private var index = 0
    private var request = 0L

    @Synchronized
    private fun <T> next(build: (Int, Long) -> T): T = build(index++, ++request)

    fun setCurrentTime(epochMs: Long) = next { i, r -> LimitlessProtocol.setCurrentTime(i, r, epochMs) }

    fun setMode(mode: LimitlessMode) = next { i, r -> LimitlessProtocol.setMode(i, r, mode) }

    fun acknowledge(upToIndex: Long) = next { i, r -> LimitlessProtocol.acknowledge(i, r, upToIndex) }

    fun getStatus() = next { i, r -> LimitlessProtocol.getStatus(i, r) }

    fun setLed(percent: Int) = next { i, r -> LimitlessProtocol.setLed(i, r, percent) }
}
