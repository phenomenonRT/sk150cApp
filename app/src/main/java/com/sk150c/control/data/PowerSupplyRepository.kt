package com.sk150c.control.data

import com.sk150c.control.ble.*
import com.sk150c.control.diag.DebugLog
import com.sk150c.control.modbus.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "SK150C-Repo"

data class PowerReading(
    val vSet: Double = 0.0,
    val iSet: Double = 0.0,
    val vOut: Double = 0.0,
    val iOut: Double = 0.0,
    val power: Double = 0.0,
    val vIn: Double = 0.0,
    val ahLow: Int = 0,
    val ahHigh: Int = 0,
    val whLow: Int = 0,
    val whHigh: Int = 0,
    val outH: Int = 0,
    val outM: Int = 0,
    val outS: Int = 0,
    val tIn: Double = 0.0,
    val tEx: Double = 0.0,
    val locked: Boolean = false,
    val protectCode: Int = 0,
    val isConstantCurrent: Boolean = false,
    val outputOn: Boolean = false,
    val backlight: Int = 0,
    val sleepMin: Int = 0,

    // Calculated fields
    val capacityAh: Double = 0.0,
    val energyWh: Double = 0.0,
    val totalSeconds: Long = 0,

    // Protection limits (Group M0)
    val limitLup: Double = 0.0,
    val limitOvp: Double = 0.0,
    val limitOcp: Double = 0.0,
    val limitOpp: Double = 0.0,
    val limitOtp: Double = 0.0,
    val limitOah: Double = 0.0,
    val limitOwh: Double = 0.0,
    val limitOhpH: Int = 0,
    val limitOhpM: Int = 0,
    val powerOnOutput: Boolean = false,
    val protectCloseMask: Int = 0,
    val tempUnit: String = "°C",
    val tempInOffset: Double = 0.0,
    val tempExOffset: Double = 0.0,
    val slaveAddress: Int = 1,
    val baudRate: Int = 115200,
    val deviceStatus: Int = 0
)

enum class ConnectionPhase { IDLE, SCANNING, CONNECTING, LIVE, ERROR }

enum class ValueDisplayPosition { INSIDE, ABOVE, NONE }

data class AppUiState(
    val phase: ConnectionPhase = ConnectionPhase.IDLE,
    val reading: PowerReading = PowerReading(),
    val message: String? = null,
    val foundDevices: List<FoundDevice> = emptyList(),
    val savedDevices: List<SavedDevice> = emptyList(),
    val debugEnabled: Boolean = false,
    val memoryGroups: Map<Int, MemoryGroup> = emptyMap(),
    val loadingMemory: Boolean = false,
    val keepPanelsState: Boolean = true,
    val expandedPanels: Set<String> = emptySet(),
    val chargingActive: Boolean = false,
    val activeProfile: com.sk150c.control.data.db.BatteryProfile? = null,
    val roundingEnabled: Boolean = false,
    val valueDisplayPosition: ValueDisplayPosition = ValueDisplayPosition.INSIDE,
    val isStale: Boolean = false,
    val saveToFlash: Boolean = true,
    val vInOffset: Double = 0.0,
    val vOutOffset: Double = 0.0,
    val iOutOffset: Double = 0.0,
    val model: String = "",
    val version: String = "",
    val history: List<GraphPoint> = emptyList()
)

data class GraphPoint(
    val timestamp: Long,
    val voltage: Double,
    val current: Double
)

@OptIn(InternalSerializationApi::class)
@Serializable
data class SavedDevice(
    val address: String,
    val customName: String,
    val transport: Transport
)

data class MemoryGroup(
    val index: Int,
    val vSet: Double,
    val iSet: Double,
    val lvp: Double,
    val ovp: Double,
    val ocp: Double,
    val opp: Double,
    val ohpH: Int,
    val ohpM: Int,
    val oah: Double,
    val owh: Double,
    val otp: Double,
    val powerOn: Boolean,
    val closeMask: Int
)

