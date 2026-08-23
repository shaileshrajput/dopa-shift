package com.dopashift.domain.repository

import com.dopashift.domain.entity.HabitTrack
import java.util.UUID

/**
 * Port interface for habit track persistence.
 * Implementations live in the infrastructure layer.
 */
interface HabitTrackRepository {
    suspend fun findById(id: UUID): HabitTrack?
    suspend fun findActiveByGoalId(goalId: UUID): List<HabitTrack>
    suspend fun findActiveByUserId(userId: UUID): List<HabitTrack>
    suspend fun save(track: HabitTrack): HabitTrack
    suspend fun deleteByGoalId(goalId: UUID)
    suspend fun reassignToGoal(fromGoalId: UUID, toGoalId: UUID)
}
