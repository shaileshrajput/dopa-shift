package com.dopashift.domain.repository

import com.dopashift.domain.entity.HabitTrack
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Repository port interface for habit track persistence.
 */
interface HabitTrackRepository {
    suspend fun findById(id: UUID): HabitTrack?
    suspend fun findActiveByGoalId(goalId: UUID): List<HabitTrack>
    suspend fun findActiveByUserId(userId: UUID): List<HabitTrack>
    suspend fun save(track: HabitTrack): HabitTrack

    /**
     * Observes active habit tracks for a user as a reactive Flow.
     * Room emits a new list whenever the underlying data changes.
     */
    fun observeActiveByUserId(userId: UUID): Flow<List<HabitTrack>>
}
