package com.dopashift.data.repository

import com.dopashift.data.local.dao.DiagnosticEventDao
import com.dopashift.domain.creation.DiagnosticEventType
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rolls the local-only `quick_create_diagnostics` events into an aggregated, counts-only
 * summary and syncs it under strict privacy/volume bounds (DQC-6.5, DQC-6.6).
 *
 * Guarantees enforced here:
 * - **Counts only, never raw records.** The sync path can only ever transmit a
 *   [DiagnosticsCountsPayload] of per-type counts. Raw [com.dopashift.data.local.entity.LocalDiagnosticEvent]
 *   rows are never referenced by the uploader port — they are read locally to compute
 *   counts and then cleared. This makes leaking a raw record structurally impossible
 *   (mirrors the [AggregatedCounterSync] privacy design).
 * - **≤ 10 KB payload.** The serialized payload size is checked before upload; if it
 *   would exceed [MAX_PAYLOAD_BYTES] the sync is skipped (counts remain queued locally).
 * - **≤ 1× / 24 h.** [QuickCreatePreferencesStore.lastDiagnosticsSyncAt] gates the cadence;
 *   a sync within the last 24 h is skipped.
 * - **Offline-first queue + flush on reconnect.** Raw events are already persisted to Room
 *   by [DataStoreDiagnosticsSink], so they survive offline periods. The actual transmission
 *   is delegated to the injectable [DiagnosticsCountsUploader] port whose concrete network
 *   implementation runs under the standard sync retry policy (exponential backoff, WorkManager
 *   `NetworkType.CONNECTED` constraint — see the `sync` module's `SyncManager`/`SyncWorker`),
 *   so a failed/offline attempt is retried on reconnect. `lastDiagnosticsSyncAt` is only
 *   advanced on a successful upload, so a failed attempt does not consume the 24 h window.
 */
@Singleton
class DiagnosticsAggregator @Inject constructor(
    private val diagnosticEventDao: DiagnosticEventDao,
    private val preferences: QuickCreatePreferencesStore,
    private val uploader: DiagnosticsCountsUploader
) {

    /**
     * Aggregate the queued diagnostic events into per-type counts and, if the 24 h and
     * 10 KB bounds allow, sync the counts-only summary.
     *
     * @param now current wall-clock epoch millis (injectable for testing).
     * @return the outcome of the attempt; see [DiagnosticsSyncResult].
     */
    suspend fun aggregateAndSync(now: Long = System.currentTimeMillis()): DiagnosticsSyncResult {
        // Enforce <= 1x / 24h using the persisted last-sync timestamp (DQC-6.5).
        val lastSyncAt = preferences.observe().first().lastDiagnosticsSyncAt
        if (lastSyncAt != null && now - lastSyncAt < TWENTY_FOUR_HOURS_MS) {
            return DiagnosticsSyncResult.SkippedWithin24h
        }

        val counts = aggregateCounts()
        if (counts.eventTypeCounts.isEmpty()) {
            return DiagnosticsSyncResult.NothingToSync
        }

        // Enforce the <= 10 KB payload bound (DQC-6.5). Counts-only payloads are tiny, but
        // the guard keeps the invariant explicit and future-proof.
        val estimatedBytes = counts.estimatedSizeBytes()
        if (estimatedBytes > MAX_PAYLOAD_BYTES) {
            return DiagnosticsSyncResult.SkippedPayloadTooLarge(estimatedBytes)
        }

        // Delegate transmission to the injectable port. A concrete network uploader runs
        // under the standard sync retry policy; on failure the raw rows stay queued locally
        // and are retried on reconnect (DQC-6.6). We only advance the 24 h window on success.
        return try {
            uploader.upload(counts)
            preferences.setLastDiagnosticsSyncAt(now)
            // Raw rows have served their purpose (counts synced) and are never synced raw,
            // so clear them to keep the local table bounded.
            diagnosticEventDao.clear()
            DiagnosticsSyncResult.Synced(counts)
        } catch (e: Exception) {
            // Leave rows queued for the next attempt; do not advance lastDiagnosticsSyncAt.
            DiagnosticsSyncResult.Failed(e.message ?: "diagnostics upload failed")
        }
    }

    /**
     * Roll the stored raw events into a counts-only summary — one count per
     * [DiagnosticEventType]. No raw record, correlation id, timestamp, or entity value is
     * included in the result.
     */
    suspend fun aggregateCounts(): DiagnosticsCountsPayload {
        val counts = LinkedHashMap<String, Long>()
        for (type in DiagnosticEventType.entries) {
            val count = diagnosticEventDao.countByType(type.name)
            if (count > 0) {
                counts[type.name] = count.toLong()
            }
        }
        return DiagnosticsCountsPayload(eventTypeCounts = counts)
    }

    companion object {
        /** Maximum aggregated diagnostics payload size in bytes (DQC-6.5). */
        const val MAX_PAYLOAD_BYTES = 10 * 1024

        /** 24 hours in milliseconds; the minimum interval between diagnostics syncs (DQC-6.5). */
        const val TWENTY_FOUR_HOURS_MS = 24L * 60L * 60L * 1000L
    }
}

/**
 * Aggregated, counts-only diagnostics summary (DQC-6.5).
 *
 * Contains ONLY per-event-type counts — never raw records, correlation ids, timestamps,
 * or entity values. This is the sole shape that ever leaves the device for diagnostics.
 */
data class DiagnosticsCountsPayload(
    val eventTypeCounts: Map<String, Long>
) {
    /**
     * Estimate the serialized payload size in bytes for the ≤ 10 KB bound. Uses a compact
     * `key=value` UTF-8 encoding so the estimate is a stable upper bound independent of any
     * serializer configuration.
     */
    fun estimatedSizeBytes(): Int =
        eventTypeCounts.entries
            .joinToString(separator = ";") { (k, v) -> "$k=$v" }
            .toByteArray(Charsets.UTF_8)
            .size
}

/**
 * Injectable port for transmitting a counts-only [DiagnosticsCountsPayload] to the backend.
 *
 * Kept as an interface (mirroring [AggregatedCounterUploader]) so:
 * - the `data` module does not depend on the `sync` module (which depends on `data`),
 *   avoiding a dependency cycle; the concrete implementation lives with the sync engine;
 * - the aggregation flow is testable without a real network call; and
 * - raw diagnostic records are structurally unreachable from the transmission path.
 */
interface DiagnosticsCountsUploader {
    /** Transmit the aggregated counts-only summary. Runs under the standard sync retry policy. */
    suspend fun upload(payload: DiagnosticsCountsPayload)
}

/** Outcome of a [DiagnosticsAggregator.aggregateAndSync] attempt. */
sealed interface DiagnosticsSyncResult {
    /** Counts were synced and the 24 h window advanced. */
    data class Synced(val payload: DiagnosticsCountsPayload) : DiagnosticsSyncResult

    /** A sync already occurred within the last 24 h (DQC-6.5). */
    data object SkippedWithin24h : DiagnosticsSyncResult

    /** No queued events to aggregate. */
    data object NothingToSync : DiagnosticsSyncResult

    /** The aggregated payload would exceed the 10 KB bound (DQC-6.5). */
    data class SkippedPayloadTooLarge(val estimatedBytes: Int) : DiagnosticsSyncResult

    /** Transmission failed; rows remain queued for retry on reconnect (DQC-6.6). */
    data class Failed(val reason: String) : DiagnosticsSyncResult
}
