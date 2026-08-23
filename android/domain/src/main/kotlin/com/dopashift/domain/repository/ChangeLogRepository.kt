package com.dopashift.domain.repository

import com.dopashift.domain.entity.ChangeLogEntry
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

/**
 * Repository port interface for the append-only change log.
 */
interface ChangeLogRepository {
    suspend fun append(entry: ChangeLogEntry)
    suspend fun findSince(userId: UUID, since: Instant): List<ChangeLogEntry>
    suspend fun countByUserId(userId: UUID): Long
    suspend fun archiveOlderThan(cutoff: Instant)

    /**
     * Observes the most recent change log entries for a user, paginated.
     * Used to drive the Dashboard activity feed.
     */
    fun observeRecent(userId: UUID, limit: Int, offset: Int): Flow<List<ChangeLogEntry>>
}
