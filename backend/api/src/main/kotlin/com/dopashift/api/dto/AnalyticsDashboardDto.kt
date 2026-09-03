package com.dopashift.api.dto

import com.dopashift.domain.entity.EfficiencyScore
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// ===== Analytics — Efficiency Scores =====

/**
 * Response DTO for a single efficiency score entry.
 * Requirements: 7.1, 7.3
 */
data class EfficiencyScoreResponse(
    val id: UUID,
    val date: LocalDate,
    val productiveSeconds: Long,
    val totalTrackedSeconds: Long,
    val scorePercent: Int?,
    val computedAt: Instant
) {
    companion object {
        fun from(score: EfficiencyScore): EfficiencyScoreResponse = EfficiencyScoreResponse(
            id = score.id,
            date = score.date,
            productiveSeconds = score.productiveSeconds,
            totalTrackedSeconds = score.totalTrackedSeconds,
            scorePercent = score.scorePercent,
            computedAt = score.computedAt
        )
    }
}

/**
 * Request body for POST /v1/analytics/efficiency — submit a computed daily score.
 * Clients compute scores locally and submit aggregated results.
 * Requirements: 7.1
 */
data class SubmitEfficiencyScoreRequest(
    @field:NotNull(message = "date is required")
    val date: LocalDate,

    @field:Min(value = 0, message = "productiveSeconds must be non-negative")
    val productiveSeconds: Long,

    @field:Min(value = 0, message = "totalTrackedSeconds must be non-negative")
    val totalTrackedSeconds: Long
)

// ===== Dashboard — Summary =====

/**
 * Aggregated dashboard summary returned by GET /v1/dashboard/summary.
 *
 * The [activeGoalCount], [todayTodoCount], and [activeHabitCount] fields provide the
 * per-entity-type counts required to distinguish Zero_State (all three zero) from
 * Partial_State in a single response (DQC-5.3, Backend Delta 2). Note [todayTodoCount]
 * is the TOTAL number of to-dos for today in the user's time zone — distinct from
 * [pendingTodosCount], which counts only incomplete items — because a day of only
 * completed to-dos is not Zero_State.
 *
 * The remaining fields ([pendingTodosCount], [activeGoalsCount], [activeHabitsCount],
 * [todayEfficiencyScore], [efficiencyTrend]) are retained for existing consumers
 * (parent Requirement 20); the additions are non-breaking.
 *
 * Requirements: 20.1, 20.2, 20.12, 5.3
 */
data class DashboardSummaryResponse(
    val pendingTodosCount: Int,
    val activeGoalsCount: Int,
    val activeHabitsCount: Int,
    val todayEfficiencyScore: Int?,
    val efficiencyTrend: EfficiencyTrend,
    // DQC-5.3 per-entity-type counts sufficient to distinguish Zero_State from Partial_State.
    val activeGoalCount: Int,
    val todayTodoCount: Int,
    val activeHabitCount: Int
)

/**
 * Efficiency score trend direction based on current score vs. 7-day average.
 * Requirements: 20.3
 */
enum class EfficiencyTrend {
    IMPROVING,
    DECLINING,
    STABLE,
    NOT_ENOUGH_DATA
}

// ===== Dashboard — Activity Feed =====

/**
 * A single activity feed entry representing a recent user action.
 * Requirements: 20.6, 20.10
 */
data class ActivityFeedEntry(
    val id: UUID,
    val actionType: String,
    val entityType: String,
    val entityId: UUID,
    val entityName: String?,
    val timestamp: Instant
)

/**
 * Paginated activity feed response.
 * Requirements: 20.6, 20.10
 */
data class ActivityFeedResponse(
    val items: List<ActivityFeedEntry>,
    val page: Int,
    val size: Int,
    val totalItems: Long
)
