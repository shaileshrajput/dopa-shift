package com.dopashift.domain.usecase.goal

import com.dopashift.domain.exception.NoActiveGoalsException
import com.dopashift.domain.repository.GoalRepository
import java.util.UUID

/**
 * Guard that blocks creation of HabitTracks or InterceptionRules
 * when the user has zero active goals.
 *
 * Requirement 1.4: IF a user has zero active goals, THEN the system SHALL block
 * creation of Habit_Tracks or interception rules that depend on a goal reference
 * and display an error message indicating that at least one active goal is required.
 */
class ActiveGoalGuard(
    private val goalRepository: GoalRepository
) {

    /**
     * Ensures the user has at least one active goal.
     * Throws [NoActiveGoalsException] if the user has zero active goals.
     */
    suspend fun requireActiveGoal(userId: UUID) {
        val activeCount = goalRepository.countActiveByUserId(userId)
        if (activeCount == 0) {
            throw NoActiveGoalsException()
        }
    }
}
