package io.github.nytka_app.pendant

import io.github.nytka_app.pendant.LimitlessFixtures.TEN_MS
import io.github.nytka_app.pendant.LimitlessFixtures.liveMessage
import io.github.nytka_app.pendant.LimitlessFixtures.opus
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LimitlessLiveAudioTest {
    private val live = LimitlessLiveAudio { NOW }

    private fun LimitlessLiveAudio.acceptAll(notifications: List<ByteArray>) = notifications.flatMap { accept(it) }

    @Test
    fun `a message of three frames is stamped back from its arrival time`() {
        val frames = listOf(opus(fill = 1), opus(fill = 2), opus(fill = 3))
        val out = live.acceptAll(liveMessage(frames))
        assertEquals(listOf(NOW - 40, NOW - 20, NOW), out.map { it.capturedAtMs })
        assertArrayEquals(frames[0], out[0].payload)
    }

    @Test
    fun `nothing comes out until the last fragment`() {
        val parts = liveMessage(List(6) { opus(fill = it) }, partBytes = 60)
        assertEquals(true, parts.size > 2)
        for (part in parts.dropLast(1)) assertEquals(emptyList<AudioFrame>(), live.accept(part))
        assertEquals(6, live.accept(parts.last()).size)
    }

    @Test
    fun `10 ms packets are paired across messages`() {
        assertEquals(0, live.acceptAll(liveMessage(listOf(opus(TEN_MS)), index = 1)).size)
        val out = live.acceptAll(liveMessage(listOf(opus(TEN_MS)), index = 2))
        assertEquals(1, out.size)
        assertEquals(0, live.dropped)
    }

    @Test
    fun `reset forgets a half joined message and a waiting packet`() {
        val parts = liveMessage(List(6) { opus(fill = it) }, partBytes = 60)
        live.accept(parts.first())
        live.acceptAll(liveMessage(listOf(opus(TEN_MS)), index = 5))
        live.reset()
        assertEquals(1, live.dropped)
        for (part in parts.drop(1)) assertEquals(emptyList<AudioFrame>(), live.accept(part))
    }

    @Test
    fun `a notification that is no audio gives no frames`() {
        assertEquals(emptyList<AudioFrame>(), live.accept(ByteArray(0)))
        assertEquals(
            emptyList<AudioFrame>(),
            live.accept(LimitlessMessages.notifications(1, LimitlessMessages.doublePress(), 200).single()),
        )
    }

    private companion object {
        const val NOW = 50_000L
    }
}
