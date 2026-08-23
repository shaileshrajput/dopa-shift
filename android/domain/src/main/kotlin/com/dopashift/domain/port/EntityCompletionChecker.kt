package com.dopashift.domain.port

import com.dopashift.domain.entity.ReminderEntityType
import java.util.UUID

/**
 * Port interface for checking whether a reminder's linked entity is complete.
 *
 * This decouples the reminder evaluation logic from specific repository implementations,
 * allowing condition evaluation without direct dependency on entity repositories.
 */
interface EntityCompletionChecker {

    /**
     * Returns true if the entity identified by [entityId] of [entityType] is complete
     * (e.g., a DailyTodoItem is marked done, a GoalChecklistItem is checked off,
     * or a HabitCheckpoint is completed).
     */
    suspend fun isComplete(entityType: ReminderEntityType, entityId: UUID): Boolean
}
