package com.sk150c.control

import android.app.Application
import com.sk150c.control.ble.BleSerialLink
import com.sk150c.control.ble.ClassicSerialLink
import com.sk150c.control.data.PowerSupplyRepository
import com.sk150c.control.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class SK150CApplication : Application() {

    // Application scope that lives as long as the process
    val applicationScope = CoroutineScope(SupervisorJob())

    lateinit var repository: PowerSupplyRepository
        private set

    override fun onCreate() {
        super.onCreate()
        
        val db = AppDatabase.getDatabase(this)
        val bleLink = BleSerialLink(this)
        val classicLink = ClassicSerialLink(this)
        
        repository = PowerSupplyRepository(
            this,
            bleLink, 
            classicLink, 
            db.batteryDao(), 
            applicationScope
        )
    }
}
