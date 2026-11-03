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
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * The Limitless pendant over Bluetooth Low Energy. UNTESTED on real hardware: no Limitless pendant has been in reach,
 * so this class is built from `BasedHardware/omi` at afad3b7 ([LimitlessProtocol]) and exercised only against
 * [FakeLimitless]. Callers hold BLUETOOTH_CONNECT, as for [OmiPendant].
 *
 * The connection follows [OmiPendant]'s: autoConnect, a retry on a lost link, one GATT operation at a time. What is
 * different is what the pendant says. Everything arrives on one characteristic (RX), so [onNotification] sorts it:
 * answers to an operation of [LimitlessStorage] go to it, anything else is live audio or a button press.
 *
 * Not done, and why: no liveness watchdog (the battery's notification interval is not known, so silence proves
 * nothing; Android's supervision timeout ends a dead link), no pendant clock skew, no firmware check.
 */
@SuppressLint("MissingPermission")
@Suppress("TooManyFunctions") // one Bluetooth link, one setUp: cohesive but wide
class LimitlessPendant(
    private val context: Context,
    scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    private val logger: PendantLogger = PendantLogger.Android,
) : Pendant {
    /** Last resort: nothing launched here may take the process down or strand the link, so it reconnects. */
    private val scope =
        scope +
            CoroutineExceptionHandler { _, e ->
                logger.log(Log.ERROR, TAG, "uncaught ${e.javaClass.simpleName}, reconnecting")
                recoverLink()
            }
    private val mutableConnection = MutableStateFlow<PendantConnection>(PendantConnection.Disconnected)
    private val mutableBattery = MutableStateFlow<Int?>(null)
    private val mutableStats = MutableStateFlow(LinkStats())
    private val mutableFrames = MutableSharedFlow<AudioFrame>(extraBufferCapacity = 1024)
    private val mutableButtons = MutableSharedFlow<ButtonEvent>(extraBufferCapacity = 8)

    override val model = RecorderModel.Limitless
    override val capabilities = LimitlessDriver.capabilities
    override val connection: StateFlow<PendantConnection> = mutableConnection
    override val battery: StateFlow<Int?> = mutableBattery
    override val stats: StateFlow<LinkStats> = mutableStats
    override val frames: Flow<AudioFrame> = mutableFrames
    override val buttons: Flow<ButtonEvent> = mutableButtons

    private val commands = LimitlessCommands()
    private val audioIntent = AudioIntent()
    private val limitlessStorage = LimitlessStorage(GattLink(), commands, ::restingMode, ::warn)
    override val storage: RecorderStorage = limitlessStorage
    private val limitlessSettings = LimitlessSettings(GattLink(), commands, ::warn)
    override val settings: PendantSettings = limitlessSettings

    private val liveAudio = LimitlessLiveAudio(now)
    private val operations = Mutex()

    /** Serialises open, close, connect and disconnect: the retry runs on the scope, disconnect on the caller. */
    private val link = Any()
    private val connectTimeout =
        ConnectTimeout(
            scope,
            CONNECT_TIMEOUT_MS,
            isConnecting = { address != null && mutableConnection.value == PendantConnection.Connecting },
            retry = {
                warn("connect timeout after ${CONNECT_TIMEOUT_MS}ms, opening a new client")
                synchronized(link) {
                    close()
                    open()
                }
            },
        )

    /** Serialises the mode writes of setUp and [setAudio], so one cannot undo the other. */
    private val audioLock = Mutex()

    @Volatile
    private var pending: PendingOperation? = null

    @Volatile
    private var gatt: BluetoothGatt? = null

    @Volatile
    private var address: String? = null
    private var watchingAdapter = false
    private var emittedFrames = 0L
    private var overflowFrames = 0L
    private var notifications = 0L
    private var session: Job? = null
    private val busy = BusyClient()

    /** A setUp left with a stuck client reconnects once; a second one in a row carries on degraded. */
    private val stuckSetUps = FailureStreak(MAX_STUCK_SETUPS)

    @Volatile
    private var mtu: Int? = null

    @Volatile
    private var mtuStatus: Int? = null

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
            info("disconnect requested")
            address = null
            audioIntent.clear()
            connectTimeout.cancel()
            watchAdapter(false)
            close()
            mutableConnection.value = PendantConnection.Disconnected
        }

    /**
     * Audio is the mode (message 8): live while wanted, flash only otherwise. A drain in progress owns the mode and
     * writes the intent as it stands when it ends, so only the intent is recorded then.
     */
    override suspend fun setAudio(enabled: Boolean) =
        audioLock.withLock {
            audioIntent.set(enabled)
            if (gatt == null || mutableConnection.value !is PendantConnection.Connected) return@withLock
            if (limitlessStorage.downloading) return@withLock
            writeMode()
        }

    /** The pendant has no haptic motor that is known. */
    override suspend fun buzz(haptic: Haptic) = Unit

    private fun restingMode() = if (audioIntent.wanted) LimitlessMode.Live else LimitlessMode.RecordOnly

    private suspend fun writeMode(): Boolean {
        val written = GattLink().write(commands.setMode(restingMode()))
        liveAudio.reset()
        info("mode ${restingMode()}, write ${if (written) "ok" else "failed"}")
        return written
    }

    private fun info(message: String) = logger.log(Log.INFO, TAG, message)

    private fun warn(message: String) = logger.log(Log.WARN, TAG, message)

    private fun open() =
        synchronized(link) {
            openLocked()
        }

    private fun openLocked() {
        val target = address ?: return // re-checked under the lock: disconnect() may have won
        val adapter = guarded(null) { context.getSystemService(BluetoothManager::class.java)?.adapter }
        val enabled = guarded(null) { adapter?.isEnabled }
        if (adapter == null || enabled == null) {
            warn("open: Bluetooth stack unreachable, retrying")
            return reopenLater() // no STATE_ON is coming for a stack that only failed to answer
        }
        if (!enabled) {
            info("open: Bluetooth is off, waiting for STATE_ON")
            return // adapterState opens a client once Bluetooth is back on
        }
        releaseGatt() // two callers can reach open(); never leave the first client alive
        info("open: connectGatt ${redactAddress(target)}")
        gatt =
            guarded(
                null,
            ) { adapter.getRemoteDevice(target).connectGatt(context, true, callback, BluetoothDevice.TRANSPORT_LE) }
        if (gatt == null) reopenLater() else connectTimeout.arm()
    }

    /** Drops the client and starts over: for a link that failed in a way no callback will report. */
    private fun recoverLink() {
        synchronized(link) {
            if (address == null) return
            close()
            mutableConnection.value = PendantConnection.Connecting
        }
        reopenLater()
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
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                val action = adapterAction(state)
                if (action == AdapterAction.None) return
                synchronized(link) {
                    if (address == null) return
                    close()
                    mutableConnection.value = PendantConnection.Connecting
                    if (action == AdapterAction.Reopen) {
                        info("adapter STATE_ON, opening a fresh client")
                        openLocked()
                    } else {
                        info("adapter state $state, client dropped")
                        connectTimeout.cancel() // STATE_ON opens the next client
                    }
                }
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
        limitlessStorage.linkLost()
        limitlessSettings.linkLost()
        releaseGatt()
        busy.reset()
        mutableStats.update { it.copy(rssi = null) }
        liveAudio.reset()
    }

    private fun releaseGatt() {
        gatt?.let {
            guarded(Unit) { it.disconnect() }
            guarded(Unit) { it.close() }
        }
        gatt = null
    }

    /** Runs after every connection: MTU, services, RX, the pendant's clock, the mode, battery. */
    private suspend fun setUp(current: BluetoothGatt) {
        operation(OperationKind.Mtu) {
            val known = mtu
            known == null && current.requestMtu(MTU)
        }
        mtu?.let { info("setUp: mtu $it") } ?: warn("setUp: mtu unknown (status ${mtuStatus ?: "none"})")
        // A failed discovery is transient: disconnecting makes the reconnect path try again.
        if (operation(OperationKind.Services) { current.discoverServices() } == null) {
            warn("setUp: service discovery failed")
            return recoverLink()
        }
        if (find(current, LimitlessUuids.RX) == null || find(current, LimitlessUuids.TX) == null) {
            mutableConnection.value =
                PendantConnection.Refused("This device does not have the Limitless service Nytka expects.")
            return
        }
        if (!subscribe(current, LimitlessUuids.RX, true)) {
            warn("setUp: RX subscription failed")
            return recoverLink()
        }
        // The waits are upstream's: the pendant is not ready for commands the moment RX is subscribed.
        delay(SETTLE_MS)
        val link = GattLink()
        val clock = link.write(commands.setCurrentTime(now()))
        info("setUp: pendant clock write ${if (clock) "ok" else "failed"}")
        delay(CLOCK_WAIT_MS)
        limitlessStorage.evaluate()
        subscribe(current, OmiUuids.BATTERY_LEVEL, true)
        mutableBattery.value = OmiParsing.battery(read(current, OmiUuids.BATTERY_LEVEL)) ?: mutableBattery.value
        if (!carryOnWithClient()) return recoverLink()
        // Audio is the caller's intent and survives the link; the mode write is the audio switch.
        audioLock.withLock {
            writeMode()
            val name = guarded("Limitless") { current.device.name ?: "Limitless" }
            mutableConnection.value = PendantConnection.Connected(PendantInfo(name = name, model = "Limitless"))
        }
        connectTimeout.cancel()
        limitlessSettings.load()
        info("setUp: connected")
    }

    /**
     * False when a setUp request went unanswered, so the client refuses the rest, and the setUp before did not end
     * the same way: the caller reconnects. A second one in a row carries on without what the client refused.
     */
    private fun carryOnWithClient(): Boolean {
        if (!busy.stuck) {
            stuckSetUps.succeeded()
            return true
        }
        if (stuckSetUps.failed()) {
            warn("setUp: a request went unanswered again, carrying on without what it refused")
            return true
        }
        warn("setUp: a request went unanswered and the client refuses the rest, reconnecting")
        return false
    }

    private fun onNotification(
        uuid: UUID,
        value: ByteArray,
    ) {
        when (uuid) {
            LimitlessUuids.RX -> onRx(value)
            OmiUuids.BATTERY_LEVEL -> mutableBattery.value = OmiParsing.battery(value)
        }
    }

    /** What an operation of [limitlessStorage] waits for is its own; the rest is live audio, or else a button. */
    private fun onRx(raw: ByteArray) {
        if (limitlessStorage.onNotification(raw)) return
        val live = if (audioIntent.wanted) liveAudio.accept(raw) else emptyList()
        if (live.isEmpty()) {
            LimitlessStatus.parseButton(raw)?.let { mutableButtons.tryEmit(it) }
            return
        }
        for (frame in live) if (mutableFrames.tryEmit(frame)) emittedFrames++ else overflowFrames++
        mutableStats.update {
            it.copy(
                notifications = ++notifications,
                droppedFrames = liveAudio.dropped + overflowFrames,
                frames = emittedFrames,
            )
        }
    }

    private fun find(
        current: BluetoothGatt,
        uuid: UUID,
    ): BluetoothGattCharacteristic? =
        guarded(null) {
            current.services
                .asSequence()
                .flatMap { it.characteristics.asSequence() }
                .firstOrNull { it.uuid == uuid }
        }

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
    ): Boolean {
        val name = uuid.toString().take(UUID_HEAD)
        val characteristic = find(current, uuid)
        val descriptor = characteristic?.getDescriptor(OmiUuids.CCCD)
        if (characteristic == null || descriptor == null) {
            warn("subscribe: no notifiable characteristic $name")
            return false
        }
        val value =
            if (enabled) {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            }
        return retrying(
            SUBSCRIBE_ATTEMPTS,
            onFailed = { warn("notification write for $name failed (enabled=$enabled, attempt $it)") },
        ) {
            guarded(false) { current.setCharacteristicNotification(characteristic, enabled) }
            operation(OperationKind.DescriptorWrite, uuid) { write(current, descriptor, value) } != null
        }
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
            val started = guarded(false, start)
            val result = if (started) withTimeoutOrNull(OPERATION_TIMEOUT_MS) { op.done.await() } else null
            pending = null
            if (started && !op.done.isCompleted) {
                busy.timedOut(kind)
                warn("$kind timed out after ${OPERATION_TIMEOUT_MS}ms")
            }
            result
        }

    /** A GATT call that returns [fallback] instead of throwing when the Bluetooth stack is gone. */
    private inline fun <T> guarded(
        fallback: T,
        call: () -> T,
    ): T = gattCall(fallback, { warn("GATT call failed: ${it.javaClass.simpleName}") }, call)

    private fun finish(
        kind: OperationKind,
        characteristic: UUID?,
        value: ByteArray?,
    ) {
        val op = pending
        if (op?.complete(kind, characteristic, value) == true) return
        if (busy.lateCallback(kind)) info("late $kind callback, the client takes requests again")
        if (op != null) warn("ignored a late $kind callback")
    }

    // agent-checks: allow suppress-warning -- the pre-Android-13 GATT calls are the only ones below API 33
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

    // agent-checks: allow suppress-warning -- the pre-Android-13 GATT calls are the only ones below API 33
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

    /** The command characteristic on the current GATT client; a write fails once the link is gone. */
    private inner class GattLink : LimitlessLink {
        override suspend fun write(value: ByteArray): Boolean {
            val current = gatt ?: return false
            val characteristic = find(current, LimitlessUuids.TX) ?: return false
            return operation(OperationKind.Write, LimitlessUuids.TX) { write(current, characteristic, value) } != null
        }
    }

    private val callback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                current: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                val statusName = GattStatus.name(status)?.let { " ($it)" }.orEmpty()
                info("onConnectionStateChange status=$status$statusName newState=$newState")
                // One lock section for the check and the handling: open() assigns gatt after connectGatt returns
                // (autoConnect on a live ACL can call back before that), and a late event from a replaced client
                // must not close or reopen the live one.
                val stale =
                    synchronized(link) {
                        if (current !== gatt) return@synchronized true
                        when (newState) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                mtu = null
                                mtuStatus = null
                                busy.reset() // a connection change clears Android's busy flag too
                                connectTimeout.cancel() // connected: setUp's own operation timeouts take over
                                session?.cancel()
                                session = scope.launch { setUp(current) }
                            }

                            BluetoothProfile.STATE_DISCONNECTED -> {
                                mutableStats.update { it.copy(lastDisconnectStatus = status) }
                                close()
                                if (address != null) {
                                    mutableConnection.value = PendantConnection.Connecting
                                    reopenLater()
                                }
                            }
                        }
                        false
                    }
                if (stale) guarded(Unit) { current.close() }
            }

            override fun onMtuChanged(
                current: BluetoothGatt,
                mtu: Int,
                status: Int,
            ) {
                // A replaced client's MTU says nothing about this link.
                if (synchronized(link) { current !== gatt }) return
                mtuStatus = status
                if (status == BluetoothGatt.GATT_SUCCESS) this@LimitlessPendant.mtu = mtu
                // Not through finish(): nothing is pending when the callback comes early, and that is no error.
                pending?.complete(OperationKind.Mtu, null, byteArrayOf())
            }

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
            // agent-checks: allow suppress-warning -- the pre-Android-13 GATT calls are the only ones below API 33
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
                if (status == BluetoothGatt.GATT_SUCCESS) byteArrayOf() else null,
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
            // agent-checks: allow suppress-warning -- the pre-Android-13 GATT calls are the only ones below API 33
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
        const val MAX_STUCK_SETUPS = 2
        const val UUID_HEAD = 8
        const val SUBSCRIBE_ATTEMPTS = 2
        const val OPERATION_TIMEOUT_MS = 5_000L
        const val SETTLE_MS = 1_000L
        const val CLOCK_WAIT_MS = 1_000L
        const val RECONNECT_DELAY_MS = 3_000L
        const val CONNECT_TIMEOUT_MS = 30_000L
        const val TAG = "LimitlessPendant"
    }
}
