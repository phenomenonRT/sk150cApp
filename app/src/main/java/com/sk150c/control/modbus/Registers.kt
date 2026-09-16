package com.sk150c.control.modbus

import com.sk150c.control.R

enum class Reg(val address: Int, val decimals: Int, val unit: String, val writable: Boolean) {
    V_SET(0x0000, 2, "V", true),
    I_SET(0x0001, 3, "A", true),
    VOUT(0x0002, 2, "V", false),
    IOUT(0x0003, 3, "A", false),
    POWER(0x0004, 1, "W", false),
    UIN(0x0005, 2, "V", false),
    AH_LOW(0x0006, 0, "mAh", false),
    AH_HIGH(0x0007, 0, "mAh", false),
    WH_LOW(0x0008, 0, "mWh", false),
    WH_HIGH(0x0009, 0, "mWh", false),
    OUT_H(0x000A, 0, "h", false),
    OUT_M(0x000B, 0, "m", false),
    OUT_S(0x000C, 0, "s", false),
    T_IN(0x000D, 1, "\u00b0", false),
    T_EX(0x000E, 1, "\u00b0", false),
    LOCK(0x000F, 0, "", true),
    PROTECT(0x0010, 0, "", false),
    CVCC(0x0011, 0, "", false),
    ONOFF(0x0012, 0, "", true),
    F_C(0x0013, 0, "", false),
    B_LED(0x0014, 0, "", true),
    SLEEP(0x0015, 0, "min", true),
    MODEL(0x0016, 0, "", false),
    VERSION(0x0017, 0, "", false),
    SLAVE_ADD(0x0018, 0, "", true),
    BAUDRATE_L(0x0019, 0, "", true),
    T_IN_OFFSET(0x001A, 1, "\u00b0", true),
    T_EX_OFFSET(0x001B, 1, "\u00b0", true),
    BUZZER(0x001C, 0, "", true),
    EXTRACT_M(0x001D, 0, "", true),
    DEVICE(0x001E, 0, "", true),
    V_SET_TEMP(0x001F, 2, "V", true),

    M0_V_SET(0x0050, 2, "V", true),
    M0_I_SET(0x0051, 3, "A", true),

    RESTORE_FACTORY(0x0020, 0, "", true),
    ZERO(0x0021, 0, "", true),
    RESET(0x002F, 0, "", true);

    companion object {
        fun byAddress(addr: Int): Reg? = entries.firstOrNull { it.address == addr }
    }
}

enum class GroupReg(val offset: Int, val decimals: Int, val unit: String) {
    V_SET(0x00, 2, "V"),
    I_SET(0x01, 3, "A"),
    S_LVP(0x02, 2, "V"),
    S_OVP(0x03, 2, "V"),
    S_OCP(0x04, 3, "A"),
    S_OPP(0x05, 1, "W"),
    S_OHP_H(0x06, 0, "h"),
    S_OHP_M(0x07, 0, "m"),
    S_OAH_L(0x08, 0, "mAh"),
    S_OAH_H(0x09, 0, "mAh"),
    S_OWH_L(0x0A, 0, "x10mWh"),
    S_OWH_H(0x0B, 0, "x10mWh"),
    S_OTP(0x0C, 1, "\u00b0"),
    S_INI(0x0D, 0, ""),
    S_O_CLOSE(0x0E, 0, "");

    fun address(group: Int): Int = 0x0050 + group * 0x0010 + offset
}

object ProtectStatus {
    private val resourceMap = mapOf(
        0 to R.string.protect_normal,
        1 to R.string.protect_ovp,
        2 to R.string.protect_ocp,
        3 to R.string.protect_opp,
        4 to R.string.protect_lvp,
        5 to R.string.protect_oah,
        6 to R.string.protect_ohp,
        7 to R.string.protect_otp,
        8 to R.string.protect_oep,
        9 to R.string.protect_owh,
        10 to R.string.protect_icp,
        11 to R.string.protect_ivp
    )

    fun getResourceId(code: Int): Int = resourceMap[code] ?: 0
}
