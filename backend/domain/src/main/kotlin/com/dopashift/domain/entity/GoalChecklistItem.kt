package com.dopashift.domain.entity

import java.time.Instant
import java.util.UUID

/**
 * A task/checklist entry scoped to exactly one GoalProfile.
 * Distinct from DailyTodoItem — uses the same sync engine but different data model.
 *
 * Validation constraints:
 * - text: 1–500 characters
 */
data class GoalChecklistItem(
    val id: UUID,
    val goalId: UUID,
    val userId: UUID,
    val text: String,
    val isCompleted: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(text.isNotBlank() && text.length in 1..500) {
            "Checklist item text must be between 1 and 500 characters"
        }
    }
}
