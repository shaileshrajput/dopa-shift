package com.dopashift.domain.port

import com.dopashift.domain.model.QuietHoursWindow
import java.time.ZoneId
import java.util.UUID

/**
 * Port interface for retrieving the user's quiet-hours configuration and timezone.
 *
 * Implementations may read from local storage (DataStore/Room) or derive
 * from the user profile, enabling offline-capable evaluation.
 */
interface QuietHoursProvider {

    /**
     * Returns the user's configured quiet-hours window, or null if not configured.
     */
    suspend fun getQuietHours(userId: UUID): QuietHoursWindow?

    /**
     * Returns the user's configured timezone for date/time calculations.
     */
    suspend fun getUserTimeZone(userId: UUID): ZoneId
}
