package com.dopashift.sync

import com.dopashift.domain.repository.SyncStatusProvider
import com.dopashift.sync.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the sync engine's [SyncManager.syncStatus] to the framework-free
 * [SyncStatusProvider] port the `ui` layer observes for the Dashboard's persistent,
 * non-modal sync-pending indicator (DUX-3 AC9).
 *
 * "Pending" is any state other than fully [SyncStatus.SYNCED] — i.e. changes queued,
 * a push/pull in flight, or a failed attempt awaiting retry. Observation is purely
 * reactive and triggers no network request, preserving the local-only render path
 * (DUX-3 AC10).
 */
@Singleton
class SyncStatusProviderImpl @Inject constructor(
    private val syncManager: SyncManager
) : SyncStatusProvider {

    override fun observeSyncPending(): Flow<Boolean> =
        syncManager.syncStatus.map { status -> status != SyncStatus.SYNCED }
}
