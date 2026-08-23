package com.dopashift.domain.repository

import com.dopashift.domain.entity.ChangeLogEntry
import java.time.Instant
import java.util.UUID

/**
 * Port interface for the append-only change log used by the sync engine.
 * Implementations live in the infrastructure layer.
 */
interface ChangeLogRepository {
    suspend fun append(entry: ChangeLogEntry)
    suspend fun findSince(userId: UUID, since: Instant): List<ChangeLogEntry>
    suspend fun findLatestByEntityIdAndField(entityId: UUID, field: String): ChangeLogEntry?
    suspend fun countByUserId(userId: UUID): Long
    suspend fun countTotal(): Long
    suspend fun archiveOlderThan(cutoff: Instant)
}
