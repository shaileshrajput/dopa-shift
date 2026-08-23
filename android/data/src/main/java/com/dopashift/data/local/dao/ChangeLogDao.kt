package com.dopashift.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.dopashift.data.local.entity.LocalChangeLogEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface ChangeLogDao {
    @Insert
    suspend fun insert(entry: LocalChangeLogEntry)

    @Query("SELECT * FROM change_log WHERE userId = :userId AND timestamp > :since ORDER BY timestamp")
    suspend fun findSince(userId: String, since: Long): List<LocalChangeLogEntry>

    @Query("SELECT * FROM change_log WHERE userId = :userId AND isSynced = 0 ORDER BY timestamp")
    suspend fun findUnsyncedByUserId(userId: String): List<LocalChangeLogEntry>

    @Query("SELECT * FROM change_log WHERE userId = :userId AND isSynced = 0")
    fun observeUnsyncedByUserId(userId: String): Flow<List<LocalChangeLogEntry>>

    @Query("UPDATE change_log SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("SELECT COUNT(*) FROM change_log WHERE userId = :userId")
    suspend fun countByUserId(userId: String): Long

    @Query("DELETE FROM change_log WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT * FROM change_log WHERE userId = :userId ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun observeRecent(userId: String, limit: Int, offset: Int): Flow<List<LocalChangeLogEntry>>
}
