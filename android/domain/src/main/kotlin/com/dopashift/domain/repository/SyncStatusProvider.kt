package com.dopashift.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Read-only port exposing whether local changes are still waiting to be synced.
 *
 * Defined in `domain` (framework-free) so the `ui` layer can surface a persistent,
 * non-modal sync-pending indicator on the Dashboard (DUX-3 AC9) without depending on
 * the `sync`/`data` modules directly. The concrete implementation bridges the sync
 * engine's status and is provided via DI in an outer module.
 */
interface SyncStatusProvider {
    /**
     * Emits `true` while there are un-synced local changes (or an in-flight/failed sync
     * that has not yet reached a fully-synced state), `false` once everything is synced.
     *
     * Purely observational — subscribing never triggers a network request, preserving the
     * Dashboard's local-only synchronous render path (DUX-3 AC10).
     */
    fun observeSyncPending(): Flow<Boolean>
}
