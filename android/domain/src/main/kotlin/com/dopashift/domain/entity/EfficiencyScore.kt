package com.dopashift.domain.entity

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * A daily percentage computed from tracked productive vs. distracting foreground time.
 */
data class EfficiencyScore(
    val id: UUID,
    val userId: UUID,
    val date: LocalDate,
    val productiveSeconds: Long,
    val totalTrackedSeconds: Long,
    val scorePercent: Int?,
    val computedAt: Instant
)
