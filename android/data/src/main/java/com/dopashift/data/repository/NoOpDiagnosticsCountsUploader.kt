package com.dopashift.data.repository

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [DiagnosticsCountsUploader] used until a concrete remote endpoint for aggregated
 * diagnostics counts is wired into the sync engine. It performs no transmission, keeping the
 * privacy contract trivially satisfied (nothing leaves the device) while allowing
 * [DiagnosticsAggregator] to be injected and exercised end to end.
 *
 * A real network-backed uploader (running under the standard sync retry policy) can replace
 * this binding without touching the aggregation logic, because [DiagnosticsAggregator]
 * depends only on the [DiagnosticsCountsUploader] port. Raw diagnostic records never reach
 * this port by design.
 */
@Singleton
class NoOpDiagnosticsCountsUploader @Inject constructor() : DiagnosticsCountsUploader {
    override suspend fun upload(payload: DiagnosticsCountsPayload) {
        // No-op: no remote endpoint is wired yet. Counts-only by construction; no raw records.
    }
}
