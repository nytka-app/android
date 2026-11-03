package io.github.nytka_app.pendant

import org.junit.Assert.assertEquals
import org.junit.Test

class RecorderModelTest {
    @Test
    fun `a missing model reads as Omi`() {
        assertEquals(RecorderModel.Omi, RecorderModel.of(null))
    }

    @Test
    fun `a stored id reads back as its model and an unknown one as Omi`() {
        assertEquals(RecorderModel.Omi, RecorderModel.of(RecorderModel.Omi.id))
        assertEquals(RecorderModel.Omi, RecorderModel.of("a-model-from-a-newer-app"))
    }

    @Test
    fun `Omi pairs on its audio service`() {
        assertEquals(OmiUuids.AUDIO_SERVICE, RecorderModel.Omi.serviceUuid)
    }

    @Test
    fun `Limitless reads back from its id and pairs on its own service`() {
        assertEquals(RecorderModel.Limitless, RecorderModel.of(RecorderModel.Limitless.id))
        assertEquals(LimitlessUuids.SERVICE, RecorderModel.Limitless.serviceUuid)
    }

    @Test
    fun `Limitless keeps its audio on the phone until the app says so`() {
        val capabilities = RecorderModel.Limitless.capabilities
        assertEquals(StorageKind.Custody, capabilities.storage)
        assertEquals(setOf(ButtonEvent.DoubleTap), capabilities.buttons)
        assertEquals(false, capabilities.haptic)
        assertEquals(true, capabilities.audioSwitch)
    }
}
