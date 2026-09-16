package com.sk150c.control.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.sk150c.control.diag.DebugLog
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.UUID
import kotlinx.coroutines.flow.SharedFlow

/**
 * Known "serial passthrough" GATT service/characteristic UUID sets used by
 * cheap Chinese BLE UART modules. The ZK-BT module's exact chipset isn't
 * documented, so we try to auto-detect: after connecting we scan all
 * discovered services for a characteristic we can write to and one we can
 * subscribe to for notifications, preferring these known families first.
 */
private val KNOWN_UART_SERVICES = listOf(
    // Nordic UART Service (NUS)
    Triple(
        UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"),
        UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"), // write (RX)
        UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")  // notify (TX)
    ),
    // HM-10 / CC254x / JDY-08 / AT-09 style modules
    Triple(
        UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    ),
    // Another common family (FFE5 service, FFE9 write, FFE4 notify)
    Triple(
        UUID.fromString("0000ffe5-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("0000ffe9-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("0000ffe4-0000-1000-8000-00805f9b34fb")
    ),
    // FFF0 service / FFF3 write / FFF1 notify — confirmed against a real
    // hardware GATT property dump on the ZK-BT module (Telink-style cheap
    // BLE UART bridge):
    //   fff1 = NOTIFY + WRITE_NO_RESPONSE   (props=0x14)
    //   fff2 = NOTIFY + READ                (props=0x12)  <- NOT writable!
    //   fff3 = WRITE_NO_RESPONSE            (props=0x04)  <- real write target
    // fff2 was previously (incorrectly) pinned as the write characteristic.
    // Since it has no WRITE/WRITE_NO_RESPONSE property at all,
    // writeCharacteristic() failed *synchronously* on every single call
    // (not a timeout — an immediate rejection by the Android BLE stack
    // before anything was sent over the air). fff3 is the actual
    // write-capable characteristic; fff1 correctly stays as notify.
    Triple(
        UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
    )
)

private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }

@SuppressLint("MissingPermission")
class BleSerialLink(private val context: android.content.Context) : SerialLink, LinkFlows() {

    fun getContext() = context

    override val state: SharedFlow<LinkState> get() = stateFlow
    override val incoming: SharedFlow<ByteArray> get() = incomingFlow
    override val lastError: SharedFlow<String> get() = errorFlow

    private val adapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var writeQueue = ArrayDeque<ByteArray>()
    private var writeInFlight = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var writeStallTimeout: Runnable? = null

