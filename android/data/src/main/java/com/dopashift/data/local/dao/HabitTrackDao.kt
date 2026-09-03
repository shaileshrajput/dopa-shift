package com.dopashift.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dopashift.data.local.entity.LocalHabitCheckpoint
import com.dopashift.data.local.entity.LocalHabitTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitTrackDao {
    @Query("SELECT * FROM habit_tracks WHERE id = :id")
    suspend fun findById(id: String): LocalHabitTrack?

    @Query("SELECT * FROM habit_tracks WHERE goalId = :goalId AND isFinished = 0")
    suspend fun findActiveByGoalId(goalId: String): List<LocalHabitTrack>

    @Query("SELECT * FROM habit_tracks WHERE userId = :userId AND isFinished = 0")
    suspend fun findActiveByUserId(userId: String): List<LocalHabitTrack>

    @Query("SELECT * FROM habit_tracks WHERE userId = :userId AND isFinished = 0")
    fun observeActiveByUserId(userId: String): Flow<List<LocalHabitTrack>>

    @Query("SELECT COUNT(*) FROM habit_tracks WHERE userId = :userId AND isFinished = 0")
    fun observeActiveCountByUserId(userId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: LocalHabitTrack)

    @Query("SELECT * FROM habit_checkpoints WHERE habitTrackId = :trackId ORDER BY dayNumber")
    suspend fun getCheckpoints(trackId: String): List<LocalHabitCheckpoint>

    @Query("SELECT * FROM habit_checkpoints WHERE id = :checkpointId")
    suspend fun findCheckpointById(checkpointId: String): LocalHabitCheckpoint?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheckpoint(checkpoint: LocalHabitCheckpoint)

    @Query("DELETE FROM habit_checkpoints WHERE habitTrackId = :trackId")
    suspend fun deleteCheckpointsForTrack(trackId: String)

    @Query("DELETE FROM habit_tracks WHERE id = :id")
    suspend fun deleteTrack(id: String)
}
