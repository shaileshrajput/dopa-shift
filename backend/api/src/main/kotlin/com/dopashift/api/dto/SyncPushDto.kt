package com.dopashift.api.dto

import java.time.Instant
import java.util.UUID

/**
 * Request body for POST /v1/sync/push.
 * Contains a batch of change_log events from the client.
 */
data class SyncPushRequest(
    val events: List<SyncPushEventDto>
)

/**
 * A single change_log event from the client.
 */
data class SyncPushEventDto(
    val id: UUID,
    val entityId: UUID,
    val entityType: String,
    val field: String,
    val value: String?,
    val timestamp: Instant,
    val deviceId: String
)

/**
 * Response body for POST /v1/sync/push.
 * Contains server-assigned timestamps and conflict resolution results.
 */
data class SyncPushResponse(
    val serverTimestamps: Map<UUID, Instant>,
    val resolved: List<ResolvedConflictDto>,
    val conflicts: List<ConflictDto>
)

/**
 * A conflict that was resolved by the server.
 */
data class ResolvedConflictDto(
    val eventId: UUID,
    val winningEventId: UUID,
    val supersededEventId: UUID,
    val reason: String
)

/**
 * Summary of a conflict for the client to be aware of
 * (same as resolved, included for clarity in the response contract).
 */
data class ConflictDto(
    val entityId: UUID,
    val field: String,
    val winningValue: String?,
    val supersededValue: String?,
    val reason: String
)
