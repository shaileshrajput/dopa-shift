package com.dopashift.domain.model

import java.time.LocalTime

/**
 * Represents a user's configured quiet-hours window during which reminders are suppressed.
 *
 * The window is defined by a start and end time. If start > end, the window spans midnight
 * (e.g., 22:00 to 07:00 means quiet from 10pm to 7am).
 */
data class QuietHoursWindow(
    val start: LocalTime,
    val end: LocalTime
) {
    /**
     * Returns true if the given [time] falls within the quiet-hours window.
     */
    fun contains(time: LocalTime): Boolean {
        return if (start <= end) {
            // Same-day window (e.g., 13:00 to 15:00)
            time >= start && time < end
        } else {
            // Overnight window (e.g., 22:00 to 07:00)
            time >= start || time < end
        }
    }
}
