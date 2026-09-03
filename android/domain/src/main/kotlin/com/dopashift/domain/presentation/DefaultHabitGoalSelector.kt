package com.dopashift.domain.presentation

import com.dopashift.domain.entity.GoalProfile

/**
 * Resolves the goal that a habit Creation_Sheet pre-selects by default.
 *
 * Per DQC-3.1 (Property 15), when the user opens "New Habit" with one or more active
 * [GoalProfile]s, the sheet's goal-selection step defaults to the most recently updated
 * active goal. This helper isolates that choice as pure, total, and deterministic Kotlin —
 * no Android/Compose dependency — so it runs under fast JVM tests and can be reused by the
 * `ui` layer and the `interception` overlay without a parallel implementation.
 */
object DefaultHabitGoalSelector {

    /**
     * Returns the active goal with the most recent `updatedAt` from [goals], or `null` when
     * [goals] contains no active goal (in which case the sheet begins with Inline_Goal_Capture
     * instead, per DQC-3.2).
     *
     * Only active goals ([GoalProfile.isActive] `== true`) are eligible — an inactive goal is
     * never defaulted even if it is the most recently updated overall.
     *
     * Selection is by the latest `updatedAt`. Ties (equal `updatedAt`) are broken
     * deterministically by the goal's `id`, so identical input always yields identical output
     * regardless of the order in which goals are supplied.
     */
    fun selectDefault(goals: List<GoalProfile>): GoalProfile? =
        goals
            .asSequence()
            .filter { it.isActive }
            .maxWithOrNull(
                compareBy<GoalProfile> { it.updatedAt }
                    .thenBy { it.id }
            )
}
