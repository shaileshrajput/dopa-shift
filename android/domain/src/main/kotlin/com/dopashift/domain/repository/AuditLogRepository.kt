package com.dopashift.domain.repository

import com.dopashift.domain.entity.AuditLogEntry

/**
 * Repository port interface for the tamper-evident audit log.
 */
interface AuditLogRepository {
    suspend fun append(entry: AuditLogEntry)
    suspend fun findByCorrelationId(correlationId: String): List<AuditLogEntry>
    suspend fun getLastEntry(): AuditLogEntry?
}
