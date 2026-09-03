package com.dopashift.data.repository

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [AggregatedCounterUploader] used until a concrete remote endpoint for aggregated
 * counters is wired. It performs no transmission, keeping the privacy contract trivially
 * satisfied (nothing leaves the device) while allowing [AggregatedCounterSync] to be injected
 * and exercised end-to-end.
 *
 * A real network-backed uploader can replace this binding without touching the sync logic,
 * because [AggregatedCounterSync] depends only on the [AggregatedCounterUploader] port.
 */
@Singleton
class NoOpAggregatedCounterUploader @Inject constructor() : AggregatedCounterUploader {
    override suspend fun upload(payload: AggregatedCounterPayload) {
        // No-op: no remote endpoint is wired yet. Raw telemetry never reaches this port by design.
    }
}