class PowerSupplyRepository(
    private val context: android.content.Context,
    private val bleLink: BleSerialLink,
    private val classicLink: ClassicSerialLink,
    private val batteryDao: com.sk150c.control.data.db.BatteryDao,
    private val scope: CoroutineScope
) {
    private var activeLink: SerialLink? = null
    private var session: ModbusSession? = null
    private var pollJob: Job? = null
    private var linkObserveJob: Job? = null

    var slaveAddress: Int = 1
        private set

    private val sharedPrefs = bleLink.getContext().getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState

    init {
        // Load persisted settings
        slaveAddress = sharedPrefs.getInt("slave_address", 1)
        val debug = sharedPrefs.getBoolean("debug", false)
        val saveToFlash = sharedPrefs.getBoolean("save_to_flash", true)
        val rounding = sharedPrefs.getBoolean("rounding", false)
        val keepPanels = sharedPrefs.getBoolean("keep_panels", true)
        val posName = sharedPrefs.getString("display_pos", ValueDisplayPosition.INSIDE.name)
        val pos = try { ValueDisplayPosition.valueOf(posName!!) } catch (_: Exception) { ValueDisplayPosition.INSIDE }
        
        val panels = if (keepPanels) sharedPrefs.getStringSet("expanded_panels", emptySet()) ?: emptySet() else emptySet()
        val savedJson = sharedPrefs.getString("saved_devices", "[]") ?: "[]"
        val saved = try { Json.decodeFromString<List<SavedDevice>>(savedJson) } catch (_: Exception) { emptyList() }

        val vInOff = sharedPrefs.getFloat("v_in_offset", 0f).toDouble()
        val vOutOff = sharedPrefs.getFloat("v_out_offset", 0f).toDouble()
        val iOutOff = sharedPrefs.getFloat("i_out_offset", 0f).toDouble()

        _uiState.update { it.copy(
            debugEnabled = debug,
            saveToFlash = saveToFlash,
            roundingEnabled = rounding,
            keepPanelsState = keepPanels,
            valueDisplayPosition = pos,
            expandedPanels = panels,
            savedDevices = saved,
            vInOffset = vInOff,
            vOutOffset = vOutOff,
            iOutOffset = iOutOff
        ) }

        // Auto-connect to last used device
        val lastAddr = sharedPrefs.getString("last_device_addr", null)
        if (lastAddr != null) {
            val lastName = sharedPrefs.getString("last_device_name", "") ?: ""
            val lastTransport = sharedPrefs.getString("last_device_transport", Transport.BLE.name)
            val transport = try { Transport.valueOf(lastTransport!!) } catch(_: Exception) { Transport.BLE }
            
            // Try to connect using these details
            connect(FoundDevice(lastName, lastAddr, transport))
        }
    }

    private fun saveDevices(list: List<SavedDevice>) {
        val json = Json.encodeToString(list)
        sharedPrefs.edit().putString("saved_devices", json).apply()
        _uiState.update { it.copy(savedDevices = list) }
    }

    fun addSavedDevice(device: FoundDevice) {
        val current = _uiState.value.savedDevices
        if (current.none { it.address == device.address }) {
            val newList = current + SavedDevice(device.address, device.name, device.transport)
            saveDevices(newList)
        }
    }

    fun renameSavedDevice(address: String, newName: String) {
        val newList = _uiState.value.savedDevices.map {
            if (it.address == address) it.copy(customName = newName) else it
        }
        saveDevices(newList)
    }

    fun deleteSavedDevice(address: String) {
        val newList = _uiState.value.savedDevices.filter { it.address != address }
        saveDevices(newList)
        if (sharedPrefs.getString("last_device_addr", "") == address) {
            sharedPrefs.edit().remove("last_device_addr").apply()
        }
    }

    fun startCharging(profile: com.sk150c.control.data.db.BatteryProfile) {
        scope.launch {
            try {
                // 1. Reset stats on device for accurate measurement
                zeroStats()
                delay(200)
                
                // 2. Set protection limits if specified in profile
                if (profile.capacityLimitAh > 0) setLimitOah(profile.capacityLimitAh)
                if (profile.timeLimitMinutes > 0) setLimitOhp(profile.timeLimitMinutes / 60, profile.timeLimitMinutes % 60)
                delay(200)
                
                // 3. Turn Output ON to begin the process
                setOutputOn(true)
                
                _uiState.update { it.copy(chargingActive = true, activeProfile = profile) }
            } catch (e: Exception) {
                DebugLog.e(TAG, "Failed to start charging", e)
                _uiState.update { it.copy(message = "Start failed: ${e.message}") }
            }
        }
    }

    fun stopCharging(status: String = "STOPPED") {
        val profile = _uiState.value.activeProfile ?: return
        val reading = _uiState.value.reading
        
        scope.launch {
            try {
                // 1. Turn Output OFF
                setOutputOn(false)
                
                // 2. Save session to DB
                val session = com.sk150c.control.data.db.ChargingSession(
                    profileName = profile.name,
                    startTime = System.currentTimeMillis() - (reading.totalSeconds * 1000),
                    endTime = System.currentTimeMillis(),
                    chargedAh = reading.capacityAh,
                    chargedWh = reading.energyWh,
                    status = status
                )
                batteryDao.insertSession(session)
                
                _uiState.update { it.copy(chargingActive = false, activeProfile = null) }
            } catch (e: Exception) {
                DebugLog.e(TAG, "Failed to stop/save charging", e)
            }
        }
    }

    fun setDebugEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("debug", enabled).apply()
        _uiState.update { it.copy(debugEnabled = enabled) }
    }

    fun setSaveToFlash(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("save_to_flash", enabled).apply()
        _uiState.update { it.copy(saveToFlash = enabled) }
    }

    fun setKeepPanelsState(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("keep_panels", enabled).apply()
        _uiState.update { it.copy(keepPanelsState = enabled) }
        if (!enabled) {
            sharedPrefs.edit().remove("expanded_panels").apply()
            _uiState.update { it.copy(expandedPanels = emptySet()) }
        }
    }

    fun setRoundingEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("rounding", enabled).apply()
        _uiState.update { it.copy(roundingEnabled = enabled) }
    }

    fun setVInOffset(offset: Double) {
        sharedPrefs.edit().putFloat("v_in_offset", offset.toFloat()).apply()
        _uiState.update { it.copy(vInOffset = offset) }
    }

    fun setVOutOffset(offset: Double) {
        sharedPrefs.edit().putFloat("v_out_offset", offset.toFloat()).apply()
        _uiState.update { it.copy(vOutOffset = offset) }
    }

    fun setIOutOffset(offset: Double) {
        sharedPrefs.edit().putFloat("i_out_offset", offset.toFloat()).apply()
        _uiState.update { it.copy(iOutOffset = offset) }
    }

    fun setValueDisplayPosition(position: ValueDisplayPosition) {
        sharedPrefs.edit().putString("display_pos", position.name).apply()
        _uiState.update { it.copy(valueDisplayPosition = position) }
    }

    fun togglePanel(panelId: String, expanded: Boolean) {
        _uiState.update { s ->
            val newSet = if (expanded) s.expandedPanels + panelId else s.expandedPanels - panelId
            if (s.keepPanelsState) {
                sharedPrefs.edit().putStringSet("expanded_panels", newSet).apply()
            }
            s.copy(expandedPanels = newSet)
        }
    }

    fun scan(transport: Transport) {
        _uiState.update { it.copy(phase = ConnectionPhase.SCANNING, foundDevices = emptyList()) }
        when (transport) {
            Transport.BLE -> {
                bleLink.scan(
                    onFound = { device ->
                        // Filter for "Wuzhi Power" only
                        if (device.name.contains("Wuzhi Power", ignoreCase = true)) {
                            _uiState.update { s ->
                                if (s.foundDevices.any { it.address == device.address }) s
                                else s.copy(foundDevices = s.foundDevices + device)
                            }
                        }
                    },
                    onScanFailed = { msg ->
                        _uiState.update { it.copy(phase = ConnectionPhase.ERROR, message = msg) }
                    }
                )
            }
            Transport.CLASSIC -> {
                val paired = classicLink.pairedDevices()
                    .filter { it.name.contains("Wuzhi Power", ignoreCase = true) }
                _uiState.update { it.copy(foundDevices = paired) }
            }
        }
    }

    fun stopScan() {
        bleLink.stopScan()
    }

    fun connect(device: FoundDevice) {
        DebugLog.d(TAG, "connect() -> ${device.address} via ${device.transport}")
        stopScan()
        disconnect()
        val link: SerialLink = if (device.transport == Transport.BLE) bleLink else classicLink
        activeLink = link
        val newSession = ModbusSession(link, scope)
        session = newSession
        newSession.start()

        linkObserveJob?.cancel()
        linkObserveJob = scope.launch {
            link.state.collect { state ->
                DebugLog.d(TAG, "link.state -> $state")
                when (state) {
                    LinkState.CONNECTED -> {
                        sharedPrefs.edit()
                            .putString("last_device_addr", device.address)
                            .putString("last_device_name", device.name)
                            .putString("last_device_transport", device.transport.name)
                            .apply()
                        
                        // Start background service
                        val intent = android.content.Intent(context, com.sk150c.control.service.PowerSupplyService::class.java)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }

                        _uiState.update { it.copy(phase = ConnectionPhase.LIVE, message = null) }
                        startPolling(newSession)
                    }
                    LinkState.CONNECTING, LinkState.DISCOVERING ->
                        _uiState.update { it.copy(phase = ConnectionPhase.CONNECTING) }
                    LinkState.FAILED ->
                        _uiState.update { it.copy(phase = ConnectionPhase.ERROR) }
                    LinkState.DISCONNECTED -> {
                        pollJob?.cancel()
                        _uiState.update { it.copy(phase = ConnectionPhase.IDLE) }
                    }
                }
            }
        }
        scope.launch {
            link.lastError.collect { err ->
                DebugLog.d(TAG, "link.lastError: $err")
                _uiState.update { it.copy(message = err) }
            }
        }
        scope.launch { link.connect(device) }
    }

    fun disconnect() {
        if (_uiState.value.chargingActive) stopCharging("ABORTED")
        pollJob?.cancel()
        linkObserveJob?.cancel()
        activeLink?.disconnect()
        session?.stop()
        activeLink = null
        session = null
        _uiState.update { it.copy(phase = ConnectionPhase.IDLE) }
    }

    private fun startPolling(s: ModbusSession) {
        DebugLog.d(TAG, "startPolling()")
        pollJob?.cancel()
        pollJob = scope.launch {
            delay(400)
            // Initial fetch of settings and device info
            fetchProtectionSettings()
            fetchDeviceInfo()
            
            DebugLog.d(TAG, "poll loop entering, isActive=$isActive")
            while (isActive) {
                try {
                    // Split into two chunks: device firmware often limits max registers per request
                    val chunk1 = s.readRegisters(0x0000, 16, slaveAddress, timeoutMs = 800)
                    val chunk2 = s.readRegisters(0x0010, 15, slaveAddress, timeoutMs = 800)

                    val allValues = IntArray(31)
                    System.arraycopy(chunk1, 0, allValues, 0, 16)
                    System.arraycopy(chunk2, 0, allValues, 16, 15)

                    val newReading = decode(allValues)
                    _uiState.update { state ->
                        val newHistory = (state.history + GraphPoint(System.currentTimeMillis(), newReading.vOut, newReading.iOut))
                            .takeLast(100) // Keep last 100 points
                        state.copy(reading = newReading, history = newHistory, message = null, isStale = false)
                    }

                    // Automation: Stop charging if current is low
                    if (_uiState.value.chargingActive) {
                        val profile = _uiState.value.activeProfile
                        val reading = _uiState.value.reading
                        if (profile != null && reading.outputOn) {
                            // Check if current is below threshold for termination (must be in CV mode ideally)
                            if (reading.iOut < profile.endCurrent && reading.iOut > 0) {
                                DebugLog.d(TAG, "Automation: Charge complete (current ${reading.iOut} < ${profile.endCurrent})")
                                stopCharging("COMPLETED")
                            }
                        }
                    }
                } catch (e: ModbusTimeoutException) {
                    DebugLog.w(TAG, "poll: timeout")
                    _uiState.update { it.copy(isStale = true) }
                } catch (e: Exception) {
                    DebugLog.e(TAG, "poll: error", e)
                    _uiState.update { it.copy(message = e.message, isStale = true) }
                }
                delay(50)
            }
        }
    }

    private fun decode(v: IntArray): PowerReading {
        fun at(reg: Reg): Int = if (reg.address < v.size) v[reg.address] else 0
        fun dv(reg: Reg): Double = ModbusFrames.decode(at(reg), reg.decimals)

        val ah = (at(Reg.AH_HIGH).toLong() shl 16) or at(Reg.AH_LOW).toLong()
        val wh = (at(Reg.WH_HIGH).toLong() shl 16) or at(Reg.WH_LOW).toLong()

        val currentReading = _uiState.value.reading
        val state = _uiState.value

        return PowerReading(
            vSet = dv(Reg.V_SET),
            iSet = dv(Reg.I_SET),
            vOut = (dv(Reg.VOUT) + state.vOutOffset).coerceAtLeast(0.0),
            iOut = (dv(Reg.IOUT) + state.iOutOffset).coerceAtLeast(0.0),
            power = (dv(Reg.VOUT) + state.vOutOffset).coerceAtLeast(0.0) * (dv(Reg.IOUT) + state.iOutOffset).coerceAtLeast(0.0),
            vIn = (dv(Reg.UIN) + state.vInOffset).coerceAtLeast(0.0),
            ahLow = at(Reg.AH_LOW),
            ahHigh = at(Reg.AH_HIGH),
            whLow = at(Reg.WH_LOW),
            whHigh = at(Reg.WH_HIGH),
            outH = at(Reg.OUT_H),
            outM = at(Reg.OUT_M),
            outS = at(Reg.OUT_S),
            tIn = dv(Reg.T_IN),
            tEx = dv(Reg.T_EX),
            locked = at(Reg.LOCK) != 0,
            protectCode = at(Reg.PROTECT),
            isConstantCurrent = at(Reg.CVCC) != 0,
            outputOn = at(Reg.ONOFF) != 0,
            backlight = at(Reg.B_LED),
            sleepMin = at(Reg.SLEEP),
            tempUnit = if (at(Reg.F_C) == 0) "°C" else "°F",
            tempInOffset = dv(Reg.T_IN_OFFSET),
            tempExOffset = dv(Reg.T_EX_OFFSET),
            slaveAddress = at(Reg.SLAVE_ADD),
            baudRate = at(Reg.BAUDRATE_L),
            deviceStatus = at(Reg.DEVICE),
            capacityAh = ah / 1000.0,
            energyWh = wh / 1000.0,
            totalSeconds = at(Reg.OUT_H).toLong() * 3600 + at(Reg.OUT_M) * 60 + at(Reg.OUT_S),
            // Preserve limits from previous state until we fetch them
            limitLup = currentReading.limitLup,
            limitOvp = currentReading.limitOvp,
            limitOcp = currentReading.limitOcp,
            limitOpp = currentReading.limitOpp,
            limitOtp = currentReading.limitOtp
        )
    }

    suspend fun zeroStats() = writeReg(Reg.ZERO, 1)

    suspend fun fetchDeviceInfo() {
        val s = session ?: return
        try {
            val regs = s.readRegisters(Reg.MODEL.address, 2, slaveAddress)
            _uiState.update { it.copy(
                model = regs[0].toString(),
                version = "v${regs[1] / 10}.${regs[1] % 10}"
            ) }
        } catch (e: Exception) {
            DebugLog.e(TAG, "fetchDeviceInfo failed", e)
        }
    }

    suspend fun fetchProtectionSettings() {
        val s = session ?: return
        try {
            // Group 0 (M0) starts at 0x0050. Read 16 registers.
            val v = s.readRegisters(0x0050, 16, slaveAddress)
            _uiState.update { state ->
                val oah = (v[GroupReg.S_OAH_H.offset].toLong() shl 16) or v[GroupReg.S_OAH_L.offset].toLong()
                val owh = (v[GroupReg.S_OWH_H.offset].toLong() shl 16) or v[GroupReg.S_OWH_L.offset].toLong()
                state.copy(
                    reading = state.reading.copy(
                        limitLup = ModbusFrames.decode(v[GroupReg.S_LVP.offset], GroupReg.S_LVP.decimals),
                        limitOvp = ModbusFrames.decode(v[GroupReg.S_OVP.offset], GroupReg.S_OVP.decimals),
                        limitOcp = ModbusFrames.decode(v[GroupReg.S_OCP.offset], GroupReg.S_OCP.decimals),
                        limitOpp = ModbusFrames.decode(v[GroupReg.S_OPP.offset], GroupReg.S_OPP.decimals),
                        limitOtp = ModbusFrames.decode(v[GroupReg.S_OTP.offset], GroupReg.S_OTP.decimals),
                        limitOah = oah / 1000.0,
                        limitOwh = owh / 100.0, // Scale by 10 mWh (100.0 divisor)
                        limitOhpH = v[GroupReg.S_OHP_H.offset],
                        limitOhpM = v[GroupReg.S_OHP_M.offset],
                        powerOnOutput = v[GroupReg.S_INI.offset] != 0,
                        protectCloseMask = v[GroupReg.S_O_CLOSE.offset]
                    )
                )
            }
        } catch (e: Exception) {
            DebugLog.e(TAG, "Failed to fetch protection settings", e)
        }
    }

    suspend fun fetchAllMemoryGroups() {
        val s = session ?: return
        if (_uiState.value.loadingMemory) return
        
        _uiState.update { it.copy(loadingMemory = true) }
        try {
            val groups = mutableMapOf<Int, MemoryGroup>()
            // Read groups one by one to avoid large chunk issues and simplify mapping
            for (i in 0..10) {
                val baseAddr = 0x0050 + i * 0x0010
                val v = s.readRegisters(baseAddr, 16, slaveAddress)
                
                val oah = (v[GroupReg.S_OAH_H.offset].toLong() shl 16) or v[GroupReg.S_OAH_L.offset].toLong()
                val owh = (v[GroupReg.S_OWH_H.offset].toLong() shl 16) or v[GroupReg.S_OWH_L.offset].toLong()
                
                groups[i] = MemoryGroup(
                    index = i,
                    vSet = ModbusFrames.decode(v[GroupReg.V_SET.offset], GroupReg.V_SET.decimals),
                    iSet = ModbusFrames.decode(v[GroupReg.I_SET.offset], GroupReg.I_SET.decimals),
                    lvp = ModbusFrames.decode(v[GroupReg.S_LVP.offset], GroupReg.S_LVP.decimals),
                    ovp = ModbusFrames.decode(v[GroupReg.S_OVP.offset], GroupReg.S_OVP.decimals),
                    ocp = ModbusFrames.decode(v[GroupReg.S_OCP.offset], GroupReg.S_OCP.decimals),
                    opp = ModbusFrames.decode(v[GroupReg.S_OPP.offset], GroupReg.S_OPP.decimals),
                    ohpH = v[GroupReg.S_OHP_H.offset],
                    ohpM = v[GroupReg.S_OHP_M.offset],
                    oah = oah / 1000.0,
                    owh = owh / 100.0, // Scale by 10 mWh (100.0 divisor)
                    otp = ModbusFrames.decode(v[GroupReg.S_OTP.offset], GroupReg.S_OTP.decimals),
                    powerOn = v[GroupReg.S_INI.offset] != 0,
                    closeMask = v[GroupReg.S_O_CLOSE.offset]
                )
            }
            _uiState.update { it.copy(memoryGroups = groups, loadingMemory = false) }
        } catch (e: Exception) {
            DebugLog.e(TAG, "Failed to fetch memory groups", e)
            _uiState.update { it.copy(loadingMemory = false) }
        }
    }

    suspend fun updateMemoryGroup(group: MemoryGroup) {
        val s = session ?: throw IllegalStateException("Not connected")
        val baseAddr = 0x0050 + group.index * 0x0010
        
        val oahRaw = (group.oah * 1000).toLong()
        val owhRaw = (group.owh * 100).toLong() // Scale by 10 mWh (100x multiplier)
        
        val regs = IntArray(15)
        regs[GroupReg.V_SET.offset] = ModbusFrames.encode(group.vSet, GroupReg.V_SET.decimals)
        regs[GroupReg.I_SET.offset] = ModbusFrames.encode(group.iSet, GroupReg.I_SET.decimals)
        regs[GroupReg.S_LVP.offset] = ModbusFrames.encode(group.lvp, GroupReg.S_LVP.decimals)
        regs[GroupReg.S_OVP.offset] = ModbusFrames.encode(group.ovp, GroupReg.S_OVP.decimals)
        regs[GroupReg.S_OCP.offset] = ModbusFrames.encode(group.ocp, GroupReg.S_OCP.decimals)
        regs[GroupReg.S_OPP.offset] = ModbusFrames.encode(group.opp, GroupReg.S_OPP.decimals)
        regs[GroupReg.S_OHP_H.offset] = group.ohpH
        regs[GroupReg.S_OHP_M.offset] = group.ohpM
        regs[GroupReg.S_OAH_L.offset] = (oahRaw and 0xFFFF).toInt()
        regs[GroupReg.S_OAH_H.offset] = (oahRaw shr 16).toInt()
        regs[GroupReg.S_OWH_L.offset] = (owhRaw and 0xFFFF).toInt()
        regs[GroupReg.S_OWH_H.offset] = (owhRaw shr 16).toInt()
        regs[GroupReg.S_OTP.offset] = ModbusFrames.encode(group.otp, GroupReg.S_OTP.decimals)
        regs[GroupReg.S_INI.offset] = if (group.powerOn) 1 else 0
        regs[GroupReg.S_O_CLOSE.offset] = group.closeMask
        
        s.writeMultiple(baseAddr, regs.toList(), slaveAddress)
        fetchAllMemoryGroups()
        if (group.index == 0) fetchProtectionSettings()
    }

    suspend fun updateProtectionLimit(reg: GroupReg, value: Double) {
        val s = session ?: throw IllegalStateException("Not connected")
        val addr = reg.address(0) // M0
        val raw = ModbusFrames.encode(value, reg.decimals)
        s.writeSingle(addr, raw, slaveAddress)
        fetchProtectionSettings()
    }

    suspend fun setLimitOhp(hours: Int, minutes: Int) {
        val s = session ?: throw IllegalStateException("Not connected")
        s.writeMultiple(GroupReg.S_OHP_H.address(0), listOf(hours, minutes), slaveAddress)
        fetchProtectionSettings()
    }

    suspend fun setLimitOah(ah: Double) {
        val s = session ?: throw IllegalStateException("Not connected")
        val raw = (ah * 1000).toLong()
        s.writeMultiple(GroupReg.S_OAH_L.address(0), listOf((raw and 0xFFFF).toInt(), (raw shr 16).toInt()), slaveAddress)
        fetchProtectionSettings()
    }

    suspend fun setLimitOwh(wh: Double) {
        val s = session ?: throw IllegalStateException("Not connected")
        val raw = (wh * 100).toLong() // Scale by 10 mWh
        s.writeMultiple(GroupReg.S_OWH_L.address(0), listOf((raw and 0xFFFF).toInt(), (raw shr 16).toInt()), slaveAddress)
        fetchProtectionSettings()
    }

    suspend fun setPowerOnOutput(on: Boolean) {
        val s = session ?: throw IllegalStateException("Not connected")
        s.writeSingle(GroupReg.S_INI.address(0), if (on) 1 else 0, slaveAddress)
        fetchProtectionSettings()
    }

    suspend fun setVoltage(volts: Double) {
        val vRaw = ModbusFrames.encode(volts, Reg.V_SET.decimals)
        if (_uiState.value.saveToFlash) {
            // Write to Flash memory (Group M0)
            writeReg(Reg.M0_V_SET, vRaw)
        }
        // Write to Active RAM (Apply immediately)
        writeReg(Reg.V_SET, vRaw)
    }

    suspend fun setCurrent(amps: Double) {
        val iRaw = ModbusFrames.encode(amps, Reg.I_SET.decimals)
        if (_uiState.value.saveToFlash) {
            // Write to Flash memory (Group M0)
            writeReg(Reg.M0_I_SET, iRaw)
        }
        // Write to Active RAM (Apply immediately)
        writeReg(Reg.I_SET, iRaw)
    }

    suspend fun setOutputOn(on: Boolean) = writeReg(Reg.ONOFF, if (on) 1 else 0)
    suspend fun setLocked(locked: Boolean) = writeReg(Reg.LOCK, if (locked) 1 else 0)
    suspend fun setBacklight(level: Int) = writeReg(Reg.B_LED, level.coerceIn(0, 5))
    suspend fun setSleepMinutes(min: Int) = writeReg(Reg.SLEEP, min)
    suspend fun setBuzzer(on: Boolean) = writeReg(Reg.BUZZER, if (on) 1 else 0)
    suspend fun resetProtection() = writeReg(Reg.PROTECT, 0)
    suspend fun setTempUnit(celsius: Boolean) = writeReg(Reg.F_C, if (celsius) 0 else 1)
    suspend fun setSlaveAddress(addr: Int) {
        writeReg(Reg.SLAVE_ADD, addr.coerceIn(1, 255))
        slaveAddress = addr
        sharedPrefs.edit().putInt("slave_address", addr).apply()
    }
    suspend fun setBaudRate(rate: Int) = writeReg(Reg.BAUDRATE_L, rate)
    suspend fun setTempOffset(reg: Reg, offset: Double) = writeReg(reg, (offset * 10).toInt())
    suspend fun recallGroup(group: Int) = writeReg(Reg.EXTRACT_M, group.coerceIn(0, 10))
    suspend fun restoreFactory() = writeReg(Reg.RESTORE_FACTORY, 1)
    suspend fun reset() = writeReg(Reg.RESET, 1)

    suspend fun setVoltageAndCurrent(volts: Double, amps: Double) {
        val s = session ?: throw IllegalStateException("Not connected")
        val vRaw = ModbusFrames.encode(volts, Reg.V_SET.decimals)
        val iRaw = ModbusFrames.encode(amps, Reg.I_SET.decimals)

        if (_uiState.value.saveToFlash) {
            // Save to Flash (M0)
            s.writeMultiple(Reg.M0_V_SET.address, listOf(vRaw, iRaw), slaveAddress)
        }
        
        // Apply to Active (RAM)
        s.writeMultiple(Reg.V_SET.address, listOf(vRaw, iRaw), slaveAddress)
    }

    private suspend fun writeReg(reg: Reg, value: Int) {
        val s = session ?: throw IllegalStateException("Not connected")
        s.writeSingle(reg.address, value, slaveAddress)
    }
}
