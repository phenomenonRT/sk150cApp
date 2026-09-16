package com.sk150c.control.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sk150c.control.ble.BleSerialLink
import com.sk150c.control.ble.ClassicSerialLink
import com.sk150c.control.ble.FoundDevice
import com.sk150c.control.ble.Transport
import com.sk150c.control.data.AppUiState
import com.sk150c.control.data.MemoryGroup
import com.sk150c.control.data.PowerSupplyRepository
import com.sk150c.control.modbus.Reg
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PowerSupplyViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as com.sk150c.control.SK150CApplication).repository
    private val batteryDao = com.sk150c.control.data.db.AppDatabase.getDatabase(app).batteryDao()

    val uiState: StateFlow<AppUiState> = repo.uiState
    
    val batteryProfiles = batteryDao.getAllProfiles()
    val chargingHistory = batteryDao.getAllSessions()

    fun scan(transport: Transport) = repo.scan(transport)
    fun stopScan() = repo.stopScan()
    fun connect(device: FoundDevice) = repo.connect(device)
    fun disconnect() = repo.disconnect()

    fun addSavedDevice(device: FoundDevice) = repo.addSavedDevice(device)
    fun renameSavedDevice(address: String, name: String) = repo.renameSavedDevice(address, name)
    fun deleteSavedDevice(address: String) = repo.deleteSavedDevice(address)
    fun connectSaved(device: com.sk150c.control.data.SavedDevice) {
        repo.connect(FoundDevice(device.customName, device.address, device.transport))
    }

    fun setVoltage(v: Double) = launchGuarded { repo.setVoltage(v) }
    fun setCurrent(a: Double) = launchGuarded { repo.setCurrent(a) }
    fun setVoltageAndCurrent(v: Double, a: Double) = launchGuarded { repo.setVoltageAndCurrent(v, a) }
    fun setOutputOn(on: Boolean) = launchGuarded { repo.setOutputOn(on) }
    fun setLocked(locked: Boolean) = launchGuarded { repo.setLocked(locked) }
    fun setBacklight(level: Int) = launchGuarded { repo.setBacklight(level) }
    fun setSleepMinutes(min: Int) = launchGuarded { repo.setSleepMinutes(min) }
    fun setBuzzer(on: Boolean) = launchGuarded { repo.setBuzzer(on) }
    fun setTempUnit(celsius: Boolean) = launchGuarded { repo.setTempUnit(celsius) }
    fun setSlaveAddress(addr: Int) = launchGuarded { repo.setSlaveAddress(addr) }
    fun setBaudRate(rate: Int) = launchGuarded { repo.setBaudRate(rate) }
    fun setTempOffset(reg: Reg, offset: Double) = launchGuarded { repo.setTempOffset(reg, offset) }
    fun recallGroup(group: Int) = launchGuarded { repo.recallGroup(group) }
    fun updateMemoryGroup(group: MemoryGroup) = launchGuarded { repo.updateMemoryGroup(group) }
    fun restoreFactory() = launchGuarded { repo.restoreFactory() }
    fun reset() = launchGuarded { repo.reset() }

    fun zeroStats() = launchGuarded { repo.zeroStats() }
    fun resetProtection() = launchGuarded { repo.resetProtection() }
    fun fetchProtectionSettings() = launchGuarded { repo.fetchProtectionSettings() }
    fun fetchAllMemoryGroups() = launchGuarded { repo.fetchAllMemoryGroups() }
    
    fun setLimitLup(v: Double) = launchGuarded { repo.updateProtectionLimit(com.sk150c.control.modbus.GroupReg.S_LVP, v) }
    fun setLimitOvp(v: Double) = launchGuarded { repo.updateProtectionLimit(com.sk150c.control.modbus.GroupReg.S_OVP, v) }
    fun setLimitOcp(a: Double) = launchGuarded { repo.updateProtectionLimit(com.sk150c.control.modbus.GroupReg.S_OCP, a) }
    fun setLimitOpp(w: Double) = launchGuarded { repo.updateProtectionLimit(com.sk150c.control.modbus.GroupReg.S_OPP, w) }
    fun setLimitOtp(t: Double) = launchGuarded { repo.updateProtectionLimit(com.sk150c.control.modbus.GroupReg.S_OTP, t) }
    fun setLimitOah(ah: Double) = launchGuarded { repo.setLimitOah(ah) }
    fun setLimitOwh(wh: Double) = launchGuarded { repo.setLimitOwh(wh) }
    fun setLimitOhp(h: Int, m: Int) = launchGuarded { repo.setLimitOhp(h, m) }
    fun setPowerOnOutput(on: Boolean) = launchGuarded { repo.setPowerOnOutput(on) }

    fun startCharging(profile: com.sk150c.control.data.db.BatteryProfile) = repo.startCharging(profile)
    fun stopCharging() = repo.stopCharging()

    fun saveProfile(profile: com.sk150c.control.data.db.BatteryProfile) = viewModelScope.launch {
        batteryDao.insertProfile(profile)
    }

    fun deleteProfile(profile: com.sk150c.control.data.db.BatteryProfile) = viewModelScope.launch {
        batteryDao.deleteProfile(profile)
    }

    fun deleteSession(id: Long) = viewModelScope.launch {
        batteryDao.deleteSessionById(id)
    }

    fun setDebugEnabled(enabled: Boolean) {
        repo.setDebugEnabled(enabled)
    }

    fun setSaveToFlash(enabled: Boolean) {
        repo.setSaveToFlash(enabled)
    }

    fun setKeepPanelsState(enabled: Boolean) {
        repo.setKeepPanelsState(enabled)
    }

    fun setRoundingEnabled(enabled: Boolean) {
        repo.setRoundingEnabled(enabled)
    }

    fun setVInOffset(v: Double) = repo.setVInOffset(v)
    fun setVOutOffset(v: Double) = repo.setVOutOffset(v)
    fun setIOutOffset(v: Double) = repo.setIOutOffset(v)

    fun setValueDisplayPosition(position: com.sk150c.control.data.ValueDisplayPosition) {
        repo.setValueDisplayPosition(position)
    }

    fun togglePanel(panelId: String, expanded: Boolean) {
        repo.togglePanel(panelId, expanded)
    }

    private fun launchGuarded(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (_: Exception) {
                // Surfaced already via repo.uiState.message on the next poll/error.
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // No longer disconnecting automatically to allow background operation
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PowerSupplyViewModel(app) as T
        }
    }
}
