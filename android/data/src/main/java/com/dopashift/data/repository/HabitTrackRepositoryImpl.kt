package com.dopashift.data.repository

import com.dopashift.data.local.dao.HabitTrackDao
import com.dopashift.data.local.entity.LocalHabitCheckpoint
import com.dopashift.data.local.entity.LocalHabitTrack
import com.dopashift.domain.entity.CheckpointStatus
import com.dopashift.domain.entity.HabitCheckpoint
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.repository.HabitTrackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [HabitTrackRepository].
 * Maps between [LocalHabitTrack]/[LocalHabitCheckpoint] (Room entities) and
 * [HabitTrack]/[HabitCheckpoint] (domain entities), handling UUID↔String,
 * Instant↔Long, LocalDate↔String, and CheckpointStatus enum↔String conversions.
 */
@Singleton
class HabitTrackRepositoryImpl @Inject constructor(
    private val habitTrackDao: HabitTrackDao
) : HabitTrackRepository {

    override suspend fun findById(id: UUID): HabitTrack? {
        val track = habitTrackDao.findById(id.toString()) ?: return null
        val checkpoints = habitTrackDao.getCheckpoints(id.toString())
        return track.toDomain(checkpoints)
    }

    override suspend fun findActiveByGoalId(goalId: UUID): List<HabitTrack> {
        return habitTrackDao.findActiveByGoalId(goalId.toString()).map { track ->
            val checkpoints = habitTrackDao.getCheckpoints(track.id)
            track.toDomain(checkpoints)
        }
    }

    override suspend fun findActiveByUserId(userId: UUID): List<HabitTrack> {
        return habitTrackDao.findActiveByUserId(userId.toString()).map { track ->
            val checkpoints = habitTrackDao.getCheckpoints(track.id)
            track.toDomain(checkpoints)
        }
    }

    override suspend fun save(track: HabitTrack): HabitTrack {
        habitTrackDao.upsert(track.toLocal())
        track.checkpoints.forEach { checkpoint ->
            habitTrackDao.upsertCheckpoint(checkpoint.toLocal())
        }
        return track
    }

    override suspend fun delete(id: UUID) {
        // Remove the checkpoints first, then the track, so no orphan checkpoints remain.
        habitTrackDao.deleteCheckpointsForTrack(id.toString())
        habitTrackDao.deleteTrack(id.toString())
    }

    override fun observeActiveByUserId(userId: UUID): Flow<List<HabitTrack>> {
        return habitTrackDao.observeActiveByUserId(userId.toString())
            .map { locals ->
                locals.map { track ->
                    val checkpoints = habitTrackDao.getCheckpoints(track.id)
                    track.toDomain(checkpoints)
                }
            }
    }

    private fun LocalHabitTrack.toDomain(checkpoints: List<LocalHabitCheckpoint>): HabitTrack {
        return HabitTrack(
            id = UUID.fromString(id),
            goalId = UUID.fromString(goalId),
            userId = UUID.fromString(userId),
            startDate = LocalDate.parse(startDate),
            currentDay = currentDay,
            isFinished = isFinished,
            checkpoints = checkpoints.map { it.toDomain() }
        )
    }

    private fun HabitTrack.toLocal(): LocalHabitTrack {
        return LocalHabitTrack(
            id = id.toString(),
            goalId = goalId.toString(),
            userId = userId.toString(),
            startDate = startDate.toString(),
            currentDay = currentDay,
            isFinished = isFinished,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun LocalHabitCheckpoint.toDomain(): HabitCheckpoint {
        return HabitCheckpoint(
            id = UUID.fromString(id),
            habitTrackId = UUID.fromString(habitTrackId),
            dayNumber = dayNumber,
            description = description,
            status = CheckpointStatus.valueOf(status),
            completedAt = completedAt?.let { Instant.ofEpochMilli(it) }
        )
    }

    private fun HabitCheckpoint.toLocal(): LocalHabitCheckpoint {
        return LocalHabitCheckpoint(
            id = id.toString(),
            habitTrackId = habitTrackId.toString(),
            dayNumber = dayNumber,
            description = description,
            status = status.name,
            completedAt = completedAt?.toEpochMilli()
        )
    }
}
