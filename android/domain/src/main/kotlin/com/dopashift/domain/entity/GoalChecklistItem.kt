package com.dopashift.domain.entity

import java.time.Instant
import java.util.UUID

/**
 * A task/checklist entry scoped to exactly one GoalProfile.
 * Distinct from DailyTodoItem.
 */
data class GoalChecklistItem(
    val id: UUID,
    val goalId: UUID,
    val userId: UUID,
    val text: String,
    val isCompleted: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant
)
