package com.dopashift.domain.entity

import java.time.Instant
import java.util.UUID

/**
 * An append-only event store entry used for bidirectional sync
 * and conflict resolution.
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
)
