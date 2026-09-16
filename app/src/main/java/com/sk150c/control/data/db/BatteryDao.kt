package com.sk150c.control.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryDao {
    // Профили
    @Query("SELECT * FROM battery_profiles")
    fun getAllProfiles(): Flow<List<BatteryProfile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: BatteryProfile)

    @Delete
    suspend fun deleteProfile(profile: BatteryProfile)

    // Сессии (история)
    @Query("SELECT * FROM charging_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<ChargingSession>>

    @Insert
    suspend fun insertSession(session: ChargingSession)

    @Query("DELETE FROM charging_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    @Query("DELETE FROM charging_sessions")
    suspend fun deleteAllSessions()
}
