package com.dopashift.domain.entity

import java.time.Instant
import java.util.UUID

/**
 * An immutable, append-only record of sensitive operations.
 * Hash-chained for tamper evidence.
 */
data class AuditLogEntry(
    val id: UUID,
    val correlationId: String,
    val userId: UUID,
    val operationType: String,
    val entityId: UUID?,
    val beforeState: String?,
    val afterState: String?,
    val timestamp: Instant,
    val previousHash: String,
    val entryHash: String
)
