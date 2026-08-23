package com.dopashift.domain.repository

import com.dopashift.domain.entity.GoalChecklistItem
import java.util.UUID

/**
 * Port interface for goal checklist item persistence.
 * Implementations live in the infrastructure layer.
 */
interface GoalChecklistItemRepository {
    suspend fun findById(id: UUID): GoalChecklistItem?
    suspend fun findByGoalId(goalId: UUID): List<GoalChecklistItem>
    suspend fun findByUserId(userId: UUID): List<GoalChecklistItem>
    suspend fun save(item: GoalChecklistItem): GoalChecklistItem
    suspend fun delete(id: UUID)
    suspend fun reassignToGoal(fromGoalId: UUID, toGoalId: UUID)
    suspend fun deleteByGoalId(goalId: UUID)
}
