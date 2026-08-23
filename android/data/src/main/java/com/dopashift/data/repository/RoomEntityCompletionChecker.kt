package com.dopashift.data.repository

import com.dopashift.data.local.dao.DailyTodoDao
import com.dopashift.data.local.dao.GoalChecklistDao
import com.dopashift.data.local.dao.HabitTrackDao
import com.dopashift.domain.entity.ReminderEntityType
import com.dopashift.domain.port.EntityCompletionChecker
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [EntityCompletionChecker].
 *
 * Queries Room DAOs to determine whether a reminder's linked entity is complete.
 * This enables offline-capable conditional reminder evaluation
 * (Requirement 5.4, 5.15).
 */
@Singleton
class RoomEntityCompletionChecker @Inject constructor(
    private val dailyTodoDao: DailyTodoDao,
    private val goalChecklistDao: GoalChecklistDao,
    private val habitTrackDao: HabitTrackDao
) : EntityCompletionChecker {

    override suspend fun isComplete(entityType: ReminderEntityType, entityId: UUID): Boolean {
        val id = entityId.toString()
        return when (entityType) {
            ReminderEntityType.DAILY_TODO -> {
                val item = dailyTodoDao.findById(id)
                // If item is null (deleted), treat as complete to suppress the reminder
                item?.isCompleted ?: true
            }

            ReminderEntityType.GOAL_CHECKLIST -> {
                val item = goalChecklistDao.findById(id)
                item?.isCompleted ?: true
            }

            ReminderEntityType.HABIT_CHECKPOINT -> {
                // For habit checkpoints, we look up by checkpoint ID in the habit track DAO
                val checkpoints = findHabitCheckpointStatus(id)
                checkpoints
            }
        }
    }

    /**
     * Looks up a habit checkpoint status via the HabitTrackDao.
     * Returns true if the checkpoint is COMPLETED, false otherwise.
     */
    private suspend fun findHabitCheckpointStatus(checkpointId: String): Boolean {
        // The habit checkpoint is stored separately; look up directly via dao
        val checkpoint = habitTrackDao.findCheckpointById(checkpointId)
        return checkpoint?.status == "COMPLETED"
    }
}
