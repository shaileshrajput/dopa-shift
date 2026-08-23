package com.dopashift.domain.usecase.goal

import java.util.UUID

/**
 * Represents the user's chosen action when deleting a goal with dependent items.
 */
sealed class DeleteGoalAction {
    /** Delete all dependent items (GoalChecklistItems, HabitTracks, InterceptionRules). */
    data object DeleteAll : DeleteGoalAction()

    /** Reassign all dependent items to another existing goal. */
    data class Reassign(val targetGoalId: UUID) : DeleteGoalAction()
}
