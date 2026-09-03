package com.dopashift.domain.entity

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * A persisted monitoring rule linking a Monitored_App to an enforcement threshold,
 * plus its state (enabled, paused-for-today, created_at).
 *
 * The [limitType] selects the enforcement pattern: [LimitType.Once] uses the
 * [dailyLimitMinutes] single daily cumulative threshold (1..480) and leaves
 * [repetitiveIntervalMinutes] null; [LimitType.Repetitive] interrupts every
 * [repetitiveIntervalMinutes] whole minutes of continued use (1..120).
 *
 * This is the domain-owned source of truth for a Rule; the data layer maps it to
 * its Room representation. Pure Kotlin — no Android/framework dependencies.
 */
data class InterceptionRule(
    val id: UUID,
    val userId: UUID,
    val appPackageName: String,
    val dailyLimitMinutes: Int,
    val limitType: LimitType = LimitType.Once,
    val repetitiveIntervalMinutes: Int? = null,
    val enabled: Boolean = true,
    val pausedForDate: LocalDate? = null,
    val createdAt: Instant
) {
    init {
        require(dailyLimitMinutes in 1..480) {
            "Daily limit must be between 1 and 480 whole minutes"
        }
        when (limitType) {
            LimitType.Once -> require(repetitiveIntervalMinutes == null) {
                "Once rule must not set a repetitive interval"
            }
            LimitType.Repetitive -> require(repetitiveIntervalMinutes in 1..120) {
                "Repetitive interval must be between 1 and 120 whole minutes"
            }
        }
    }

    /**
     * Returns true when this Rule is paused for the given local calendar day.
     * A Rule paused for day D suppresses accumulation and interception while the
     * device local date equals D, then auto-resumes on the next day boundary.
     */
    fun isPausedOn(today: LocalDate): Boolean = pausedForDate == today
}
