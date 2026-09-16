package com.sk150c.control.modbus

import com.sk150c.control.ble.SerialLink
import com.sk150c.control.diag.DebugLog
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream

private const val TAG = "SK150C-Modbus"

class ModbusSession(private val link: SerialLink, private val scope: CoroutineScope) {

    private val mutex = Mutex()
    private val rxBuffer = ByteArrayOutputStream()
    private var pending: CompletableDeferred<ModbusResponse>? = null

    private var collectJob: Job? = null

    fun start() {
        collectJob?.cancel()
        collectJob = scope.launch {
            link.incoming.collect { chunk ->
                onBytes(chunk)
            }
        }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        synchronized(rxBuffer) { rxBuffer.reset() }
    }

    private fun onBytes(chunk: ByteArray) {
        DebugLog.d(TAG, "onBytes: ${chunk.joinToString(" ") { "%02X".format(it) }}")
        synchronized(rxBuffer) {
            rxBuffer.write(chunk)
            var current = rxBuffer.toByteArray()

            while (current.size >= 5) {
                // Try to find a valid frame in the current buffer
                val result = ModbusFrames.tryParse(current)
                
                if (result != null) {
                    val (response, consumed) = result
                    DebugLog.d(TAG, "onBytes: parsed $response ($consumed bytes consumed)")
                    
                    // Consume the bytes
                    val remaining = current.copyOfRange(consumed, current.size)
                    rxBuffer.reset()
                    rxBuffer.write(remaining)
                    current = remaining
                    
                    pending?.let { if (!it.isCompleted) it.complete(response) }
                    continue // Try to parse next frame if any
                } else {
                    // If tryParse returned null, it could be:
                    // 1. Not enough bytes yet (wait for more)
                    // 2. Corrupted frame (CRC failed or unknown function)
                    
                    // Let's check if we have enough bytes for a potential frame but it's invalid
                    val fn = current[1].toInt() and 0xFF
                    val expectedLen = when (fn and 0x7F) {
                        0x03 -> if (current.size >= 3) (current[2].toInt() and 0xFF) + 5 else 1000
                        0x06, 0x10 -> 8
                        else -> 1 // Unknown function, skip byte
                    }

                    if (current.size >= expectedLen) {
                        // We have the full frame but it didn't parse (likely bad CRC)
                        // Discard one byte to "hunt" for the next valid header
                        DebugLog.w(TAG, "onBytes: invalid frame or bad CRC at start, skipping 1 byte")
                        val remaining = current.copyOfRange(1, current.size)
                        rxBuffer.reset()
                        rxBuffer.write(remaining)
                        current = remaining
                        continue
                    }
                    break // Need more data
                }
            }
            if (rxBuffer.size() > 1024) rxBuffer.reset()
        }
    }

    suspend fun readRegisters(
        startAddress: Int,
        count: Int,
        slave: Int = 1,
        timeoutMs: Long = 1500
    ): IntArray = mutex.withLock {
        val deferred = CompletableDeferred<ModbusResponse>()
        pending = deferred
        val frame = ModbusFrames.readRequest(slave, startAddress, count)
        DebugLog.d(TAG, "readRegisters: sending ${frame.joinToString(" ") { "%02X".format(it) }}")
        link.write(frame)
        val result = withTimeoutOrNull(timeoutMs) { deferred.await() }
        pending = null
        DebugLog.d(TAG, "readRegisters: result=$result")
        when (result) {
            is ModbusResponse.ReadRegisters -> result.values
            is ModbusResponse.Exception -> throw ModbusException(result.errorCode)
            null -> throw ModbusTimeoutException()
            else -> throw IllegalStateException("Unexpected response $result")
        }
    }

    suspend fun writeSingle(
        address: Int,
        value: Int,
        slave: Int = 1,
        timeoutMs: Long = 1500
    ): Unit = mutex.withLock {
        val deferred = CompletableDeferred<ModbusResponse>()
        pending = deferred
        link.write(ModbusFrames.writeSingleRequest(slave, address, value))
        val result = withTimeoutOrNull(timeoutMs) { deferred.await() }
        pending = null
        when (result) {
            is ModbusResponse.WriteSingle -> Unit
            is ModbusResponse.Exception -> throw ModbusException(result.errorCode)
            null -> throw ModbusTimeoutException()
            else -> throw IllegalStateException("Unexpected response $result")
        }
    }

    suspend fun writeMultiple(
        startAddress: Int,
        values: List<Int>,
        slave: Int = 1,
        timeoutMs: Long = 1500
    ): Unit = mutex.withLock {
        val deferred = CompletableDeferred<ModbusResponse>()
        pending = deferred
        link.write(ModbusFrames.writeMultipleRequest(slave, startAddress, values))
        val result = withTimeoutOrNull(timeoutMs) { deferred.await() }
        pending = null
        when (result) {
            is ModbusResponse.WriteMultiple -> Unit
            is ModbusResponse.Exception -> throw ModbusException(result.errorCode)
            null -> throw ModbusTimeoutException()
            else -> throw IllegalStateException("Unexpected response $result")
        }
    }
}

class ModbusTimeoutException : Exception("No response from power supply")
class ModbusException(val code: Int) : Exception("Modbus error code $code")
