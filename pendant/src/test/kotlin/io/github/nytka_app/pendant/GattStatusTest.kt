package io.github.nytka_app.pendant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GattStatusTest {
    @Test
    fun `names the operation statuses BluetoothGatt itself defines`() {
        assertEquals("GATT_SUCCESS", GattStatus.name(0))
        assertEquals("GATT_READ_NOT_PERMITTED", GattStatus.name(2))
        assertEquals("GATT_WRITE_NOT_PERMITTED", GattStatus.name(3))
        assertEquals("GATT_INSUFFICIENT_AUTHENTICATION", GattStatus.name(5))
        assertEquals("GATT_REQUEST_NOT_SUPPORTED", GattStatus.name(6))
        assertEquals("GATT_INVALID_OFFSET", GattStatus.name(7))
        assertEquals("GATT_INVALID_ATTRIBUTE_LENGTH", GattStatus.name(13))
        assertEquals("GATT_INSUFFICIENT_ENCRYPTION", GattStatus.name(15))
        assertEquals("GATT_CONNECTION_CONGESTED", GattStatus.name(143))
        assertEquals("GATT_FAILURE", GattStatus.name(257))
    }

    @Test
    fun `names the link-layer disconnect reasons, 147 included`() {
        assertEquals("GATT_CONN_L2C_FAILURE", GattStatus.name(1))
        assertEquals("GATT_CONN_TIMEOUT", GattStatus.name(8))
        assertEquals("GATT_CONN_TERMINATE_PEER_USER", GattStatus.name(19))
        assertEquals("GATT_CONN_TERMINATE_LOCAL_HOST", GattStatus.name(22))
        assertEquals("GATT_CONN_LMP_TIMEOUT", GattStatus.name(34))
        assertEquals("GATT_CONN_FAIL_ESTABLISH", GattStatus.name(62))
        assertEquals("GATT_ERROR", GattStatus.name(133))
        assertEquals("LINK_WENT_SILENT", GattStatus.name(147))
    }

    @Test
    fun `an unknown code names nothing, so the log keeps just the number`() {
        assertNull(GattStatus.name(-1))
        assertNull(GattStatus.name(9_999))
    }
}
