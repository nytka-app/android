package io.github.nytka_app.ui.developer.server

import androidx.test.core.app.ApplicationProvider
import io.github.nytka_app.core.api.Delivery
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeliveryDetailTest {
    private val resources = ApplicationProvider.getApplicationContext<android.app.Application>().resources

    @Test
    fun `a delivery reads as attempts, outcome and time`() {
        assertEquals(
            "6 attempts · HTTP 500 · t",
            deliveryDetail(resources, Delivery("d", "ping", "failed", 6, 500, null, "t", null)),
        )
        assertEquals(
            "1 attempt · timeout · done",
            deliveryDetail(resources, Delivery("d", "ping", "failed", 1, null, "timeout", "t", "done")),
        )
    }
}
