package com.dopashift.domain.repository

import com.dopashift.domain.entity.GoalProfile
import java.util.UUID

/**
 * Port interface for goal persistence.
 * Implementations live in the infrastructure layer.
 */
interface GoalRepository {
    suspend fun findById(id: UUID): GoalProfile?
    suspend fun findByUserId(userId: UUID): List<GoalProfile>
    suspend fun findByUserIdAndName(userId: UUID, name: String): GoalProfile?
    suspend fun save(goal: GoalProfile): GoalProfile
    suspend fun delete(id: UUID)
    suspend fun countActiveByUserId(userId: UUID): Int
}
