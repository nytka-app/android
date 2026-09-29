package io.github.nytka_app.capture

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.ParcelUuid
import io.github.nytka_app.pendant.OmiUuids

data class PairedPendant(
    val address: String,
    val name: String,
)

/**
 * Pairing through the system's companion-device chooser, filtered on the Omi audio service. An
 * association lets the app see the pendant come into range and start capture from the background.
 */
class CompanionPairing(
    context: Context,
) {
    private val manager = context.getSystemService(CompanionDeviceManager::class.java)
    private val executor = context.mainExecutor

    fun request(): AssociationRequest =
        AssociationRequest
            .Builder()
            .addDeviceFilter(
                BluetoothLeDeviceFilter
                    .Builder()
                    .setScanFilter(ScanFilter.Builder().setServiceUuid(ParcelUuid(OmiUuids.AUDIO_SERVICE)).build())
                    .build(),
            ).build()

    /** Opens the chooser through [onChooser] (launch it with an IntentSenderRequest). */
    fun associate(
        onChooser: (IntentSender) -> Unit,
        onError: (String) -> Unit,
    ) {
        val callback =
            object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) = onChooser(intentSender)

                @Deprecated("Called before Android 13 only")
                override fun onDeviceFound(intentSender: IntentSender) = onChooser(intentSender)

                override fun onFailure(error: CharSequence?) = onError(error?.toString() ?: "Pairing failed.")
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.associate(request(), executor, callback)
        } else {
            @Suppress("DEPRECATION")
            manager.associate(request(), callback, null)
        }
    }

    /** Reads the chosen pendant from the chooser's result and starts watching for it. */
    fun complete(data: Intent?): PairedPendant? {
        val paired =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val association =
                    data?.getParcelableExtra(CompanionDeviceManager.EXTRA_ASSOCIATION, AssociationInfo::class.java)
                association?.let { info ->
                    info.deviceMacAddress?.toString()?.uppercase()?.let {
                        PairedPendant(
                            it,
                            info.displayName?.toString() ?: "Omi",
                        )
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                data?.getParcelableExtra<ScanResult>(CompanionDeviceManager.EXTRA_DEVICE)?.device?.let {
                    PairedPendant(it.address, nameOf(it))
                }
            }
        paired?.let { observe(it.address) }
        return paired
    }

    /** Before Android 13 reading the name needs BLUETOOTH_CONNECT, which the user may not have granted yet. */
    private fun nameOf(device: BluetoothDevice): String =
        try {
            device.name
        } catch (_: SecurityException) {
            null
        } ?: "Omi"

    /**
     * Idempotent; also called after a reboot. Deprecated on Android 16 in favour of a request
     * object, and still honoured.
     */
    @Suppress("DEPRECATION")
    fun observe(address: String) = manager.startObservingDevicePresence(address)

    @Suppress("DEPRECATION")
    fun forget(address: String) {
        runCatching { manager.stopObservingDevicePresence(address) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.myAssociations
                .firstOrNull { it.deviceMacAddress?.toString().equals(address, ignoreCase = true) }
                ?.let { manager.disassociate(it.id) }
        } else {
            manager.disassociate(address)
        }
    }
}
