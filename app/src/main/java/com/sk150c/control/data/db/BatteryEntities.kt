package com.sk150c.control.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "battery_profiles")
data class BatteryProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val endCurrent: Double,       // Ток окончания (например, 0.05А)
    val capacityLimitAh: Double = 0.0,
    val timeLimitMinutes: Int = 0,
    val notes: String = ""
)

@Entity(tableName = "charging_sessions")
data class ChargingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileName: String,
    val startTime: Long,
    val endTime: Long = 0,
    val chargedAh: Double = 0.0,
    val chargedWh: Double = 0.0,
    val status: String = "COMPLETED" // COMPLETED, STOPPED, ERROR
)
