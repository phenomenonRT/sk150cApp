package com.sk150c.control.ble

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

enum class LinkState { DISCONNECTED, CONNECTING, DISCOVERING, CONNECTED, FAILED }

/** A discovered candidate device, before we know if it's BLE or Classic SPP. */
data class FoundDevice(
    val name: String,
    val address: String,
    val transport: Transport,
    val rssi: Int? = null
)

enum class Transport { BLE, CLASSIC }

/**
 * Abstraction over the physical link to the power supply's communication
 * module (ZK-BT). Two concrete implementations exist: [BleSerialLink] for
 * BLE "UART passthrough" style modules, and [ClassicSerialLink] for classic
 * Bluetooth SPP (RFCOMM) modules. The ZK-BT module's exact chipset isn't
 * publicly documented, so the app supports both and lets the user pick
 * whichever one finds and talks to the device.
 */
interface SerialLink {
    val state: SharedFlow<LinkState>
    val incoming: SharedFlow<ByteArray>
    val lastError: SharedFlow<String>

    suspend fun connect(device: FoundDevice)
    fun disconnect()
    fun write(bytes: ByteArray)
}

/** Small helper so both implementations can emit to shared flows uniformly. */
open class LinkFlows {
    val stateFlow = MutableSharedFlow<LinkState>(replay = 1, extraBufferCapacity = 8)
    val incomingFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val errorFlow = MutableSharedFlow<String>(extraBufferCapacity = 8)
}
