package com.dopashift.application.audit

import com.dopashift.domain.entity.AuditLogEntry
import com.dopashift.domain.repository.AuditLogRepository
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Application service for the append-only, hash-chained audit log.
 *
 * Responsibilities:
 * - Accepts audit events for sensitive operations
 * - Maintains a hash chain for tamper evidence (each entry includes hash of previous)
 * - Records before/after state for sensitive operations
 * - Propagates correlation ID from request origin
 *
 * This service is deliberately append-only: no update or delete methods are exposed.
 *
 * Requirements: 18.3, 18.4, 18.13
 */
class AuditLogService(
    private val auditLogRepository: AuditLogRepository,
    private val clock: Clock = Clock.systemUTC()
) {

    companion object {
        /** Sentinel hash used as the previousHash for the very first entry in the chain. */
        const val GENESIS_HASH = "GENESIS"
    }

    /**
     * Records a new audit log entry with hash-chain integrity.
     *
     * 1. Fetches the last entry's hash from the repository
     * 2. Computes the new entry's hash: SHA-256(previousHash + operationType + entityId + timestamp + beforeState + afterState)
     * 3. Sets previousHash to the last entry's entryHash (forming a chain)
     * 4. For the first entry ever, previousHash = "GENESIS"
     * 5. Appends the entry via AuditLogRepository
     *
     * @param correlationId unique identifier propagated from the originating user action
     * @param userId authenticated user who performed the operation
     * @param operationType type of sensitive operation (e.g., "GOAL_DELETION", "API_KEY_ROTATION")
     * @param entityId the affected entity's identifier, or null if not applicable
     * @param beforeState serialized state before the operation, or null
     * @param afterState serialized state after the operation, or null
     * @return the created AuditLogEntry
     */
    suspend fun record(
        correlationId: String,
        userId: UUID,
        operationType: String,
        entityId: UUID? = null,
        beforeState: String? = null,
        afterState: String? = null
    ): AuditLogEntry {
        require(correlationId.isNotBlank()) { "Correlation ID must not be blank" }
        require(operationType.isNotBlank()) { "Operation type must not be blank" }

        val timestamp = Instant.now(clock)

        // Fetch previous entry to maintain the hash chain
        val lastEntry = auditLogRepository.getLastEntry()
        val previousHash = lastEntry?.entryHash ?: GENESIS_HASH

        // Compute entry hash: SHA-256(previousHash + operationType + entityId + timestamp + beforeState + afterState)
        val entryHash = computeHash(
            previousHash = previousHash,
            operationType = operationType,
            entityId = entityId,
            timestamp = timestamp,
            beforeState = beforeState,
            afterState = afterState
        )

        val entry = AuditLogEntry(
            id = UUID.randomUUID(),
            correlationId = correlationId,
            userId = userId,
            operationType = operationType,
            entityId = entityId,
            beforeState = beforeState,
            afterState = afterState,
            timestamp = timestamp,
            previousHash = previousHash,
            entryHash = entryHash
        )

        auditLogRepository.append(entry)

        return entry
    }

    /**
     * Finds all audit log entries associated with the given correlation ID.
     *
     * @param correlationId the correlation ID to search by
     * @return list of matching entries, in chronological order
     */
    suspend fun findByCorrelationId(correlationId: String): List<AuditLogEntry> {
        require(correlationId.isNotBlank()) { "Correlation ID must not be blank" }
        return auditLogRepository.findByCorrelationId(correlationId)
    }

    /**
     * Computes SHA-256 hash for a new audit log entry.
     *
     * Hash input: previousHash + operationType + entityId + timestamp + beforeState + afterState
     * All null values are represented as empty string in the hash computation.
     */
    internal fun computeHash(
        previousHash: String,
        operationType: String,
        entityId: UUID?,
        timestamp: Instant,
        beforeState: String?,
        afterState: String?
    ): String {
        val input = buildString {
            append(previousHash)
            append(operationType)
            append(entityId?.toString().orEmpty())
            append(timestamp.toString())
            append(beforeState.orEmpty())
            append(afterState.orEmpty())
        }

        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
