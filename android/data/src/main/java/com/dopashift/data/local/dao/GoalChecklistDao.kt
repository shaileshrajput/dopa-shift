package com.dopashift.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dopashift.data.local.entity.LocalGoalChecklist
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalChecklistDao {
    @Query("SELECT * FROM goal_checklist_items WHERE id = :id")
    suspend fun findById(id: String): LocalGoalChecklist?

    @Query("SELECT * FROM goal_checklist_items WHERE goalId = :goalId")
    suspend fun findByGoalId(goalId: String): List<LocalGoalChecklist>

    @Query("SELECT * FROM goal_checklist_items WHERE userId = :userId")
    suspend fun findByUserId(userId: String): List<LocalGoalChecklist>

    @Query("SELECT * FROM goal_checklist_items WHERE goalId = :goalId")
    fun observeByGoalId(goalId: String): Flow<List<LocalGoalChecklist>>

    @Query("SELECT * FROM goal_checklist_items WHERE userId = :userId")
    fun observeByUserId(userId: String): Flow<List<LocalGoalChecklist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LocalGoalChecklist)

    @Query("DELETE FROM goal_checklist_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM goal_checklist_items WHERE goalId = :goalId")
    suspend fun deleteByGoalId(goalId: String)

    @Query("UPDATE goal_checklist_items SET goalId = :toGoalId WHERE goalId = :fromGoalId")
    suspend fun reassignToGoal(fromGoalId: String, toGoalId: String)
}
