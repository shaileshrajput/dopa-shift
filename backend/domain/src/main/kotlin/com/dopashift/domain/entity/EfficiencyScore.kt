package com.dopashift.domain.entity

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * A daily efficiency percentage computed from tracked productive vs. distracting time.
 *
 * Validation constraints:
 * - scorePercent: null (unavailable when <60s tracked) or 0–100
 * - productiveSeconds: non-negative
 * - totalTrackedSeconds: non-negative
 */
data class EfficiencyScore(
    val id: UUID,
    val userId: UUID,
    val date: LocalDate,
    val productiveSeconds: Long,
    val totalTrackedSeconds: Long,
    val scorePercent: Int?,
    val computedAt: Instant
) {
    init {
        require(productiveSeconds >= 0) {
            "Productive seconds must be non-negative"
        }
        require(totalTrackedSeconds >= 0) {
            "Total tracked seconds must be non-negative"
        }
        if (scorePercent != null) {
            require(scorePercent in 0..100) {
                "Score percent must be between 0 and 100"
            }
        }
    }
}
