package io.github.nytka_app.pendant

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ReconnectTest {
    @Test
    fun `audio intent survives everything except an explicit clear`() {
        val intent = AudioIntent()
        assertFalse(intent.wanted)
        intent.set(true)
        assertTrue(intent.wanted) // a lost link does not touch it
        intent.set(false)
        assertFalse(intent.wanted) // muted: a reconnect must not resubscribe
        intent.set(true)
        intent.clear()
        assertFalse(intent.wanted)
    }

    @Test
    fun `a connect attempt that never completes is retried and re-armed`() =
        runTest {
            var retries = 0
            lateinit var timeout: ConnectTimeout
            timeout =
                ConnectTimeout(backgroundScope, 30_000, isConnecting = { true }) {
                    retries++
                    timeout.arm() // what open() does
                }
            timeout.arm()

            advanceTimeBy(29_999)
            assertEquals(0, retries)
            advanceTimeBy(2)
            assertEquals(1, retries)
            advanceTimeBy(30_000)
            assertEquals(2, retries)
        }

    @Test
    fun `no retry once connected or cancelled`() =
        runTest {
            var retries = 0
            var connecting = true
            val timeout = ConnectTimeout(backgroundScope, 30_000, isConnecting = { connecting }) { retries++ }
            timeout.arm()
            connecting = false
            advanceTimeBy(31_000)
            assertEquals(0, retries)

            connecting = true
            timeout.arm()
            timeout.cancel()
            advanceTimeBy(31_000)
            assertEquals(0, retries)
        }

    @Test
    fun `a late callback does not complete a different operation`() {
        val uuid = UUID.randomUUID()
        val op = PendingOperation(OperationKind.DescriptorWrite, uuid)

        assertFalse(op.complete(OperationKind.Read, uuid, byteArrayOf()))
        assertFalse(op.complete(OperationKind.DescriptorWrite, UUID.randomUUID(), byteArrayOf()))
        assertFalse(op.done.isCompleted)

        assertTrue(op.complete(OperationKind.DescriptorWrite, uuid, null))
        assertNull(op.done.getCompleted())
    }

    @Test
    fun `addresses are logged by their tail only`() {
        assertEquals("…EE:FF", redactAddress("AA:BB:CC:DD:EE:FF"))
    }
}
