package com.dopashift.domain.entity

import java.time.Instant
import java.util.UUID

/**
 * An append-only event store entry used for bidirectional sync
 * and field-level conflict resolution.
 *
 * Validation constraints:
 * - entityType: non-blank
 * - field: non-blank
 * - deviceId: non-blank
 */
data class ChangeLogEntry(
    val id: UUID,
    val entityId: UUID,
    val entityType: String,
    val field: String,
    val value: String?,
    val timestamp: Instant,
    val deviceId: String,
    val userId: UUID
) {
    init {
        require(entityType.isNotBlank()) {
            "Entity type must not be blank"
        }
        require(field.isNotBlank()) {
            "Field must not be blank"
        }
        require(deviceId.isNotBlank()) {
            "Device ID must not be blank"
        }
    }
}
