package com.dopashift.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dopashift.data.local.entity.LocalDailyTodo
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyTodoDao {
    @Query("SELECT * FROM daily_todo_items WHERE id = :id")
    suspend fun findById(id: String): LocalDailyTodo?

    @Query("SELECT * FROM daily_todo_items WHERE userId = :userId AND dayDate = :date")
    suspend fun findByUserIdAndDate(userId: String, date: String): List<LocalDailyTodo>

    @Query("SELECT * FROM daily_todo_items WHERE userId = :userId AND dayDate = :date")
    fun observeByUserIdAndDate(userId: String, date: String): Flow<List<LocalDailyTodo>>

    @Query("SELECT COUNT(*) FROM daily_todo_items WHERE userId = :userId AND dayDate = :date")
    suspend fun countByUserIdAndDate(userId: String, date: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LocalDailyTodo)

    @Query("DELETE FROM daily_todo_items WHERE id = :id")
    suspend fun deleteById(id: String)
}
