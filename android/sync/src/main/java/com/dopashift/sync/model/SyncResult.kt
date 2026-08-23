package com.dopashift.sync.model

import com.dopashift.data.remote.ResolvedConflict

/**
 * Result of a sync operation (push or pull).
 */
sealed class SyncResult {

    /** Push completed successfully with server-assigned timestamps applied. */
    data class PushSuccess(
        val syncedCount: Int,
        val conflicts: List<ResolvedConflict> = emptyList()
    ) : SyncResult()

    /** Pull completed successfully with remote events applied locally. */
    data class PullSuccess(
        val appliedCount: Int,
        val newSyncTimestamp: Long
    ) : SyncResult()

    /** Sync operation failed after all retry attempts. */
    data class Failure(
        val reason: String,
        val attemptsExhausted: Boolean = false
    ) : SyncResult()

    /** No pending changes to sync. */
    data object NothingToSync : SyncResult()
}
