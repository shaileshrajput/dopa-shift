package com.dopashift.sync

import com.dopashift.data.local.dao.ChangeLogDao
import com.dopashift.data.local.dao.SyncStateDao
import com.dopashift.data.local.entity.LocalChangeLogEntry
import com.dopashift.data.local.entity.LocalSyncState
import com.dopashift.data.remote.DopaShiftApi
import com.dopashift.data.remote.SyncEvent
import com.dopashift.data.remote.SyncPushRequest
import com.dopashift.sync.model.SyncResult
import com.dopashift.sync.model.SyncStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the Change_Log sync engine client.
 * Handles push/pull synchronization with the backend
 * using exponential backoff retry (1s, 2s, 4s, 8s, 16s) up to 5 attempts.
 *
 * Requirements: 4.4, 4.5, 4.8, 4.10
 */
@Singleton
class SyncManager @Inject constructor(
    private val changeLogDao: ChangeLogDao,
    private val syncStateDao: SyncStateDao,
    private val api: DopaShiftApi
) {

    companion object {
        const val MAX_RETRY_ATTEMPTS = 5
        const val INITIAL_BACKOFF_MS = 1000L
        const val SYNC_STATE_KEY = "last_sync_timestamp"
    }

    private val _syncStatus = MutableStateFlow(SyncStatus.SYNCED)

    /** Observable sync status for UI binding. */
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    /**
     * Observes whether there are unsynced entries in the local change log.
     * Returns true when entries are pending push.
     */
    fun observeHasPendingChanges(userId: String): Flow<Boolean> {
        return changeLogDao.observeUnsyncedByUserId(userId).map { it.isNotEmpty() }
    }

    /**
     * Appends a new change to the local Room Change_Log table.
     * The entry is persisted immediately (within 200ms per Requirement 4.4).
     * It will be pushed to the backend on the next sync cycle.
     */
    suspend fun appendChange(entry: LocalChangeLogEntry) {
        changeLogDao.insert(entry)
        updateStatusFromQueue(entry.userId)
    }

    /**
     * Pushes queued local change log events to the backend.
     * Retries with exponential backoff (1s, 2s, 4s, 8s, 16s) up to 5 attempts.
     *
     * @param userId The user whose pending changes to push.
     * @return [SyncResult.PushSuccess] on success, [SyncResult.Failure] after exhausting retries,
     *         or [SyncResult.NothingToSync] if no pending changes exist.
     */
    suspend fun pushPendingChanges(userId: String): SyncResult {
        val unsyncedEntries = changeLogDao.findUnsyncedByUserId(userId)

        if (unsyncedEntries.isEmpty()) {
            return SyncResult.NothingToSync
        }

        _syncStatus.value = SyncStatus.SYNCING

        val events = unsyncedEntries.map { it.toSyncEvent() }
        val request = SyncPushRequest(events = events)

        var attempt = 0
        var lastError: Exception? = null

        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                val response = api.pushSync(request)

                // Mark entries as synced after successful push
                val syncedIds = unsyncedEntries.map { it.id }
                changeLogDao.markSynced(syncedIds)

                _syncStatus.value = SyncStatus.SYNCED
                Timber.d("Push succeeded: ${syncedIds.size} events synced, ${response.conflicts.size} conflicts resolved")

                return SyncResult.PushSuccess(
                    syncedCount = syncedIds.size,
                    conflicts = response.conflicts
                )
            } catch (e: Exception) {
                lastError = e
                attempt++
                Timber.w(e, "Push attempt $attempt/$MAX_RETRY_ATTEMPTS failed")

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    val backoffMs = calculateBackoff(attempt)
                    Timber.d("Retrying push in ${backoffMs}ms")
                    delay(backoffMs)
                }
            }
        }

        // All retries exhausted — display sync-pending indicator
        _syncStatus.value = SyncStatus.ERROR
        Timber.e(lastError, "Push failed after $MAX_RETRY_ATTEMPTS attempts")

        return SyncResult.Failure(
            reason = lastError?.message ?: "Unknown error",
            attemptsExhausted = true
        )
    }

    /**
     * Pulls remote change log events since the client's last sync point.
     * Applies them to the local database (field-level merge).
     * Retries with exponential backoff on network failure.
     *
     * @param userId The user whose remote changes to pull.
     * @return [SyncResult.PullSuccess] with the count of applied events,
     *         or [SyncResult.Failure] after exhausting retries.
     */
    suspend fun pullRemoteChanges(userId: String): SyncResult {
        _syncStatus.value = SyncStatus.SYNCING

        val lastSync = syncStateDao.getByKey(SYNC_STATE_KEY)?.lastSyncTimestamp ?: 0L

        var attempt = 0
        var lastError: Exception? = null

        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                val response = api.pullSync(since = lastSync)

                // Apply remote events to local store
                val appliedCount = applyRemoteEvents(response.events, userId)

                // Update last sync timestamp
                syncStateDao.upsert(
                    LocalSyncState(
                        key = SYNC_STATE_KEY,
                        lastSyncTimestamp = response.serverTimestamp
                    )
                )

                _syncStatus.value = SyncStatus.SYNCED
                Timber.d("Pull succeeded: $appliedCount events applied, new sync timestamp: ${response.serverTimestamp}")

                return SyncResult.PullSuccess(
                    appliedCount = appliedCount,
                    newSyncTimestamp = response.serverTimestamp
                )
            } catch (e: Exception) {
                lastError = e
                attempt++
                Timber.w(e, "Pull attempt $attempt/$MAX_RETRY_ATTEMPTS failed")

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    val backoffMs = calculateBackoff(attempt)
                    Timber.d("Retrying pull in ${backoffMs}ms")
                    delay(backoffMs)
                }
            }
        }

        // All retries exhausted
        _syncStatus.value = SyncStatus.ERROR
        Timber.e(lastError, "Pull failed after $MAX_RETRY_ATTEMPTS attempts")

        return SyncResult.Failure(
            reason = lastError?.message ?: "Unknown error",
            attemptsExhausted = true
        )
    }

    /**
     * Performs a full sync cycle: pull first (to get latest server state),
     * then push local changes.
     *
     * Per Requirement 4.8: pull events since last sync before allowing new local writes.
     */
    suspend fun sync(userId: String): Pair<SyncResult, SyncResult> {
        val pullResult = pullRemoteChanges(userId)
        val pushResult = pushPendingChanges(userId)
        return pullResult to pushResult
    }

    /**
     * Calculates exponential backoff delay for the given attempt number.
     * Backoff sequence: 1s, 2s, 4s, 8s, 16s (doubling per attempt).
     *
     * @param attempt 1-based attempt number (1 = first retry).
     * @return Delay in milliseconds.
     */
    internal fun calculateBackoff(attempt: Int): Long {
        // attempt 1 → 1s, attempt 2 → 2s, attempt 3 → 4s, attempt 4 → 8s, attempt 5 → 16s
        return INITIAL_BACKOFF_MS * (1L shl (attempt - 1))
    }

    /**
     * Applies remote sync events to the local change log for field-level merge.
     * Remote events are inserted into the local change_log as already-synced entries,
     * enabling the repository layer to apply field-level merges.
     *
     * @return The number of events applied.
     */
    private suspend fun applyRemoteEvents(events: List<SyncEvent>, userId: String): Int {
        var appliedCount = 0

        for (event in events) {
            // Skip events that originated from this device (already local)
            val existingEntry = changeLogDao.findSince(userId, 0L)
                .find { it.id == event.id }

            if (existingEntry != null) {
                // Already have this event locally, skip
                continue
            }

            // Insert remote event as a synced entry in the local change_log.
            // The repository layer uses change_log entries to reconstruct entity state
            // via field-level merge (each entry represents one field change).
            val localEntry = LocalChangeLogEntry(
                id = event.id,
                entityId = event.entityId,
                entityType = event.entityType,
                field = event.field,
                value = event.value,
                timestamp = event.timestamp,
                deviceId = event.deviceId,
                userId = event.userId,
                isSynced = true // Already synced from server
            )
            changeLogDao.insert(localEntry)
            appliedCount++
        }

        return appliedCount
    }

    /**
     * Updates sync status based on whether there are pending unsynced entries.
     */
    private suspend fun updateStatusFromQueue(userId: String) {
        val unsynced = changeLogDao.findUnsyncedByUserId(userId)
        if (unsynced.isNotEmpty() && _syncStatus.value == SyncStatus.SYNCED) {
            _syncStatus.value = SyncStatus.PENDING
        }
    }

    /**
     * Maps a local change log entry to the API sync event DTO.
     */
    private fun LocalChangeLogEntry.toSyncEvent(): SyncEvent {
        return SyncEvent(
            id = id,
            entityId = entityId,
            entityType = entityType,
            field = field,
            value = value,
            timestamp = timestamp,
            deviceId = deviceId,
            userId = userId
        )
    }
}
