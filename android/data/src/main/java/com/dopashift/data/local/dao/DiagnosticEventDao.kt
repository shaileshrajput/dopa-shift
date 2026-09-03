package com.dopashift.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dopashift.data.local.entity.LocalDiagnosticEvent

/**
 * DAO for [LocalDiagnosticEvent] — Dashboard quick-create diagnostics.
 *
 * Records are LOCAL ONLY and never synced raw (DQC-6.4/6.5). The aggregator reads counts
 * from here and syncs only aggregated summaries; raw rows are cleared after aggregation.
 */
@Dao
interface DiagnosticEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: LocalDiagnosticEvent)

    @Query("SELECT * FROM quick_create_diagnostics ORDER BY occurredAt ASC")
    suspend fun getAll(): List<LocalDiagnosticEvent>

    @Query("SELECT COUNT(*) FROM quick_create_diagnostics WHERE eventType = :eventType")
    suspend fun countByType(eventType: String): Int

    @Query("DELETE FROM quick_create_diagnostics WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM quick_create_diagnostics")
    suspend fun clear()
}
