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
}
