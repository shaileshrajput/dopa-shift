package com.dopashift.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dopashift.data.local.entity.LocalEfficiencyScore
import kotlinx.coroutines.flow.Flow

@Dao
interface EfficiencyScoreDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(score: LocalEfficiencyScore)

    @Query("SELECT * FROM efficiency_scores WHERE userId = :userId AND scoreDate BETWEEN :from AND :to ORDER BY scoreDate")
    suspend fun findByUserIdAndDateRange(userId: String, from: String, to: String): List<LocalEfficiencyScore>

    @Query("SELECT * FROM efficiency_scores WHERE userId = :userId AND scoreDate BETWEEN :from AND :to ORDER BY scoreDate")
    fun observeByUserIdAndDateRange(userId: String, from: String, to: String): Flow<List<LocalEfficiencyScore>>
}
