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
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * The Omi pendant over Bluetooth Low Energy. Callers hold BLUETOOTH_CONNECT: first run asks for
 * it before pairing, and the capture service only starts for a paired pendant.
 */
@SuppressLint("MissingPermission")
@Suppress("TooManyFunctions") // one Bluetooth link, one setUp: cohesive but wide
class OmiPendant(
    private val context: Context,
    scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    /** Monotonic clock for the watchdog; [now] is wall-clock time and feeds the frames' capture times. */
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

    override val connection: StateFlow<PendantConnection> = mutableConnection
    override val battery: StateFlow<Int?> = mutableBattery
    override val stats: StateFlow<LinkStats> = mutableStats
    override val frames: Flow<AudioFrame> = mutableFrames
    override val buttons: Flow<ButtonEvent> = mutableButtons

    private val omiStorage = OmiStorage(GattStorageLink(), ::warn)
    override val storage: PendantStorage = omiStorage

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
                warn("connect timeout after ${CONNECT_TIMEOUT_MS}ms, opening a new client")
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

    /** [elapsed] time of the last audio notification, or of switching audio on; the watchdog's audio clock. */
    @Volatile private var lastAudioAtMs = 0L

    /**
     * [elapsed] time of the last notification of any kind, or of the link coming up; the watchdog's liveness clock.
     * The pendant notifies its battery every 5 s while connected, so a link that has shown that pulse and then says
     * nothing is dead.
     */
    @Volatile private var lastAnyNotificationAtMs = 0L
    private var watchingAdapter = false
    private var emittedFrames = 0L
    private var overflowFrames = 0L
    private var session: Job? = null
    private val resubscribeFailures = FailureStreak(MAX_RESUBSCRIBE_FAILURES)
    private val watchdog = LinkWatchdog()
    private val resume = ResumeDetector()

    /** What the last `onMtuChanged` reported; observed only, nothing acts on it. Reset per connection. */
    @Volatile private var mtu: Int? = null

    @Volatile private var mtuStatus: Int? = null

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

    override suspend fun setAudio(enabled: Boolean) =
        audioLock.withLock {
            audioIntent.set(enabled)
            val current = gatt ?: return@withLock
            if (mutableConnection.value !is PendantConnection.Connected) return@withLock
            if (enabled) resume.switchedOn(elapsed()) // before the write: audio can arrive before it returns
            subscribe(current, OmiUuids.AUDIO_DATA, enabled)
            synchronized(assembler) { assembler.reset() }
            lastAudioAtMs = elapsed()
        }

    override suspend fun buzz(haptic: Haptic) {
        val current = gatt ?: return
        val characteristic = find(current, OmiUuids.HAPTIC) ?: return
        operation(OperationKind.Write, OmiUuids.HAPTIC) { write(current, characteristic, byteArrayOf(haptic.code)) }
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
            resubscribeFailures.succeeded()
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
                if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) !=
                    BluetoothAdapter.STATE_ON
                ) {
                    return
                }
                if (address == null) return
                info("adapter STATE_ON, opening a fresh client")
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
        omiStorage.linkLost()
        releaseGatt()
        synchronized(assembler) { assembler.reset() }
    }

    private fun releaseGatt() {
        gatt?.let {
            guarded(Unit) { it.disconnect() }
            guarded(Unit) { it.close() }
        }
        gatt = null
    }

    /** Runs after every connection: MTU, services, codec check, audio, pendant clock, device info, subscriptions. */
    private suspend fun setUp(current: BluetoothGatt) {
        operation(OperationKind.Mtu) {
            val known = mtu
            if (known != null) info("setUp: mtu $known already reported, request skipped")
            known == null && current.requestMtu(MTU)
        }
        // The callback can also arrive unprompted or early, so read what it recorded, not the operation's result.
        mtu?.let { info("setUp: mtu $it") } ?: warn("setUp: mtu unknown (status ${mtuStatus ?: "none"})")
        // A failed discovery or codec read is transient: disconnecting makes the reconnect path try again.
        if (operation(OperationKind.Services) { current.discoverServices() } == null) {
            warn("setUp: service discovery failed")
            return recoverLink()
        }
        info("setUp: services discovered")
        val rawCodec =
            read(current, OmiUuids.AUDIO_CODEC) ?: run {
                warn("setUp: codec read failed")
                return recoverLink()
            }

        val codec = OmiParsing.codec(rawCodec)
        info("setUp: codec $codec")
        if (codec != OPUS_FS320) {
            mutableConnection.value =
                PendantConnection.Refused(
                    "The pendant sends audio codec ${codec ?: "unknown"}; Nytka needs Opus FS320 (21). " +
                        "Update the pendant's firmware with the official Omi app.",
                )
            return
        }

        // Audio first, so a pendant that reconnects to a running service drops no frame while the rest of setUp runs.
        val audioOn = audioLock.withLock { subscribeAudioIfWanted(current) }
        val info = readInfo(current)
        omiStorage.evaluate(info.firmware)
        info("setUp: storage ${omiStorage.support.value}")
        // The time write also tells the SD worker the clock is set: only a pendant with the ring gets it.
        if (omiStorage.support.value == StorageSupport.Supported) syncPendantClock()
        subscribe(current, OmiUuids.BUTTON, true)
        subscribe(current, OmiUuids.BATTERY_LEVEL, true)
        mutableBattery.value = OmiParsing.battery(read(current, OmiUuids.BATTERY_LEVEL)) ?: mutableBattery.value
        // Audio is the caller's intent and survives the link. A setAudio during setUp only recorded the intent,
        // so subscribe here too if it arrived after the early subscription, before Connected is published.
        audioLock.withLock {
            reconcileAudio(current, audioOn)
            mutableConnection.value = PendantConnection.Connected(info)
        }
        connectTimeout.cancel()
        info("setUp: connected")
        lastAnyNotificationAtMs = elapsed() // the link just answered our reads: the liveness clock starts here
        watch(current)
    }

    /** Subscribes to audio when the caller wants it; the caller holds [audioLock]. */
    private suspend fun subscribeAudioIfWanted(current: BluetoothGatt): Boolean {
        if (!audioIntent.wanted) return false
        resume.switchedOn(elapsed())
        val subscribed = subscribe(current, OmiUuids.AUDIO_DATA, true)
        synchronized(assembler) { assembler.reset() }
        lastAudioAtMs = elapsed()
        info("setUp: subscribed audio")
        return subscribed
    }

    /** Makes the audio subscription match the intent as it stands now; the caller holds [audioLock]. */
    private suspend fun reconcileAudio(
        current: BluetoothGatt,
        subscribed: Boolean,
    ) {
        when {
            audioIntent.wanted && !subscribed -> subscribeAudioIfWanted(current)
            !audioIntent.wanted && subscribed -> subscribe(current, OmiUuids.AUDIO_DATA, false) // muted during setUp
        }
    }

    /** The stamps of stored audio count from the pendant clock: read it before writing the phone's time. */
    private suspend fun syncPendantClock() {
        val written = omiStorage.syncClock { now() / MS_PER_S }
        val skew = omiStorage.clockSkew.value?.let { "$it s" } ?: "unknown"
        info("setUp: pendant clock skew $skew, write ${if (written) "ok" else "failed"}")
    }

    private suspend fun readInfo(current: BluetoothGatt) =
        PendantInfo(
            name = guarded("Omi") { current.device.name ?: "Omi" },
            model = OmiParsing.text(read(current, OmiUuids.MODEL_NUMBER)),
            firmware = OmiParsing.text(read(current, OmiUuids.FIRMWARE_REVISION)),
            hardware = OmiParsing.text(read(current, OmiUuids.HARDWARE_REVISION)),
        )

    /**
     * Watches the link while audio is wanted. Quiet audio is only noted, once: the pendant's microphone sleeps in a
     * quiet room. A resubscribe is rare and slow, and a reconnect needs a link that had a pulse and says nothing.
     */
    private suspend fun watch(current: BluetoothGatt) {
        while (true) {
            delay(WATCHDOG_TICK_MS)
            // Bluetooth is turning off or off: the adapter-state path reopens the link, a resubscribe would only fail.
            if (!adapterOn()) continue
            audioLock.withLock {
                if (!audioIntent.wanted) return@withLock // muted: silence is intended
                when (val action = watchdog.check(elapsed(), lastAudioAtMs, lastAnyNotificationAtMs)) {
                    WatchdogAction.None -> Unit
                    is WatchdogAction.AudioIdle -> info("audio idle, pendant mic asleep?")
                    is WatchdogAction.Escalate -> {
                        warn("watchdog: no notification of any kind for ${action.quietMs}ms, reconnecting")
                        return recoverLink()
                    }
                    is WatchdogAction.Resubscribe -> {
                        info("watchdog: no audio for ${action.silentMs}ms, resubscribing, next in ${action.nextMs}ms")
                        subscribe(current, OmiUuids.AUDIO_DATA, false)
                        if (subscribe(current, OmiUuids.AUDIO_DATA, true)) {
                            resubscribeFailures.succeeded()
                        } else if (resubscribeFailures.failed()) {
                            // The client is dead though the adapter says on: only a new one can recover.
                            warn("watchdog: resubscribe keeps failing, reconnecting")
                            return recoverLink()
                        }
                        synchronized(assembler) { assembler.reset() }
                    }
                }
            }
        }
    }

    private fun onNotification(
        uuid: UUID,
        value: ByteArray,
    ) {
        val nowMs = elapsed()
        lastAnyNotificationAtMs = nowMs // audio or not: any notification shows the link is alive
        // The battery's or the button's notification lets silence be judged; audio and storage traffic do not.
        if (uuid != OmiUuids.AUDIO_DATA && uuid != OmiUuids.STORAGE_CONTROL) watchdog.pulseArrived()
        when (uuid) {
            OmiUuids.AUDIO_DATA -> {
                lastAudioAtMs = nowMs
                resume.arrived(nowMs)?.let { info("audio resumed after ${it}ms") }
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
            OmiUuids.STORAGE_CONTROL -> omiStorage.onNotification(value)
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
        if (characteristic == null) {
            warn("subscribe: no characteristic $name")
            return false
        }
        val descriptor = characteristic.getDescriptor(OmiUuids.CCCD)
        if (descriptor == null) {
            warn("subscribe: characteristic $name has no notification descriptor")
            return false
        }
        val value =
            if (enabled) {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            }
        // Audio, the button and the battery pulse all depend on this write: a failed one is tried once more at once.
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
            val result =
                if (guarded(
                        false,
                        start,
                    )
                ) {
                    withTimeoutOrNull(OPERATION_TIMEOUT_MS) { op.done.await() }
                } else {
                    null
                }
            pending = null
            result
        }

    private fun adapterOn(): Boolean =
        guarded(false) { context.getSystemService(BluetoothManager::class.java).adapter.isEnabled }

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
        if (pending?.complete(kind, characteristic, value) == false) {
            warn("ignored a late $kind callback")
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

    /** The storage service on the current GATT client: every call fails or returns null once the link is gone. */
    private inner class GattStorageLink : StorageLink {
        override suspend fun subscribeControl(): Boolean =
            gatt?.let { subscribe(it, OmiUuids.STORAGE_CONTROL, true) } ?: false

        override suspend fun writeControl(value: ByteArray): Boolean = writeTo(OmiUuids.STORAGE_CONTROL, value)

        override suspend fun readClock(): Long? = RingProtocol.u32le(readFrom(OmiUuids.TIME_SYNC_READ))

        override suspend fun writeClock(epochS: Long): Boolean =
            writeTo(OmiUuids.TIME_SYNC_WRITE, RingProtocol.clock(epochS))

        override suspend fun readFeatures(): Long? = RingProtocol.u32le(readFrom(OmiUuids.FEATURES))

        private suspend fun readFrom(uuid: UUID): ByteArray? = gatt?.let { read(it, uuid) }

        private suspend fun writeTo(
            uuid: UUID,
            value: ByteArray,
        ): Boolean {
            val current = gatt ?: return false
            val characteristic = find(current, uuid) ?: return false
            return operation(OperationKind.Write, uuid) { write(current, characteristic, value) } != null
        }
    }

    private val callback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                current: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                info("onConnectionStateChange status=$status newState=$newState")
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
                                watchdog.linkUp()
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
                if (status == BluetoothGatt.GATT_SUCCESS) this@OmiPendant.mtu = mtu
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
        const val MS_PER_S = 1_000L
        const val MAX_RESUBSCRIBE_FAILURES = 3
        const val UUID_HEAD = 8
        const val SUBSCRIBE_ATTEMPTS = 2
        const val OPERATION_TIMEOUT_MS = 5_000L
        const val WATCHDOG_TICK_MS = 1_000L
        const val RECONNECT_DELAY_MS = 3_000L
        const val CONNECT_TIMEOUT_MS = 30_000L
        const val TAG = "OmiPendant"
    }
}
