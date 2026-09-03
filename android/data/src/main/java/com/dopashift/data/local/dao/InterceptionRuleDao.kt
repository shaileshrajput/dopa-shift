package com.dopashift.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dopashift.data.local.entity.LocalInterceptionRule
import kotlinx.coroutines.flow.Flow

@Dao
interface InterceptionRuleDao {

    /**
     * Finds the active rule for a package, scoped to the authenticated [userId] so that no
     * caller can read another user's rule via a package name alone (Requirement 3.8).
     */
    @Query(
        "SELECT * FROM interception_rules " +
            "WHERE userId = :userId AND appPackageName = :packageName AND isActive = 1 LIMIT 1"
    )
    suspend fun findActiveByPackageName(
        userId: String,
        packageName: String
    ): LocalInterceptionRule?

    /**
     * Legacy non-user-scoped lookup retained only for callers not yet migrated to the
     * user-scoped [InterceptionRuleRepository] port. Prefer [findActiveByPackageName] with a
     * [userId]. Slated for removal once [AllowanceTracker] routes lookups through the port.
     */
    @Deprecated(
        message = "Use the user-scoped findActiveByPackageName(userId, packageName) instead.",
        replaceWith = ReplaceWith("findActiveByPackageName(userId, packageName)")
    )
    @Query("SELECT * FROM interception_rules WHERE appPackageName = :packageName AND isActive = 1 LIMIT 1")
    suspend fun findActiveByPackageName(packageName: String): LocalInterceptionRule?

    @Query("SELECT * FROM interception_rules WHERE userId = :userId AND isActive = 1")
    suspend fun findActiveByUserId(userId: String): List<LocalInterceptionRule>

    @Query("SELECT * FROM interception_rules WHERE userId = :userId")
    suspend fun listForUser(userId: String): List<LocalInterceptionRule>

    @Query("SELECT * FROM interception_rules WHERE userId = :userId")
    fun observeForUser(userId: String): Flow<List<LocalInterceptionRule>>

    @Query("SELECT * FROM interception_rules WHERE id = :id")
    suspend fun findById(id: String): LocalInterceptionRule?

    @Query(
        "UPDATE interception_rules SET pausedForDate = :pausedForDate " +
            "WHERE id = :id AND userId = :userId"
    )
    suspend fun setPausedForDate(userId: String, id: String, pausedForDate: String?): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: LocalInterceptionRule)

    @Query("DELETE FROM interception_rules WHERE id = :id")
    suspend fun deleteById(id: String)
}
