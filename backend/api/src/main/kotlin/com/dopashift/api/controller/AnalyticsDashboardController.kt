package com.dopashift.api.controller

import com.dopashift.api.config.AuthenticatedUser
import com.dopashift.api.dto.ActivityFeedEntry
import com.dopashift.api.dto.ActivityFeedResponse
import com.dopashift.api.dto.DashboardSummaryResponse
import com.dopashift.api.dto.EfficiencyScoreResponse
import com.dopashift.api.dto.EfficiencyTrend
import com.dopashift.api.dto.SubmitEfficiencyScoreRequest
import com.dopashift.domain.repository.ChangeLogRepository
import com.dopashift.domain.repository.DailyTodoRepository
import com.dopashift.domain.repository.EfficiencyScoreRepository
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import com.dopashift.application.dashboard.DashboardSummaryQueryService
import com.dopashift.domain.usecase.ComputeEfficiencyScoreUseCase
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * REST controller for analytics (efficiency scores) and dashboard (summary, activity feed).
 * All endpoints require OAuth2 JWT authentication and scope results to the authenticated user.
 *
 * Requirements: 7.1, 7.3, 20.1, 20.2, 20.3, 20.6, 20.10, 20.12
 */
@RestController
class AnalyticsDashboardController(
    private val authenticatedUser: AuthenticatedUser,
    private val efficiencyScoreRepository: EfficiencyScoreRepository,
    private val computeEfficiencyScoreUseCase: ComputeEfficiencyScoreUseCase,
    private val dailyTodoRepository: DailyTodoRepository,
    private val goalRepository: GoalRepository,
    private val habitTrackRepository: HabitTrackRepository,
    private val changeLogRepository: ChangeLogRepository,
    private val dashboardSummaryQueryService: DashboardSummaryQueryService,
    private val clock: Clock
) {

    // ===== Analytics Endpoints =====

    /**
     * GET /v1/analytics/efficiency?from={date}&to={date}
     * Returns efficiency scores for the authenticated user within the specified date range.
     * Defaults to the last 30 days if no range is provided.
     *
     * Requirements: 7.1, 7.3
     */
    @GetMapping("/v1/analytics/efficiency")
    suspend fun getEfficiencyScores(
        @RequestParam("from", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        from: LocalDate?,

        @RequestParam("to", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        to: LocalDate?
    ): ResponseEntity<List<EfficiencyScoreResponse>> {
        val userId = authenticatedUser.getUserId()
        val today = LocalDate.now(clock)
        val fromDate = from ?: today.minusDays(30)
        val toDate = to ?: today

        val scores = efficiencyScoreRepository.findByUserIdAndDateRange(userId, fromDate, toDate)
        return ResponseEntity.ok(scores.map { EfficiencyScoreResponse.from(it) })
    }

    /**
     * POST /v1/analytics/efficiency
     * Submit a computed daily efficiency score. Clients compute scores locally
     * and submit aggregated results via this endpoint.
     *
     * Requirements: 7.1
     */
    @PostMapping("/v1/analytics/efficiency")
    suspend fun submitEfficiencyScore(
        @Valid @RequestBody request: SubmitEfficiencyScoreRequest
    ): ResponseEntity<EfficiencyScoreResponse> {
        val userId = authenticatedUser.getUserId()

        val score = computeEfficiencyScoreUseCase.compute(
            productiveSeconds = request.productiveSeconds,
            totalTrackedSeconds = request.totalTrackedSeconds,
            userId = userId,
            date = request.date
        )

        val saved = efficiencyScoreRepository.save(score)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(EfficiencyScoreResponse.from(saved))
    }

    // ===== Dashboard Endpoints =====

    /**
     * GET /v1/dashboard/summary
     * Returns aggregated dashboard data in a single call: per-entity-type counts
     * (active goals, today's total to-dos, active habits) sufficient to distinguish
     * Zero_State from Partial_State (DQC-5.3), plus the parent-spec fields (pending
     * todos, today's efficiency score, and trend indicator).
     *
     * All counts are scoped to the authenticated user_id; "today" is resolved in the
     * user's configured time zone.
     *
     * Requirements: 20.1, 20.2, 20.3, 20.12, 5.3
     */
    @GetMapping("/v1/dashboard/summary")
    suspend fun getDashboardSummary(): ResponseEntity<DashboardSummaryResponse> {
        val userId = authenticatedUser.getUserId()

        // Per-entity-type counts for Zero_State / Partial_State resolution (DQC-5.3),
        // computed by the application query service scoped to the authenticated user
        // with "today" in the user's configured time zone.
        val counts = dashboardSummaryQueryService.getSummary(userId)

        // Retained parent-spec fields. Pending todos uses today in the server clock's
        // zone as before; the DQC-5.3 counts above are the authoritative Zero/Partial signal.
        val today = LocalDate.now(clock)
        val todayTodos = dailyTodoRepository.findByUserIdAndDate(userId, today)
        val pendingTodosCount = todayTodos.count { !it.isCompleted }

        // Today's efficiency score
        val todayScores = efficiencyScoreRepository.findByUserIdAndDateRange(userId, today, today)
        val todayScore = todayScores.firstOrNull()?.scorePercent

        // Efficiency trend: compare current score against 7-day average
        val trend = computeEfficiencyTrend(userId, today, todayScore)

        return ResponseEntity.ok(
            DashboardSummaryResponse(
                pendingTodosCount = pendingTodosCount,
                activeGoalsCount = counts.activeGoalCount,
                activeHabitsCount = counts.activeHabitCount,
                todayEfficiencyScore = todayScore,
                efficiencyTrend = trend,
                activeGoalCount = counts.activeGoalCount,
                todayTodoCount = counts.todayTodoCount,
                activeHabitCount = counts.activeHabitCount
            )
        )
    }

    /**
     * GET /v1/dashboard/activity?page={n}&size=20
     * Returns a paginated activity feed of recent user actions.
     * Default page size is 20 items.
     *
     * Requirements: 20.6, 20.10
     */
    @GetMapping("/v1/dashboard/activity")
    suspend fun getActivityFeed(
        @RequestParam("page", defaultValue = "0") page: Int,
        @RequestParam("size", defaultValue = "20") size: Int
    ): ResponseEntity<ActivityFeedResponse> {
        val userId = authenticatedUser.getUserId()
        val effectiveSize = size.coerceIn(1, 100)
        val effectivePage = page.coerceAtLeast(0)

        // Use change_log entries as the activity feed source — they represent
        // all recent changes across entities (todos, goals, habits, etc.)
        val allEntries = changeLogRepository.findSince(
            userId = userId,
            since = java.time.Instant.EPOCH
        )

        // Sort by timestamp descending for most recent first
        val sorted = allEntries.sortedByDescending { it.timestamp }
        val totalItems = sorted.size.toLong()

        // Paginate
        val offset = effectivePage * effectiveSize
        val pageItems = sorted.drop(offset).take(effectiveSize)

        val feedEntries = pageItems.map { entry ->
            ActivityFeedEntry(
                id = entry.id,
                actionType = inferActionType(entry.field, entry.value),
                entityType = entry.entityType,
                entityId = entry.entityId,
                entityName = extractEntityName(entry),
                timestamp = entry.timestamp
            )
        }

        return ResponseEntity.ok(
            ActivityFeedResponse(
                items = feedEntries,
                page = effectivePage,
                size = effectiveSize,
                totalItems = totalItems
            )
        )
    }

    // ===== Private Helpers =====

    /**
     * Computes the efficiency trend by comparing the current (or most recent) score
     * to the 7-day rolling average.
     *
     * Requirements: 20.3
     */
    private suspend fun computeEfficiencyTrend(
        userId: java.util.UUID,
        today: LocalDate,
        todayScore: Int?
    ): EfficiencyTrend {
        val weekAgo = today.minusDays(7)
        val recentScores = efficiencyScoreRepository.findByUserIdAndDateRange(userId, weekAgo, today.minusDays(1))
        val validScores = recentScores.mapNotNull { it.scorePercent }

        // Need at least 2 days of data to compute a trend
        if (validScores.size < 2) {
            return EfficiencyTrend.NOT_ENOUGH_DATA
        }

        val currentScore = todayScore ?: return EfficiencyTrend.NOT_ENOUGH_DATA
        val average = validScores.average()

        return when {
            currentScore > average + 5 -> EfficiencyTrend.IMPROVING
            currentScore < average - 5 -> EfficiencyTrend.DECLINING
            else -> EfficiencyTrend.STABLE
        }
    }

    /**
     * Infers the action type from the change log entry field and value.
     */
    private fun inferActionType(field: String, value: String?): String {
        return when {
            value == null -> "deleted"
            field == "isCompleted" && value == "true" -> "completed"
            field == "isCompleted" && value == "false" -> "uncompleted"
            field == "status" && value.contains("COMPLETED", ignoreCase = true) -> "completed"
            field == "status" && value.contains("MISSED", ignoreCase = true) -> "missed"
            else -> "updated"
        }
    }

    /**
     * Extracts a display name from the change log entry, if available.
     * For text/name field changes, returns the value as the entity name.
     */
    private fun extractEntityName(entry: com.dopashift.domain.entity.ChangeLogEntry): String? {
        return when (entry.field) {
            "text", "name" -> entry.value
            else -> null
        }
    }
}
