package com.dopashift.domain.entity

import java.time.Instant
import java.util.UUID

/**
 * An immutable, append-only record of sensitive operations with
 * hash-chaining for tamper evidence.
 *
 * Validation constraints:
 * - correlationId: non-blank
 * - operationType: non-blank
 * - entryHash: non-blank
 * - previousHash: non-blank
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
) {
    init {
        require(correlationId.isNotBlank()) {
            "Correlation ID must not be blank"
        }
        require(operationType.isNotBlank()) {
            "Operation type must not be blank"
        }
        require(entryHash.isNotBlank()) {
            "Entry hash must not be blank"
        }
        require(previousHash.isNotBlank()) {
            "Previous hash must not be blank"
        }
    }
}
