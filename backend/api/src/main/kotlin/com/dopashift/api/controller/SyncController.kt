package com.dopashift.api.controller

import com.dopashift.api.dto.ConflictDto
import com.dopashift.api.dto.ResolvedConflictDto
import com.dopashift.api.dto.SyncPushRequest
import com.dopashift.api.dto.SyncPushResponse
import com.dopashift.application.sync.SyncProcessor
import com.dopashift.domain.entity.ChangeLogEntry
import com.dopashift.domain.repository.ChangeLogRepository
import com.dopashift.domain.repository.ConflictHistoryRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * REST controller for sync operations.
 * All endpoints require OAuth2 JWT authentication and scope results to the authenticated user.
 *
 * Requirements: 4.5, 4.6, 4.7, 4.8
 */
@RestController
@RequestMapping("/v1/sync")
class SyncController(
    private val changeLogRepository: ChangeLogRepository,
    private val conflictHistoryRepository: ConflictHistoryRepository,
    private val syncProcessor: SyncProcessor,
    private val clock: Clock
) {

    companion object {
        private val PULL_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(30)
    }

    /**
     * POST /v1/sync/push
     *
     * Accepts a batch of change_log entries from a client device,
     * assigns server timestamps, processes conflicts, and returns
     * resolution results.
     *
     * Requirements: 4.5, 4.6, 4.7
     */
    @PostMapping("/push")
    suspend fun push(
        @RequestBody request: SyncPushRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<SyncPushResponse> {
        val userId = UUID.fromString(jwt.subject)

        if (request.events.isEmpty()) {
            return ResponseEntity.badRequest().build()
        }

        // Map DTOs to domain entities, injecting authenticated user_id
        val clientEntries = request.events.map { event ->
            ChangeLogEntry(
                id = event.id,
                entityId = event.entityId,
                entityType = event.entityType,
                field = event.field,
                value = event.value,
                timestamp = event.timestamp,
                deviceId = event.deviceId,
                userId = userId
            )
        }

        // Delegate to SyncProcessor for conflict detection and resolution
        val result = syncProcessor.processBatch(clientEntries)

        // Build server timestamps map: event ID -> server-assigned timestamp
        val serverTimestamps = mutableMapOf<UUID, Instant>()
        for (accepted in result.accepted) {
            serverTimestamps[accepted.entry.id] = accepted.entry.timestamp
        }
        for (resolved in result.resolved) {
            serverTimestamps[resolved.incomingEntry.id] = resolved.incomingEntry.timestamp
        }

        // Build resolved conflicts list
        val resolvedDtos = result.resolved.map { conflict ->
            ResolvedConflictDto(
                eventId = conflict.incomingEntry.id,
                winningEventId = conflict.winningEntry.id,
                supersededEventId = conflict.supersededEntry.id,
                reason = conflict.reason
            )
        }

        // Build conflicts detail list
        val conflictDtos = result.resolved.map { conflict ->
            ConflictDto(
                entityId = conflict.incomingEntry.entityId,
                field = conflict.incomingEntry.field,
                winningValue = conflict.winningEntry.value,
                supersededValue = conflict.supersededEntry.value,
                reason = conflict.reason
            )
        }

        return ResponseEntity.ok(
            SyncPushResponse(
                serverTimestamps = serverTimestamps,
                resolved = resolvedDtos,
                conflicts = conflictDtos
            )
        )
    }

    /**
     * Returns all change_log events for the authenticated user since the given timestamp,
     * ordered by timestamp ascending. 30-second timeout with graceful handling.
     *
     * Requirements: 4.8
     */
    @GetMapping("/pull")
    suspend fun pull(
        @RequestParam("since") since: String,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<SyncPullResponse> {
        val userId = UUID.fromString(jwt.subject)
        val sinceInstant = Instant.parse(since)

        val events = try {
            withTimeout(PULL_TIMEOUT_MS) {
                changeLogRepository.findSince(userId, sinceInstant)
            }
        } catch (_: TimeoutCancellationException) {
            emptyList()
        }

        val serverTimestamp = Instant.now(clock)

        val eventDtos = events.map { entry ->
            SyncEventDto(
                id = entry.id.toString(),
                entityId = entry.entityId.toString(),
                entityType = entry.entityType,
                field = entry.field,
                value = entry.value,
                timestamp = entry.timestamp.toString(),
                deviceId = entry.deviceId,
                userId = entry.userId.toString()
            )
        }

        return ResponseEntity.ok(
            SyncPullResponse(
                events = eventDtos,
                serverTimestamp = serverTimestamp.toString()
            )
        )
    }

    /**
     * Returns superseded entries from the conflict history for the authenticated user,
     * filtered by a "since" timestamp. Entries retained 90+ days.
     *
     * Requirements: 4.7
     */
    @GetMapping("/conflicts")
    suspend fun getConflicts(
        @RequestParam("since") since: String,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<SyncConflictsResponse> {
        val userId = UUID.fromString(jwt.subject)
        val sinceInstant = Instant.parse(since)

        val entries = conflictHistoryRepository.findSince(userId, sinceInstant)

        val conflictDtos = entries.map { entry ->
            ConflictHistoryDto(
                id = entry.id.toString(),
                changeLogId = entry.changeLogId.toString(),
                supersededValue = entry.supersededValue,
                supersededDeviceId = entry.supersededDeviceId,
                supersededTimestamp = entry.supersededTimestamp.toString(),
                resolutionReason = entry.resolutionReason,
                createdAt = entry.createdAt.toString()
            )
        }

        return ResponseEntity.ok(SyncConflictsResponse(conflicts = conflictDtos))
    }
}

data class SyncPullResponse(
    val events: List<SyncEventDto>,
    val serverTimestamp: String
)

data class SyncEventDto(
    val id: String,
    val entityId: String,
    val entityType: String,
    val field: String,
    val value: String?,
    val timestamp: String,
    val deviceId: String,
    val userId: String
)

data class SyncConflictsResponse(
    val conflicts: List<ConflictHistoryDto>
)

data class ConflictHistoryDto(
    val id: String,
    val changeLogId: String,
    val supersededValue: String?,
    val supersededDeviceId: String,
    val supersededTimestamp: String,
    val resolutionReason: String,
    val createdAt: String
)
