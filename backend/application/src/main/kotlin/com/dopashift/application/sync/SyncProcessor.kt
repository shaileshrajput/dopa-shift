package com.dopashift.application.sync

import com.dopashift.domain.entity.ChangeLogEntry
import com.dopashift.domain.entity.ConflictHistoryEntry
import com.dopashift.domain.repository.ChangeLogRepository
import com.dopashift.domain.repository.ConflictHistoryRepository
import com.dopashift.domain.strategy.ConflictResolutionStrategy
import com.dopashift.domain.strategy.DeleteWinsStrategy
import com.dopashift.domain.strategy.LastWriteWinsStrategy
import com.dopashift.domain.strategy.MergeResult
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Server-side processor for the Change_Log sync engine.
 *
 * Responsibilities:
 * - Accepts batches of client ChangeLogEntry events (with client timestamps/device IDs)
 * - Assigns server logical timestamps (monotonically increasing)
 * - For each entry, checks if a conflicting entry exists (same entityId + field from another device)
 * - If no conflict: appends to change_log directly
 * - If conflict: applies resolution strategy (DeleteWins if either is a delete, else LastWriteWins)
 * - Stores superseded entries in ConflictHistoryRepository
 * - Returns results indicating which entries were accepted and which were resolved
 *
 * Requirements: 4.5, 4.6, 4.7, 4.11, 11.2
 */
class SyncProcessor(
    private val changeLogRepository: ChangeLogRepository,
    private val conflictHistoryRepository: ConflictHistoryRepository,
    private val clock: Clock = Clock.systemUTC()
) {
    private val lastWriteWinsStrategy: ConflictResolutionStrategy = LastWriteWinsStrategy()
    private val deleteWinsStrategy: ConflictResolutionStrategy = DeleteWinsStrategy()

    /**
     * Processes a batch of change log entries from a client.
     *
     * Each entry is assigned a server logical timestamp, checked for conflicts,
     * and either appended directly or resolved via the appropriate strategy.
     *
     * @param clientEntries the batch of entries from the client device
     * @return [SyncResult] containing accepted entries and conflict resolutions
     */
    suspend fun processBatch(clientEntries: List<ChangeLogEntry>): SyncResult {
        require(clientEntries.isNotEmpty()) { "Client entries batch must not be empty" }

        val accepted = mutableListOf<AcceptedEntry>()
        val resolved = mutableListOf<ResolvedConflict>()

        for (clientEntry in clientEntries) {
            val serverTimestamp = Instant.now(clock)

            // Create the entry with server-assigned timestamp
            val serverEntry = clientEntry.copy(timestamp = serverTimestamp)

            // Check for existing entry with same entityId + field from a different device
            val existingEntry = changeLogRepository.findLatestByEntityIdAndField(
                entityId = serverEntry.entityId,
                field = serverEntry.field
            )

            if (existingEntry == null || existingEntry.deviceId == serverEntry.deviceId) {
                // No conflict: append directly
                changeLogRepository.append(serverEntry)
                accepted.add(AcceptedEntry(entry = serverEntry))
            } else {
                // Conflict detected: same entity + field from different device
                val resolution = resolveConflict(
                    existing = existingEntry,
                    incoming = serverEntry
                )

                when (resolution) {
                    is MergeResult.LocalWins -> {
                        // Existing entry wins; incoming is superseded but still appended for history
                        changeLogRepository.append(serverEntry)
                        saveConflictHistory(
                            winningEntryId = existingEntry.id,
                            supersededEntry = serverEntry,
                            reason = determineResolutionReason(existingEntry, serverEntry)
                        )
                        resolved.add(
                            ResolvedConflict(
                                incomingEntry = serverEntry,
                                winningEntry = existingEntry,
                                supersededEntry = serverEntry,
                                reason = determineResolutionReason(existingEntry, serverEntry)
                            )
                        )
                    }
                    is MergeResult.RemoteWins -> {
                        // Incoming entry wins; existing is superseded
                        changeLogRepository.append(serverEntry)
                        saveConflictHistory(
                            winningEntryId = serverEntry.id,
                            supersededEntry = existingEntry,
                            reason = determineResolutionReason(existingEntry, serverEntry)
                        )
                        resolved.add(
                            ResolvedConflict(
                                incomingEntry = serverEntry,
                                winningEntry = serverEntry,
                                supersededEntry = existingEntry,
                                reason = determineResolutionReason(existingEntry, serverEntry)
                            )
                        )
                    }
                    is MergeResult.FieldMerge -> {
                        // Field-level merge — both entries coexist (shouldn't happen for same-field conflicts)
                        changeLogRepository.append(serverEntry)
                        accepted.add(AcceptedEntry(entry = serverEntry))
                    }
                }
            }
        }

        return SyncResult(accepted = accepted, resolved = resolved)
    }

    /**
     * Selects the appropriate conflict resolution strategy:
     * - DeleteWins if either entry represents a deletion (null value)
     * - LastWriteWins otherwise
     *
     * In the context of server-side processing, "local" = existing entry,
     * "remote" = incoming entry (from the client pushing).
     */
    private fun resolveConflict(existing: ChangeLogEntry, incoming: ChangeLogEntry): MergeResult {
        val strategy = if (existing.value == null || incoming.value == null) {
            deleteWinsStrategy
        } else {
            lastWriteWinsStrategy
        }
        // existing = local (already in the system), incoming = remote (new arrival)
        return strategy.resolve(local = existing, remote = incoming)
    }

    private fun determineResolutionReason(existing: ChangeLogEntry, incoming: ChangeLogEntry): String {
        return if (existing.value == null || incoming.value == null) {
            ConflictHistoryEntry.REASON_DELETE_WINS
        } else {
            ConflictHistoryEntry.REASON_LWW
        }
    }

    private suspend fun saveConflictHistory(
        winningEntryId: UUID,
        supersededEntry: ChangeLogEntry,
        reason: String
    ) {
        val historyEntry = ConflictHistoryEntry(
            id = UUID.randomUUID(),
            userId = supersededEntry.userId,
            changeLogId = winningEntryId,
            supersededValue = supersededEntry.value,
            supersededDeviceId = supersededEntry.deviceId,
            supersededTimestamp = supersededEntry.timestamp,
            resolutionReason = reason,
            resolvedAt = Instant.now(clock),
            createdAt = Instant.now(clock)
        )
        conflictHistoryRepository.save(historyEntry)
    }
}
