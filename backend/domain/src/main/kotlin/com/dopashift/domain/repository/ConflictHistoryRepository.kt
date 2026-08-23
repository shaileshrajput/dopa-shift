package com.dopashift.domain.repository

import com.dopashift.domain.entity.ConflictHistoryEntry
import java.time.Instant
import java.util.UUID

/**
 * Port interface for the conflict history store.
 * Stores superseded change log entries that lost conflict resolution.
 * Entries are retained for at least 90 days (Requirement 4.7).
 */
interface ConflictHistoryRepository {
    suspend fun save(entry: ConflictHistoryEntry)
    suspend fun findByUserId(userId: UUID, since: Instant): List<ConflictHistoryEntry>
    suspend fun deleteOlderThan(cutoff: Instant)
}