    fun scan(onFound: (FoundDevice) -> Unit, onScanFailed: (String) -> Unit = {}) {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            onScanFailed("Bluetooth LE not available (is Bluetooth turned on?)")
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: result.scanRecord?.deviceName ?: "Unknown BLE device"
                onFound(FoundDevice(name, result.device.address, Transport.BLE, result.rssi))
            }
            override fun onScanFailed(errorCode: Int) {
                onScanFailed("BLE scan failed (code $errorCode)")
            }
        }
        currentScanCallback = callback
        try {
            scanner.startScan(null, settings, callback)
        } catch (e: SecurityException) {
            onScanFailed("Missing Bluetooth permission: ${e.message}")
        }
    }

    private var currentScanCallback: ScanCallback? = null

    fun stopScan() {
        currentScanCallback?.let { adapter?.bluetoothLeScanner?.stopScan(it) }
        currentScanCallback = null
    }

    override suspend fun connect(device: FoundDevice) {
        stopScan()
        DebugLog.d(TAG, "connect() -> ${device.address} (${device.name})")
        try {
            val btDevice = adapter?.getRemoteDevice(device.address)
            if (btDevice == null) {
                DebugLog.w(TAG, "connect() device not found")
                errorFlow.tryEmit("Device not found: ${device.address}")
                stateFlow.tryEmit(LinkState.FAILED)
                return
            }
            stateFlow.tryEmit(LinkState.CONNECTING)
            gatt = btDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            DebugLog.e(TAG, "connect() SecurityException", e)
            errorFlow.tryEmit("Missing Bluetooth permission: ${e.message}")
            stateFlow.tryEmit(LinkState.FAILED)
        }
    }

    override fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        writeChar = null
        writeQueue.clear()
        writeInFlight = false
        stateFlow.tryEmit(LinkState.DISCONNECTED)
    }

    override fun write(bytes: ByteArray) {
        DebugLog.d(TAG, "write() ${bytes.size} bytes: ${bytes.toHex()}")
        // Chunk into <=20 byte payloads for maximum compatibility with the
        // default 23-byte ATT MTU used by these cheap modules.
        val chunkSize = 20
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + chunkSize, bytes.size)
            writeQueue.addLast(bytes.copyOfRange(offset, end))
            offset = end
        }
        pumpWriteQueue()
    }

    private fun pumpWriteQueue() {
        if (writeInFlight) {
            DebugLog.d(TAG, "pumpWriteQueue() skipped, write already in flight")
            return
        }
        val char = writeChar
        if (char == null) {
            DebugLog.w(TAG, "pumpWriteQueue() aborted: no write characteristic selected yet")
            return
        }
        val g = gatt
        if (g == null) {
            DebugLog.w(TAG, "pumpWriteQueue() aborted: gatt is null")
            return
        }
        val next = writeQueue.removeFirstOrNull() ?: return
        writeInFlight = true
        DebugLog.d(TAG, "writeCharacteristic ${char.uuid} <- ${next.toHex()}")
        val ok = writeCharacteristicCompat(g, char, next)
        if (!ok) {
            DebugLog.e(TAG, "writeCharacteristic() returned failure for ${char.uuid}")
            writeInFlight = false
            errorFlow.tryEmit("BLE write failed")
            return
        }
        // Some stacks/modules don't reliably call onCharacteristicWrite for
        // write-without-response frames. Don't let a missed callback
        // permanently stall the queue.
        writeStallTimeout?.let { mainHandler.removeCallbacks(it) }
        val timeout = Runnable {
            if (writeInFlight) {
                DebugLog.w(TAG, "onCharacteristicWrite never arrived, forcing queue forward")
                writeInFlight = false
                pumpWriteQueue()
            }
        }
        writeStallTimeout = timeout
        mainHandler.postDelayed(timeout, 800)
    }

    private fun writeCharacteristicCompat(
        g: BluetoothGatt,
        char: BluetoothGattCharacteristic,
        value: ByteArray
    ): Boolean {
        // Cheap "transparent UART" BLE bridges (this module included) often
        // advertise both WRITE and WRITE_NO_RESPONSE but only actually act
        // on write-without-response frames, silently dropping "write
        // request" ones at the application layer even though the GATT ACK
        // still succeeds. Prefer no-response when it's available.
        val useNoResponse = char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val writeType = if (useNoResponse)
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            g.writeCharacteristic(char, value, writeType) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            char.value = value
            @Suppress("DEPRECATION")
            char.writeType = if (useNoResponse)
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            g.writeCharacteristic(char)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            DebugLog.d(TAG, "onConnectionStateChange status=$status newState=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                stateFlow.tryEmit(LinkState.DISCOVERING)
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                stateFlow.tryEmit(LinkState.DISCONNECTED)
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            DebugLog.d(TAG, "onMtuChanged mtu=$mtu status=$status")
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            DebugLog.d(TAG, "onServicesDiscovered status=$status, ${g.services.size} services")
            for (svc in g.services) {
                val chars = svc.characteristics.joinToString { c -> "${c.uuid}(props=${c.properties})" }
                DebugLog.d(TAG, "  service ${svc.uuid}: $chars")
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                errorFlow.tryEmit("Service discovery failed ($status)")
                stateFlow.tryEmit(LinkState.FAILED)
                return
            }
            val found = findUartCharacteristics(g)
            if (found == null) {
                DebugLog.w(TAG, "No writable+notifiable characteristic found on any service")
                errorFlow.tryEmit(
                    "No UART-like BLE characteristic found on this device. " +
                        "Try Classic Bluetooth mode instead."
                )
                stateFlow.tryEmit(LinkState.FAILED)
                return
            }
            val (write, notify) = found
            writeChar = write
            DebugLog.d(
                TAG,
                "Selected write=${write.uuid}(props=${write.properties}) " +
                    "notify=${notify.uuid}(props=${notify.properties}) service=${write.service.uuid}"
            )
            g.setCharacteristicNotification(notify, true)
            val cccd = notify.getDescriptor(CCCD_UUID)
            if (cccd != null) {
                DebugLog.d(TAG, "Writing CCCD descriptor to enable notifications")
                val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(cccd)
                }
                if (!ok) {
                    DebugLog.w(TAG, "writeDescriptor() call itself failed to enqueue; proceeding anyway")
                    finishConnect(g)
                }
                // else: wait for onDescriptorWrite() to continue, so we never issue a
                // second GATT operation (e.g. requestMtu) while this one is still pending -
                // the GATT command queue on most stacks only holds one outstanding op.
            } else {
                DebugLog.w(TAG, "Notify characteristic ${notify.uuid} has NO CCCD descriptor")
                errorFlow.tryEmit(
                    "Warning: notify characteristic has no CCCD descriptor — " +
                        "notifications may not actually be enabled on the device side."
                )
                finishConnect(g)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            DebugLog.d(TAG, "onDescriptorWrite ${descriptor.uuid} status=$status")
            if (descriptor.uuid == CCCD_UUID) {
                finishConnect(g)
            }
        }

        /** Called exactly once per connection, after CCCD is settled (or skipped). */
        private fun finishConnect(g: BluetoothGatt) {
            // Best-effort; some stacks/modules ignore this, harmless either way.
            g.requestMtu(185)
            stateFlow.tryEmit(LinkState.CONNECTED)
            DebugLog.d(TAG, "State -> CONNECTED")
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            DebugLog.d(TAG, "onCharacteristicChanged(new) ${characteristic.uuid} <- ${value.toHex()}")
            incomingFlow.tryEmit(value)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                val v = characteristic.value ?: ByteArray(0)
                DebugLog.d(TAG, "onCharacteristicChanged(legacy) ${characteristic.uuid} <- ${v.toHex()}")
                incomingFlow.tryEmit(v)
            }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            DebugLog.d(TAG, "onCharacteristicWrite ${characteristic.uuid} status=$status")
            writeStallTimeout?.let { mainHandler.removeCallbacks(it) }
            writeInFlight = false
            if (status != BluetoothGatt.GATT_SUCCESS) {
                errorFlow.tryEmit("BLE write error ($status)")
            }
            pumpWriteQueue()
        }
    }

    /**
     * Looks for a known UART service first; if none of those UUIDs are
     * present, falls back to a heuristic: any characteristic with a WRITE
     * property paired with any characteristic (same service) that supports
     * NOTIFY or INDICATE.
     */
    private fun findUartCharacteristics(
        g: BluetoothGatt
    ): Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic>? {
        for ((serviceUuid, writeUuid, notifyUuid) in KNOWN_UART_SERVICES) {
            val service = g.getService(serviceUuid) ?: continue
            val write = service.getCharacteristic(writeUuid)
            val notify = service.getCharacteristic(notifyUuid)
            if (write != null && notify != null) return write to notify
        }
        // Heuristic fallback across all discovered services.
        for (service in g.services) {
            var write: BluetoothGattCharacteristic? = null
            var notify: BluetoothGattCharacteristic? = null
            for (c in service.characteristics) {
                val props = c.properties
                if (write == null && (props and (BluetoothGattCharacteristic.PROPERTY_WRITE or
                            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0)
                ) write = c
                if (notify == null && (props and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                            BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0)
                ) notify = c
            }
            if (write != null && notify != null) return write to notify
        }
        return null
    }

    companion object {
        private const val TAG = "BleSerialLink"
    }
}
