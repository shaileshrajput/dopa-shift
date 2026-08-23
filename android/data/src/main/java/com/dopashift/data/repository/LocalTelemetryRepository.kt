package com.dopashift.data.repository

import com.dopashift.data.local.dao.TelemetryDao
import com.dopashift.data.local.entity.LocalTelemetryEvent
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LOCAL-ONLY telemetry storage. Raw foreground-app usage events are recorded here
 * and NEVER transmitted to the backend or any remote service.
 *
 * Privacy contract (Requirements 8.1, 8.2):
 * - This repository has NO network dependencies — it depends only on Room/TelemetryDao.
 * - Raw telemetry data stays on-device at all times.
 * - Only aggregated efficiency scores (via EfficiencyScoreRepository) are synced to the backend.
 *
 * This class is intentionally isolated from Retrofit/API layers to enforce the
 * architectural guarantee that raw telemetry never leaves the device.
 */
@Singleton
class LocalTelemetryRepository @Inject constructor(
    private val telemetryDao: TelemetryDao
) {

    /**
     * Records a foreground-app usage event.
     *
     * @param appPackageName The package name of the app that was in the foreground.
     * @param foregroundSeconds Duration in seconds the app was in the foreground.
     * @param date The date of the usage event.
     */
    suspend fun recordEvent(
        appPackageName: String,
        foregroundSeconds: Long,
        date: LocalDate
    ) {
        val event = LocalTelemetryEvent(
            id = UUID.randomUUID().toString(),
            appPackageName = appPackageName,
            foregroundSeconds = foregroundSeconds,
            date = date.toString(),
            recordedAt = System.currentTimeMillis()
        )
        telemetryDao.upsert(event)
    }

    /**
     * Returns all telemetry events for a given date.
     * Used by the efficiency score computation to aggregate productive vs. distracting time.
     */
    suspend fun getEventsForDate(date: LocalDate): List<LocalTelemetryEvent> {
        return telemetryDao.findByDate(date.toString())
    }

    /**
     * Returns the total foreground seconds accumulated for a specific app on a given date.
     * Used by the interception engine to determine when the daily allowance is depleted.
     *
     * @return Total seconds, or 0 if no data is recorded.
     */
    suspend fun getAccumulatedSeconds(appPackageName: String, date: LocalDate): Long {
        return telemetryDao.getTotalForegroundSeconds(date.toString(), appPackageName) ?: 0L
    }

    /**
     * Deletes telemetry events older than [cutoffDate].
     * Supports local storage management per Requirement 8.6 (prioritize retention
     * of the most recent 90 days, purge older records).
     */
    suspend fun deleteOlderThan(cutoffDate: LocalDate) {
        telemetryDao.deleteOlderThan(cutoffDate.toString())
    }
}
