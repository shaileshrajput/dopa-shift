package com.dopashift.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dopashift.data.local.entity.LocalSyncState

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE `key` = :key")
    suspend fun getByKey(key: String): LocalSyncState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: LocalSyncState)
}
