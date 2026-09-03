package com.dopashift.application.dashboard

import com.dopashift.domain.model.DashboardSummaryCounts
import com.dopashift.domain.repository.DailyTodoRepository
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import com.dopashift.domain.repository.UserProfileRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Query service that assembles the Dashboard summary counts for a single user.
 *
 * Returns per-entity-type counts (active goals, today's to-dos, active habits) that are
 * sufficient to distinguish Zero_State from Partial_State in one response (DQC-5.3,
 * Backend Delta 2). Every count is computed scoped by the supplied [userId] — the caller
 * MUST pass the authenticated user's id, never a client-supplied identifier.
 *
 * "Today" is resolved in the user's configured time zone (from their profile); if no
 * profile or an invalid time zone is stored, it falls back to the system clock's zone.
 *
 * Requirements: 5.3
 */
class DashboardSummaryQueryService(
    private val goalRepository: GoalRepository,
    private val dailyTodoRepository: DailyTodoRepository,
    private val habitTrackRepository: HabitTrackRepository,
    private val userProfileRepository: UserProfileRepository,
    private val clock: Clock = Clock.systemUTC()
) {

    /**
     * Computes the summary counts for [userId].
     *
     * @param userId the authenticated user's id; all counts are scoped to this user.
     */
    suspend fun getSummary(userId: UUID): DashboardSummaryCounts {
        val today = todayFor(userId)

        val activeGoalCount = goalRepository.countActiveByUserId(userId)
        val todayTodoCount = dailyTodoRepository.countByUserIdAndDate(userId, today)
        val activeHabitCount = habitTrackRepository.countActiveByUserId(userId)

        return DashboardSummaryCounts(
            activeGoalCount = activeGoalCount,
            todayTodoCount = todayTodoCount,
            activeHabitCount = activeHabitCount
        )
    }

    /**
     * Resolves the current date in the user's configured time zone, defaulting to the
     * clock's zone when the profile is missing or holds an unparseable time zone id.
     */
    private suspend fun todayFor(userId: UUID): LocalDate {
        val profile = userProfileRepository.findById(userId)
        val zone = profile?.timezone?.let { parseZoneOrNull(it) } ?: clock.zone
        return LocalDate.now(clock.withZone(zone))
    }

    private fun parseZoneOrNull(zoneId: String): ZoneId? =
        try {
            ZoneId.of(zoneId)
        } catch (_: Exception) {
            null
        }
}
