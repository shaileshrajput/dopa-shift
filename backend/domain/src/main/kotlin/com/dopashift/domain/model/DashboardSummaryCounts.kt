package com.dopashift.domain.model

/**
 * Per-entity-type counts for a single user, sufficient to distinguish the
 * Dashboard's Zero_State from Partial_State in one response (DQC-5.3, Backend Delta 2).
 *
 * - [activeGoalCount]: number of active Goal_Profiles for the user.
 * - [todayTodoCount]: total DailyTodoItems for the current date in the user's time zone
 *   (all items for the day, completed or not — a day of only completed to-dos is NOT Zero_State).
 * - [activeHabitCount]: number of active (unfinished) Habit_Tracks for the user.
 *
 * Zero_State holds if and only if all three counts are zero; otherwise the Dashboard
 * renders Partial_State/populated. Every count is computed scoped by the authenticated user_id.
 */
data class DashboardSummaryCounts(
    val activeGoalCount: Int,
    val todayTodoCount: Int,
    val activeHabitCount: Int
) {
    init {
        require(activeGoalCount >= 0) { "activeGoalCount must be non-negative" }
        require(todayTodoCount >= 0) { "todayTodoCount must be non-negative" }
        require(activeHabitCount >= 0) { "activeHabitCount must be non-negative" }
    }

    /** True only when the user has zero goals, zero to-dos today, and zero active habits. */
    val isZeroState: Boolean
        get() = activeGoalCount == 0 && todayTodoCount == 0 && activeHabitCount == 0
}
