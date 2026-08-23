package com.dopashift.domain.repository

import com.dopashift.domain.entity.AuditLogEntry
import java.time.Instant

/**
 * Port interface for the immutable audit log with hash-chain integrity.
 * Implementations live in the infrastructure layer.
 */
interface AuditLogRepository {
    suspend fun append(entry: AuditLogEntry)
    suspend fun findByCorrelationId(correlationId: String): List<AuditLogEntry>
    suspend fun getLastEntry(): AuditLogEntry?

    /**
     * Purges audit log entries older than the given cutoff timestamp.
     * Used by the retention-policy background job.
     *
     * @param cutoff entries with timestamp strictly before this instant are deleted
     * @param operationTypes if non-empty, only entries with these operation types are purged;
     *                       if empty, all entries older than cutoff are purged
     * @return the number of entries purged
     *
     * Requirements: 18.5
     */
    suspend fun purgeOlderThan(cutoff: Instant, operationTypes: Set<String> = emptySet()): Long
}
