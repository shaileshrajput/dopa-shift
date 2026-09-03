package com.dopashift.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dopashift.data.local.entity.LocalInterceptActionAudit

/**
 * DAO for [LocalInterceptActionAudit] — repetitive app-limit intercept-action audit log.
 *
 * Records are LOCAL ONLY and never synced (Req 3.4, 3.5). All reads are scoped to the
 * authenticated user_id; no query accepts another user's data.
 */
@Dao
interface InterceptActionAuditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LocalInterceptActionAudit)

    @Query(
        "SELECT * FROM intercept_action_audit WHERE userId = :userId ORDER BY recordedAt ASC"
    )
    suspend fun listByUser(userId: String): List<LocalInterceptActionAudit>
}
