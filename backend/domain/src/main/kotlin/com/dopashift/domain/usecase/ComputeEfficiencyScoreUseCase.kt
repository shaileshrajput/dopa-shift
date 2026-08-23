package com.dopashift.domain.usecase

import com.dopashift.domain.entity.EfficiencyScore
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Pure computation use case for daily efficiency scores.
 *
 * Formula: (productiveSeconds / totalTrackedSeconds) * 100, rounded to nearest integer.
 * Returns scorePercent = null if totalTrackedSeconds < 60 (insufficient data).
 * Result is clamped to 0–100 range.
 *
 * This class is stateless and deterministic — same inputs always produce the same output
 * regardless of which client invokes it.
 */
class ComputeEfficiencyScoreUseCase {

    companion object {
        /** Minimum tracked seconds required to produce a meaningful score. */
        const val MIN_TRACKED_SECONDS: Long = 60
    }

    /**
     * Computes an [EfficiencyScore] entity from raw tracked time data.
     *
     * @param productiveSeconds Total seconds spent on productive apps/activities.
     * @param totalTrackedSeconds Total seconds of tracked foreground time.
     * @param userId The user this score belongs to.
     * @param date The day this score represents.
     * @return An [EfficiencyScore] with scorePercent set to null if insufficient data,
     *         or to the computed percentage (0–100) otherwise.
     */
    fun compute(
        productiveSeconds: Long,
        totalTrackedSeconds: Long,
        userId: UUID,
        date: LocalDate
    ): EfficiencyScore {
        require(productiveSeconds >= 0) { "productiveSeconds must be non-negative" }
        require(totalTrackedSeconds >= 0) { "totalTrackedSeconds must be non-negative" }

        val scorePercent: Int? = if (totalTrackedSeconds < MIN_TRACKED_SECONDS) {
            null
        } else {
            val raw = (productiveSeconds.toDouble() / totalTrackedSeconds.toDouble()) * 100.0
            raw.roundToInt().coerceIn(0, 100)
        }

        return EfficiencyScore(
            id = UUID.randomUUID(),
            userId = userId,
            date = date,
            productiveSeconds = productiveSeconds,
            totalTrackedSeconds = totalTrackedSeconds,
            scorePercent = scorePercent,
            computedAt = Instant.now()
        )
    }
}
