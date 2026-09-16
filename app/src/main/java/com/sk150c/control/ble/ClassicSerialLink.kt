package com.sk150c.control.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharedFlow
import java.io.IOException
import java.util.UUID

/** Standard Serial Port Profile UUID, used by HC-05/HC-06-style modules. */
private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

@SuppressLint("MissingPermission")
class ClassicSerialLink(private val context: Context) : SerialLink, LinkFlows() {

    override val state: SharedFlow<LinkState> get() = stateFlow
    override val incoming: SharedFlow<ByteArray> get() = incomingFlow
    override val lastError: SharedFlow<String> get() = errorFlow

    private val adapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private var socket: BluetoothSocket? = null
    private var readJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Classic Bluetooth has no active "scan" callback API as simple as BLE;
     * we surface already-bonded (paired) devices, since SPP modules almost
     * always need to be paired via Android Bluetooth settings first. */
    fun pairedDevices(): List<FoundDevice> {
        return try {
            val bonded = adapter?.bondedDevices ?: emptySet()
            bonded.map { FoundDevice(it.name ?: it.address, it.address, Transport.CLASSIC) }
        } catch (e: SecurityException) {
            errorFlow.tryEmit("Missing Bluetooth permission: ${e.message}")
            emptyList()
        }
    }

    override suspend fun connect(device: FoundDevice) {
        stateFlow.tryEmit(LinkState.CONNECTING)
        withContext(Dispatchers.IO) {
            try {
                val btDevice = adapter?.getRemoteDevice(device.address)
                    ?: throw IOException("Unknown device ${device.address}")
                adapter?.cancelDiscovery()
                val sock = btDevice.createRfcommSocketToServiceRecord(SPP_UUID)
                sock.connect()
                socket = sock
                stateFlow.tryEmit(LinkState.CONNECTED)
                startReadLoop(sock)
            } catch (e: IOException) {
                errorFlow.tryEmit("Classic Bluetooth connect failed: ${e.message}")
                stateFlow.tryEmit(LinkState.FAILED)
            } catch (e: SecurityException) {
                errorFlow.tryEmit("Missing Bluetooth permission: ${e.message}")
                stateFlow.tryEmit(LinkState.FAILED)
            }
        }
    }

    private fun startReadLoop(sock: BluetoothSocket) {
        readJob?.cancel()
        readJob = scope.launch {
            val buffer = ByteArray(1024)
            val input = sock.inputStream
            try {
                while (isActive) {
                    val n = input.read(buffer)
                    if (n > 0) {
                        incomingFlow.tryEmit(buffer.copyOfRange(0, n))
                    } else if (n < 0) {
                        break
                    }
                }
            } catch (e: IOException) {
                errorFlow.tryEmit("Connection lost: ${e.message}")
            } finally {
                stateFlow.tryEmit(LinkState.DISCONNECTED)
            }
        }
    }

    override fun disconnect() {
        readJob?.cancel()
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        socket = null
        stateFlow.tryEmit(LinkState.DISCONNECTED)
    }

    override fun write(bytes: ByteArray) {
        val sock = socket ?: run {
            errorFlow.tryEmit("Not connected")
            return
        }
        scope.launch {
            try {
                sock.outputStream.write(bytes)
                sock.outputStream.flush()
            } catch (e: IOException) {
                errorFlow.tryEmit("Write failed: ${e.message}")
            }
        }
    }
}
