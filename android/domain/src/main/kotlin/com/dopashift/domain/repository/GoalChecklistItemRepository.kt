package com.dopashift.domain.repository

import com.dopashift.domain.entity.GoalChecklistItem
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Repository port interface for goal checklist item persistence.
 * Implementations live in the data layer.
 */
interface GoalChecklistItemRepository {
    suspend fun findById(id: UUID): GoalChecklistItem?
    suspend fun findByGoalId(goalId: UUID): List<GoalChecklistItem>
    suspend fun findByUserId(userId: UUID): List<GoalChecklistItem>
    suspend fun save(item: GoalChecklistItem): GoalChecklistItem
    suspend fun delete(id: UUID)
    suspend fun deleteByGoalId(goalId: UUID)
    suspend fun reassignToGoal(fromGoalId: UUID, toGoalId: UUID)

    /**
     * Observes checklist items for a goal as a reactive Flow.
     * Room emits a new list whenever the underlying data changes.
     */
    fun observeByGoalId(goalId: UUID): Flow<List<GoalChecklistItem>>

    /**
     * Observes all checklist items for a user as a reactive Flow.
     * Used by the Dashboard to compute per-goal progress.
     */
    fun observeByUserId(userId: UUID): Flow<List<GoalChecklistItem>>
}
