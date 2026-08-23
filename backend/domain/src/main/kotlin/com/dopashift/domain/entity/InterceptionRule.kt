package com.dopashift.domain.entity

import java.time.Instant
import java.util.UUID

/**
 * An interception rule that monitors a specific app or site against a daily time allowance.
 * May be linked to a goal for content recommendation matching.
 *
 * Validation constraints:
 * - dailyAllowanceMinutes: 1–480
 */
data class InterceptionRule(
    val id: UUID,
    val userId: UUID,
    val goalId: UUID?,
    val appPackageName: String?,
    val siteDomain: String?,
    val dailyAllowanceMinutes: Int,
    val isActive: Boolean = true,
    val createdAt: Instant
) {
    init {
        require(dailyAllowanceMinutes in 1..480) {
            "Daily allowance must be between 1 and 480 minutes"
        }
    }
}
