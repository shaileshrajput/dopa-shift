package com.dopashift.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dopashift.data.local.entity.LocalGoal
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun findById(id: String): LocalGoal?

    @Query("SELECT * FROM goals WHERE userId = :userId")
    suspend fun findByUserId(userId: String): List<LocalGoal>

    @Query("SELECT * FROM goals WHERE userId = :userId")
    fun observeByUserId(userId: String): Flow<List<LocalGoal>>

    @Query("SELECT * FROM goals WHERE userId = :userId AND name = :name LIMIT 1")
    suspend fun findByUserIdAndName(userId: String, name: String): LocalGoal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: LocalGoal)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM goals WHERE userId = :userId AND isActive = 1")
    suspend fun countActiveByUserId(userId: String): Int

    @Query("SELECT COUNT(*) FROM goals WHERE userId = :userId AND isActive = 1")
    fun observeActiveCountByUserId(userId: String): Flow<Int>
}
