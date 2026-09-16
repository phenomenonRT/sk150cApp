package com.sk150c.control.modbus

sealed class ModbusResponse {
    data class ReadRegisters(val slave: Int, val startAddress: Int, val values: IntArray) : ModbusResponse()
    data class WriteSingle(val slave: Int, val address: Int, val value: Int) : ModbusResponse()
    data class WriteMultiple(val slave: Int, val startAddress: Int, val count: Int) : ModbusResponse()
    data class Exception(val slave: Int, val functionCode: Int, val errorCode: Int) : ModbusResponse()
}

object ModbusFrames {

    private fun hi(v: Int) = ((v ushr 8) and 0xFF).toByte()
    private fun lo(v: Int) = (v and 0xFF).toByte()

    fun readRequest(slave: Int, startAddress: Int, count: Int): ByteArray {
        val body = byteArrayOf(
            slave.toByte(),
            0x03,
            hi(startAddress), lo(startAddress),
            hi(count), lo(count)
        )
        return Crc16.withCrc(body)
    }

    fun writeSingleRequest(slave: Int, address: Int, value: Int): ByteArray {
        val body = byteArrayOf(
            slave.toByte(),
            0x06,
            hi(address), lo(address),
            hi(value), lo(value)
        )
        return Crc16.withCrc(body)
    }

    fun writeMultipleRequest(slave: Int, startAddress: Int, values: List<Int>): ByteArray {
        val count = values.size
        val byteCount = count * 2
        val header = byteArrayOf(
            slave.toByte(),
            0x10,
            hi(startAddress), lo(startAddress),
            hi(count), lo(count),
            byteCount.toByte()
        )
        val payload = ByteArray(byteCount)
        values.forEachIndexed { i, v ->
            payload[i * 2] = hi(v)
            payload[i * 2 + 1] = lo(v)
        }
        return Crc16.withCrc(header + payload)
    }

    fun tryParse(buffer: ByteArray): Pair<ModbusResponse, Int>? {
        if (buffer.size < 5) return null
        val slave = buffer[0].toInt() and 0xFF
        val fn = buffer[1].toInt() and 0xFF

        if (fn and 0x80 != 0) {
            if (buffer.size < 5) return null
            if (!Crc16.isValid(buffer.copyOfRange(0, 5))) return null
            val errorCode = buffer[2].toInt() and 0xFF
            return ModbusResponse.Exception(slave, fn and 0x7F, errorCode) to 5
        }

        return when (fn) {
            0x03 -> {
                if (buffer.size < 3) return null
                val byteCount = buffer[2].toInt() and 0xFF
                val frameLen = 3 + byteCount + 2
                if (buffer.size < frameLen) return null
                val frame = buffer.copyOfRange(0, frameLen)
                if (!Crc16.isValid(frame)) return null
                val values = IntArray(byteCount / 2) { i ->
                    ((frame[3 + i * 2].toInt() and 0xFF) shl 8) or (frame[4 + i * 2].toInt() and 0xFF)
                }
                ModbusResponse.ReadRegisters(slave, -1, values) to frameLen
            }
            0x06 -> {
                val frameLen = 8
                if (buffer.size < frameLen) return null
                val frame = buffer.copyOfRange(0, frameLen)
                if (!Crc16.isValid(frame)) return null
                val addr = ((frame[2].toInt() and 0xFF) shl 8) or (frame[3].toInt() and 0xFF)
                val value = ((frame[4].toInt() and 0xFF) shl 8) or (frame[5].toInt() and 0xFF)
                ModbusResponse.WriteSingle(slave, addr, value) to frameLen
            }
            0x10 -> {
                val frameLen = 8
                if (buffer.size < frameLen) return null
                val frame = buffer.copyOfRange(0, frameLen)
                if (!Crc16.isValid(frame)) return null
                val addr = ((frame[2].toInt() and 0xFF) shl 8) or (frame[3].toInt() and 0xFF)
                val count = ((frame[4].toInt() and 0xFF) shl 8) or (frame[5].toInt() and 0xFF)
                ModbusResponse.WriteMultiple(slave, addr, count) to frameLen
            }
            else -> null
        }
    }

    fun encode(value: Double, decimals: Int): Int {
        var scaled = Math.round(value * Math.pow(10.0, decimals.toDouble())).toInt()
        if (scaled < 0) scaled = 0
        if (scaled > 0xFFFF) scaled = 0xFFFF
        return scaled
    }

    fun decode(raw: Int, decimals: Int): Double {
        return raw / Math.pow(10.0, decimals.toDouble())
    }
}
