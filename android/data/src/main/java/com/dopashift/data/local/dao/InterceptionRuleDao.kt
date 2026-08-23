package com.dopashift.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dopashift.data.local.entity.LocalInterceptionRule

@Dao
interface InterceptionRuleDao {

    @Query("SELECT * FROM interception_rules WHERE appPackageName = :packageName AND isActive = 1 LIMIT 1")
    suspend fun findActiveByPackageName(packageName: String): LocalInterceptionRule?

    @Query("SELECT * FROM interception_rules WHERE userId = :userId AND isActive = 1")
    suspend fun findActiveByUserId(userId: String): List<LocalInterceptionRule>

    @Query("SELECT * FROM interception_rules WHERE id = :id")
    suspend fun findById(id: String): LocalInterceptionRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: LocalInterceptionRule)

    @Query("DELETE FROM interception_rules WHERE id = :id")
    suspend fun deleteById(id: String)
}
