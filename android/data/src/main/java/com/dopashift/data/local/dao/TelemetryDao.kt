package com.dopashift.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dopashift.data.local.entity.LocalTelemetryEvent

@Dao
interface TelemetryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: LocalTelemetryEvent)

    @Query("SELECT * FROM telemetry_events WHERE date = :date")
    suspend fun findByDate(date: String): List<LocalTelemetryEvent>

    @Query("SELECT SUM(foregroundSeconds) FROM telemetry_events WHERE date = :date AND appPackageName = :packageName")
    suspend fun getTotalForegroundSeconds(date: String, packageName: String): Long?

    @Query("DELETE FROM telemetry_events WHERE date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String)
}
