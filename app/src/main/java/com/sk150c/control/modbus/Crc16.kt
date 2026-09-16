package com.sk150c.control.modbus

/**
 * Modbus-RTU CRC16 (poly 0xA001, init 0xFFFF), exactly as described in the
 * ZK-SK150C manual section 1.4.
 */
object Crc16 {

    fun compute(data: ByteArray, length: Int = data.size): ByteArray {
        var crc = 0xFFFF
        for (i in 0 until length) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 0x0001 != 0) {
                    (crc ushr 1) xor 0xA001
                } else {
                    crc ushr 1
                }
            }
        }
        val lo = (crc and 0xFF).toByte()
        val hi = ((crc ushr 8) and 0xFF).toByte()
        return byteArrayOf(lo, hi)
    }

    fun withCrc(data: ByteArray): ByteArray {
        val crc = compute(data)
        return data + crc
    }

    fun isValid(frame: ByteArray): Boolean {
        if (frame.size < 3) return false
        val payload = frame.copyOfRange(0, frame.size - 2)
        val expected = compute(payload)
        return expected[0] == frame[frame.size - 2] && expected[1] == frame[frame.size - 1]
    }
}
