package io.github.nytka_app.pendant

import android.bluetooth.BluetoothGatt

/**
 * A readable name for a GATT or link status code, to read beside the raw number in the log.
 * [BluetoothGatt] names its own operation statuses; the rest are link-layer codes Android's GATT stack
 * reports to `onConnectionStateChange` but never names. 147 is a link that went silent, as [LinkStats]
 * documents. Unknown codes return null, so the log keeps just the number.
 */
object GattStatus {
    fun name(status: Int): String? =
        when (status) {
            BluetoothGatt.GATT_SUCCESS -> "GATT_SUCCESS"
            BluetoothGatt.GATT_READ_NOT_PERMITTED -> "GATT_READ_NOT_PERMITTED"
            BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> "GATT_WRITE_NOT_PERMITTED"
            BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION -> "GATT_INSUFFICIENT_AUTHENTICATION"
            BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED -> "GATT_REQUEST_NOT_SUPPORTED"
            BluetoothGatt.GATT_INVALID_OFFSET -> "GATT_INVALID_OFFSET"
            BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> "GATT_INVALID_ATTRIBUTE_LENGTH"
            BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION -> "GATT_INSUFFICIENT_ENCRYPTION"
            BluetoothGatt.GATT_CONNECTION_CONGESTED -> "GATT_CONNECTION_CONGESTED"
            BluetoothGatt.GATT_FAILURE -> "GATT_FAILURE"
            CONN_L2C_FAILURE -> "GATT_CONN_L2C_FAILURE"
            CONN_TIMEOUT -> "GATT_CONN_TIMEOUT"
            CONN_TERMINATE_PEER_USER -> "GATT_CONN_TERMINATE_PEER_USER"
            CONN_TERMINATE_LOCAL_HOST -> "GATT_CONN_TERMINATE_LOCAL_HOST"
            CONN_LMP_TIMEOUT -> "GATT_CONN_LMP_TIMEOUT"
            CONN_FAIL_ESTABLISH -> "GATT_CONN_FAIL_ESTABLISH"
            CONN_ERROR -> "GATT_ERROR"
            CONN_WENT_SILENT -> "LINK_WENT_SILENT"
            else -> null
        }

    // Link-layer disconnect reasons Android's GATT stack reports but android.bluetooth.BluetoothGatt
    // never names (not covered by its own GATT_* constants above).
    private const val CONN_L2C_FAILURE = 1
    private const val CONN_TIMEOUT = 8
    private const val CONN_TERMINATE_PEER_USER = 19
    private const val CONN_TERMINATE_LOCAL_HOST = 22
    private const val CONN_LMP_TIMEOUT = 34
    private const val CONN_FAIL_ESTABLISH = 62
    private const val CONN_ERROR = 133
    private const val CONN_WENT_SILENT = 147
}
