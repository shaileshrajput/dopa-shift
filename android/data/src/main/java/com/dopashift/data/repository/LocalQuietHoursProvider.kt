package com.dopashift.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dopashift.domain.model.QuietHoursWindow
import com.dopashift.domain.port.QuietHoursProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.reminderPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "reminder_preferences"
)

/**
 * DataStore-backed implementation of [QuietHoursProvider].
 *
 * Reads the user's quiet-hours start/end times and timezone from local
 * preferences, enabling fully offline-capable reminder evaluation.
 */
@Singleton
class LocalQuietHoursProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : QuietHoursProvider {

    private companion object {
        val QUIET_HOURS_START = stringPreferencesKey("quiet_hours_start")
        val QUIET_HOURS_END = stringPreferencesKey("quiet_hours_end")
        val USER_TIMEZONE = stringPreferencesKey("user_timezone")
    }

    override suspend fun getQuietHours(userId: UUID): QuietHoursWindow? {
        val prefs = context.reminderPrefsDataStore.data.first()
        val startStr = prefs[QUIET_HOURS_START] ?: return null
        val endStr = prefs[QUIET_HOURS_END] ?: return null

        return try {
            QuietHoursWindow(
                start = LocalTime.parse(startStr),
                end = LocalTime.parse(endStr)
            )
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getUserTimeZone(userId: UUID): ZoneId {
        val prefs = context.reminderPrefsDataStore.data.first()
        val tzStr = prefs[USER_TIMEZONE]
        return try {
            if (tzStr != null) ZoneId.of(tzStr) else ZoneId.systemDefault()
        } catch (_: Exception) {
            ZoneId.systemDefault()
        }
    }
}
