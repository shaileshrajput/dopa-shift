package com.dopashift.domain.repository

import com.dopashift.domain.entity.GoalProfile
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Repository port interface for goal profile persistence.
 */
interface GoalRepository {
    suspend fun findById(id: UUID): GoalProfile?
    suspend fun findByUserId(userId: UUID): List<GoalProfile>
    suspend fun findByUserIdAndName(userId: UUID, name: String): GoalProfile?
    suspend fun save(goal: GoalProfile): GoalProfile
    suspend fun delete(id: UUID)
    suspend fun countActiveByUserId(userId: UUID): Int

    /**
     * Observes all goals for a user as a reactive Flow.
     * Room emits a new list whenever the underlying data changes.
     */
    fun observeByUserId(userId: UUID): Flow<List<GoalProfile>>
}
