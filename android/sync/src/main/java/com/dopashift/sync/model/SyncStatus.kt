package com.dopashift.sync.model

/**
 * Represents the current state of the sync engine.
 * Exposed as a Kotlin Flow for UI observation.
 */
enum class SyncStatus {
    /** All local changes have been successfully synced. */
    SYNCED,

    /** Local changes are queued and waiting to be pushed. */
    PENDING,

    /** Sync is currently in progress (push or pull). */
    SYNCING,

    /** Sync failed after all retry attempts exhausted. */
    ERROR
}
