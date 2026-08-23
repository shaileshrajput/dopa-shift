package com.dopashift.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dopashift.data.local.entity.LocalReminder
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun findById(id: String): LocalReminder?

    @Query("SELECT * FROM reminders WHERE userId = :userId AND isActive = 1")
    suspend fun findActiveByUserId(userId: String): List<LocalReminder>

    @Query("SELECT * FROM reminders WHERE entityId = :entityId")
    suspend fun findByEntityId(entityId: String): List<LocalReminder>

    @Query("SELECT * FROM reminders WHERE userId = :userId AND isActive = 1")
    fun observeActiveByUserId(userId: String): Flow<List<LocalReminder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: LocalReminder)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: String)
}
