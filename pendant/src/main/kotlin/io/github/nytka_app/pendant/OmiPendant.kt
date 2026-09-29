package io.github.nytka_app.pendant

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * The Omi pendant over Bluetooth Low Energy. Callers hold BLUETOOTH_CONNECT: first run asks for
 * it before pairing, and the capture service only starts for a paired pendant.
 */
@SuppressLint("MissingPermission")
class OmiPendant(
    private val context: Context,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) : Pendant {
    private val mutableConnection = MutableStateFlow<PendantConnection>(PendantConnection.Disconnected)
    private val mutableBattery = MutableStateFlow<Int?>(null)
    private val mutableStats = MutableStateFlow(LinkStats())
    private val mutableFrames = MutableSharedFlow<AudioFrame>(extraBufferCapacity = 1024)
    private val mutableButtons = MutableSharedFlow<ButtonEvent>(extraBufferCapacity = 8)

    override val connection: StateFlow<PendantConnection> = mutableConnection
    override val battery: StateFlow<Int?> = mutableBattery
    override val stats: StateFlow<LinkStats> = mutableStats
    override val frames: Flow<AudioFrame> = mutableFrames
    override val buttons: Flow<ButtonEvent> = mutableButtons

    private val assembler = FrameAssembler(now)
    private val operations = Mutex()

    /** Serialises open, close, connect and disconnect: the retry runs on the scope, disconnect on the caller. */
    private val link = Any()
    private val connectTimeout =
        ConnectTimeout(
            scope,
            CONNECT_TIMEOUT_MS,
            isConnecting = { address != null && mutableConnection.value == PendantConnection.Connecting },
            retry = {
                Log.w(TAG, "connect timeout after ${CONNECT_TIMEOUT_MS}ms, opening a new client")
                synchronized(link) {
                    close()
                    open()
                }
            },
        )

    /** Serialises mute and the watchdog's resubscribe, so a resubscribe cannot undo a mute. */
    private val audioLock = Mutex()

    @Volatile private var pending: PendingOperation? = null

    @Volatile private var gatt: BluetoothGatt? = null

    @Volatile private var address: String? = null

    private val audioIntent = AudioIntent()

    @Volatile private var lastAudioAtMs = 0L
    private var watchingAdapter = false
    private var emittedFrames = 0L
    private var overflowFrames = 0L
    private var session: Job? = null

    override fun connect(address: String) =
        synchronized(link) {
            close() // a second connect must not leave the first GATT client feeding the same callback
            this.address = address
            mutableConnection.value = PendantConnection.Connecting
            watchAdapter(true)
            open()
        }

    override fun disconnect() =
        synchronized(link) {
            Log.i(TAG, "disconnect requested")
            address = null
            audioIntent.clear()
            connectTimeout.cancel()
            watchAdapter(false)
            close()
            mutableConnection.value = PendantConnection.Disconnected
        }

    override suspend fun setAudio(enabled: Boolean) =
        audioLock.withLock {
            audioIntent.set(enabled)
            val current = gatt ?: return@withLock
            if (mutableConnection.value !is PendantConnection.Connected) return@withLock
            subscribe(current, OmiUuids.AUDIO_DATA, enabled)
            synchronized(assembler) { assembler.reset() }
            lastAudioAtMs = now()
        }

    override suspend fun buzz(haptic: Haptic) {
        val current = gatt ?: return
        val characteristic = find(current, OmiUuids.HAPTIC) ?: return
        operation(OperationKind.Write, OmiUuids.HAPTIC) { write(current, characteristic, byteArrayOf(haptic.code)) }
    }

    private fun open() =
        synchronized(link) {
            openLocked()
        }

    private fun openLocked() {
        val target = address ?: return // re-checked under the lock: disconnect() may have won
        val adapter = context.getSystemService(BluetoothManager::class.java).adapter
        if (!adapter.isEnabled) {
            Log.i(TAG, "open: Bluetooth is off, waiting for STATE_ON")
            return // adapterState opens a client once Bluetooth is back on
        }
        releaseGatt() // two callers can reach open(); never leave the first client alive
        Log.i(TAG, "open: connectGatt ${redactAddress(target)}")
        gatt = adapter.getRemoteDevice(target).connectGatt(context, true, callback, BluetoothDevice.TRANSPORT_LE)
        if (gatt == null) reopenLater() else connectTimeout.arm()
    }

    private fun reopenLater() {
        scope.launch {
            delay(RECONNECT_DELAY_MS)
            synchronized(link) { if (address != null && gatt == null) open() }
        }
    }

    /** Bluetooth came back on: every GATT client died with the adapter, so start a fresh one. */
    private val adapterState =
        object : BroadcastReceiver() {
            override fun onReceive(
                receiver: Context,
                intent: Intent,
            ) {
                if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) !=
                    BluetoothAdapter.STATE_ON
                ) {
                    return
                }
                if (address == null) return
                Log.i(TAG, "adapter STATE_ON, opening a fresh client")
                close()
                mutableConnection.value = PendantConnection.Connecting
                open()
            }
        }

    private fun watchAdapter(on: Boolean) {
        if (on == watchingAdapter) return
        watchingAdapter = on
        if (!on) {
            context.unregisterReceiver(adapterState)
            return
        }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        // The Bluetooth app sends this broadcast, not the system UID, so a not-exported receiver would
        // miss it; it is a protected broadcast, so no other app can send it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(adapterState, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(adapterState, filter)
        }
    }

    /** Drops the link and its state. The audio intent stays: only [disconnect] clears it. */
    private fun close() {
        session?.cancel()
        pending?.done?.complete(null)
        releaseGatt()
        synchronized(assembler) { assembler.reset() }
    }

    private fun releaseGatt() {
        gatt?.let {
            it.disconnect()
            it.close()
        }
        gatt = null
    }

    /** Runs after every connection: MTU, services, codec check, device info, subscriptions. */
    private suspend fun setUp(current: BluetoothGatt) {
        operation(OperationKind.Mtu) { current.requestMtu(MTU) }
        Log.i(TAG, "setUp: mtu requested")
        // A failed discovery or codec read is transient: disconnecting makes the reconnect path try again.
        if (operation(OperationKind.Services) { current.discoverServices() } == null) {
            Log.w(TAG, "setUp: service discovery failed")
            return current.disconnect()
        }
        Log.i(TAG, "setUp: services discovered")
        val rawCodec =
            read(current, OmiUuids.AUDIO_CODEC) ?: run {
                Log.w(TAG, "setUp: codec read failed")
                return current.disconnect()
            }

        val codec = OmiParsing.codec(rawCodec)
        Log.i(TAG, "setUp: codec $codec")
        if (codec != OPUS_FS320) {
            mutableConnection.value =
                PendantConnection.Refused(
                    "The pendant sends audio codec ${codec ?: "unknown"}; Nytka needs Opus FS320 (21). " +
                        "Update the pendant's firmware with the official Omi app.",
                )
            return
        }

        val info =
            PendantInfo(
                name = current.device.name ?: "Omi",
                model = OmiParsing.text(read(current, OmiUuids.MODEL_NUMBER)),
                firmware = OmiParsing.text(read(current, OmiUuids.FIRMWARE_REVISION)),
                hardware = OmiParsing.text(read(current, OmiUuids.HARDWARE_REVISION)),
            )
        subscribe(current, OmiUuids.BUTTON, true)
        subscribe(current, OmiUuids.BATTERY_LEVEL, true)
        mutableBattery.value = OmiParsing.battery(read(current, OmiUuids.BATTERY_LEVEL)) ?: mutableBattery.value
        // Audio is the caller's intent and survives the link: resubscribe here, before Connected is
        // published, instead of relying on the caller to notice the reconnect.
        audioLock.withLock {
            if (audioIntent.wanted) {
                subscribe(current, OmiUuids.AUDIO_DATA, true)
                synchronized(assembler) { assembler.reset() }
                lastAudioAtMs = now()
                Log.i(TAG, "setUp: subscribed audio")
            }
            mutableConnection.value = PendantConnection.Connected(info)
        }
        connectTimeout.cancel()
        Log.i(TAG, "setUp: connected")
        watch(current)
    }

    /** Resubscribes when audio is wanted but none has arrived for 4 s (the official app's rule). */
    private suspend fun watch(current: BluetoothGatt) {
        while (true) {
            delay(WATCHDOG_TICK_MS)
            audioLock.withLock {
                if (audioIntent.wanted && now() - lastAudioAtMs >= RESUBSCRIBE_AFTER_MS) {
                    Log.w(TAG, "watchdog: no audio for ${now() - lastAudioAtMs}ms, resubscribing")
                    subscribe(current, OmiUuids.AUDIO_DATA, false)
                    subscribe(current, OmiUuids.AUDIO_DATA, true)
                    synchronized(assembler) { assembler.reset() }
                    lastAudioAtMs = now()
                }
            }
        }
    }

    private fun onNotification(
        uuid: UUID,
        value: ByteArray,
    ) {
        when (uuid) {
            OmiUuids.AUDIO_DATA -> {
                lastAudioAtMs = now()
                val frame = synchronized(assembler) { assembler.accept(value) }
                if (frame != null) {
                    if (mutableFrames.tryEmit(frame)) emittedFrames++ else overflowFrames++
                }
                mutableStats.value =
                    synchronized(assembler) {
                        LinkStats(
                            notifications = assembler.notifications,
                            lostNotifications = assembler.lostNotifications,
                            droppedFrames = assembler.droppedFrames + overflowFrames,
                            frames = emittedFrames,
                        )
                    }
            }
            OmiUuids.BUTTON -> OmiParsing.button(value)?.let { mutableButtons.tryEmit(it) }
            OmiUuids.BATTERY_LEVEL -> mutableBattery.value = OmiParsing.battery(value)
        }
    }

    private fun find(
        current: BluetoothGatt,
        uuid: UUID,
    ): BluetoothGattCharacteristic? =
        current.services
            .asSequence()
            .flatMap { it.characteristics.asSequence() }
            .firstOrNull { it.uuid == uuid }

    private suspend fun read(
        current: BluetoothGatt,
        uuid: UUID,
    ): ByteArray? {
        val characteristic = find(current, uuid) ?: return null
        return operation(OperationKind.Read, uuid) { current.readCharacteristic(characteristic) }
    }

    private suspend fun subscribe(
        current: BluetoothGatt,
        uuid: UUID,
        enabled: Boolean,
    ) {
        val characteristic = find(current, uuid) ?: return
        current.setCharacteristicNotification(characteristic, enabled)
        val descriptor = characteristic.getDescriptor(OmiUuids.CCCD) ?: return
        val value =
            if (enabled) {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            }
        operation(OperationKind.DescriptorWrite, uuid) { write(current, descriptor, value) }
    }

    /** One GATT operation at a time; returns the callback's value, or null on failure or timeout. */
    private suspend fun operation(
        kind: OperationKind,
        characteristic: UUID? = null,
        start: () -> Boolean,
    ): ByteArray? =
        operations.withLock {
            val op = PendingOperation(kind, characteristic)
            pending = op
            val result = if (start()) withTimeoutOrNull(OPERATION_TIMEOUT_MS) { op.done.await() } else null
            pending = null
            result
        }

    private fun finish(
        kind: OperationKind,
        characteristic: UUID?,
        value: ByteArray?,
    ) {
        if (pending?.complete(kind, characteristic, value) == false) {
            Log.w(TAG, "ignored a late $kind callback")
        }
    }

    @Suppress("DEPRECATION")
    private fun write(
        current: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            current.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            characteristic.value = value
            current.writeCharacteristic(characteristic)
        }

    @Suppress("DEPRECATION")
    private fun write(
        current: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray,
    ): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            current.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        } else {
            descriptor.value = value
            current.writeDescriptor(descriptor)
        }

    private val callback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                current: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                Log.i(TAG, "onConnectionStateChange status=$status newState=$newState")
                // A late event from a client already replaced (Bluetooth came back on) must not touch the live one.
                if (current !== gatt) {
                    current.close()
                    return
                }
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        connectTimeout.cancel() // connected: setUp's own operation timeouts take over
                        session?.cancel()
                        session = scope.launch { setUp(current) }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        close()
                        if (address != null) {
                            mutableConnection.value = PendantConnection.Connecting
                            reopenLater()
                        }
                    }
                }
            }

            override fun onMtuChanged(
                current: BluetoothGatt,
                mtu: Int,
                status: Int,
            ) = finish(OperationKind.Mtu, null, byteArrayOf())

            override fun onServicesDiscovered(
                current: BluetoothGatt,
                status: Int,
            ) = finish(OperationKind.Services, null, if (status == BluetoothGatt.GATT_SUCCESS) byteArrayOf() else null)

            override fun onCharacteristicRead(
                current: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) = finish(
                OperationKind.Read,
                characteristic.uuid,
                if (status ==
                    BluetoothGatt.GATT_SUCCESS
                ) {
                    value
                } else {
                    null
                },
            )

            @Deprecated("Called before Android 13 only")
            @Suppress("DEPRECATION")
            override fun onCharacteristicRead(
                current: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    finish(
                        OperationKind.Read,
                        characteristic.uuid,
                        if (status == BluetoothGatt.GATT_SUCCESS) characteristic.value else null,
                    )
                }
            }

            override fun onCharacteristicWrite(
                current: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) = finish(
                OperationKind.Write,
                characteristic.uuid,
                if (status ==
                    BluetoothGatt.GATT_SUCCESS
                ) {
                    byteArrayOf()
                } else {
                    null
                },
            )

            override fun onDescriptorWrite(
                current: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
            ) = finish(
                OperationKind.DescriptorWrite,
                descriptor.characteristic.uuid,
                if (status == BluetoothGatt.GATT_SUCCESS) byteArrayOf() else null,
            )

            override fun onCharacteristicChanged(
                current: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) = onNotification(characteristic.uuid, value)

            @Deprecated("Called before Android 13 only")
            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(
                current: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    characteristic.value?.let { onNotification(characteristic.uuid, it) }
                }
            }
        }

    private companion object {
        const val MTU = 247
        const val OPERATION_TIMEOUT_MS = 5_000L
        const val WATCHDOG_TICK_MS = 1_000L
        const val RESUBSCRIBE_AFTER_MS = 4_000L
        const val RECONNECT_DELAY_MS = 3_000L
        const val CONNECT_TIMEOUT_MS = 30_000L
        const val TAG = "OmiPendant"
    }
}
