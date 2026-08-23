package com.dopashift.domain.repository

import com.dopashift.domain.entity.InterceptionRule
import java.util.UUID

/**
 * Port interface for interception rule persistence.
 * Implementations live in the infrastructure layer.
 */
interface InterceptionRuleRepository {
    suspend fun findById(id: UUID): InterceptionRule?
    suspend fun findByGoalId(goalId: UUID): List<InterceptionRule>
    suspend fun findByUserId(userId: UUID): List<InterceptionRule>
    suspend fun reassignToGoal(fromGoalId: UUID, toGoalId: UUID)
    suspend fun deleteByGoalId(goalId: UUID)
    suspend fun delete(id: UUID)
    suspend fun save(rule: InterceptionRule): InterceptionRule
}
