package com.dopashift.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.dopashift.data.remote.dto.InterceptionRuleResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Consent-gated aggregated-only sync (Requirements 6.2, 6.3, 6.4, 6.5).
 *
 * Privacy contract — enforced *structurally*:
 * - This component has NO dependency on [com.dopashift.data.local.dao.TelemetryDao] or
 *   [com.dopashift.data.local.entity.LocalTelemetryEvent]. It cannot reference a raw
 *   per-sample telemetry row because the types are not imported and are not reachable
 *   through its collaborators. It only ever accepts and transmits
 *   [AggregatedCounterPayload] values (aggregated counts / diagnostic summaries plus
 *   rule metadata reusing [InterceptionRuleResponse]). This makes it impossible to
 *   accidentally sync raw telemetry (Requirements 6.3, 6.4).
 * - Actual remote transmission is delegated to an injectable [AggregatedCounterUploader]
 *   port, so no network call is hardcoded here and the flow is fully testable.
 *
 * Consent gating (Requirements 6.4, 6.5):
 * - A boolean consent flag is read from DataStore. Sync only runs when consent == true.
 * - When consent is revoked, any in-flight or queued upload is cancelled immediately and
 *   all subsequent [sync] attempts no-op until consent is re-granted.
 */
@Singleton
class AggregatedCounterSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uploader: AggregatedCounterUploader
) {

    private companion object {
        val SYNC_CONSENT_GRANTED = booleanPreferencesKey("sync_consent_granted")
    }

    // Parent scope for all sync work. Uploads run as children of this SupervisorJob so a
    // single failure does not tear down the scope, and revocation can cancel all of them.
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Tracks the currently running/queued upload so it can be cancelled on revocation.
    private val jobMutex = Mutex()
    private var currentJob: Job? = null

    // === Consent flag (DataStore) ===

    /** Observe the current sync-consent state. Defaults to false (opt-in). */
    fun observeConsent(): Flow<Boolean> =
        context.syncPrefsDataStore.data.map { prefs -> prefs[SYNC_CONSENT_GRANTED] ?: false }

    /** Read the current sync-consent state once. Defaults to false (opt-in). */
    suspend fun isConsentGranted(): Boolean =
        context.syncPrefsDataStore.data.map { prefs -> prefs[SYNC_CONSENT_GRANTED] ?: false }.first()

    /** Grant sync consent. Subsequent [sync] attempts are allowed to proceed. */
    suspend fun grantConsent() {
        context.syncPrefsDataStore.edit { prefs -> prefs[SYNC_CONSENT_GRANTED] = true }
    }

    /**
     * Revoke sync consent (Requirement 6.5): immediately cancel any in-flight or queued
     * upload, then persist the revoked flag so subsequent [sync] attempts no-op until
     * consent is re-granted.
     */
    suspend fun revokeConsent() {
        // Persist first so any attempt racing with cancellation also observes the revoked flag.
        context.syncPrefsDataStore.edit { prefs -> prefs[SYNC_CONSENT_GRANTED] = false }
        cancelInFlight()
    }

    // === Sync ===

    /**
     * Attempt to sync an aggregated-only payload.
     *
     * Consent is checked immediately before dispatching the upload; if consent is not
     * granted, this is a no-op and returns [SyncResult.SkippedNoConsent] (Requirements
     * 6.4, 6.5). The upload runs on [syncScope] so that a later [revokeConsent] can cancel
     * it mid-flight.
     */
    suspend fun sync(payload: AggregatedCounterPayload): SyncResult {
        if (!isConsentGranted()) {
            return SyncResult.SkippedNoConsent
        }

        val job = jobMutex.withLock {
            // Supersede any previous queued/in-flight job with the newest request.
            currentJob?.cancel()
            val newJob = syncScope.launch {
                // Re-check inside the coroutine to close the window between the guard above
                // and dispatch, in case consent was revoked in between.
                if (!isConsentGranted()) return@launch
                uploader.upload(payload)
            }
            currentJob = newJob
            newJob
        }

        return try {
            job.join()
            if (job.isCancelled) SyncResult.Cancelled else SyncResult.Synced
        } finally {
            jobMutex.withLock {
                if (currentJob === job) currentJob = null
            }
        }
    }

    /**
     * Cancel any in-flight or queued upload immediately and wait for it to unwind.
     * Idempotent — safe to call when nothing is running.
     */
    suspend fun cancelInFlight() {
        val job = jobMutex.withLock { currentJob.also { currentJob = null } }
        job?.cancelAndJoin()
    }
}

/**
 * Aggregated-only sync payload (Requirements 6.3, 6.4).
 *
 * Contains ONLY aggregated counts / diagnostic summaries derived from telemetry — never a
 * raw [com.dopashift.data.local.entity.LocalTelemetryEvent] row. Rule metadata reuses the
 * existing [InterceptionRuleResponse] DTO.
 */
data class AggregatedCounterPayload(
    /** Per-app aggregated foreground totals for a day (no per-sample rows). */
    val appCounters: List<AppCounter>,
    /** Rule metadata, reusing the existing interception-rule DTO. */
    val rules: List<InterceptionRuleResponse>,
    /** Optional free-form diagnostic summary counts (e.g. interceptions shown, extensions granted). */
    val diagnostics: Map<String, Long> = emptyMap()
) {
    /**
     * Aggregated per-app counters for a single day. Represents summed totals only — it
     * carries no timestamps, no per-sample identifiers, and no raw event data.
     */
    data class AppCounter(
        val appPackageName: String,
        /** ISO-8601 local date (yyyy-MM-dd) the counts belong to. */
        val date: String,
        /** Total foreground minutes accumulated for the app on [date]. */
        val totalForegroundMinutes: Long,
        /** Number of times the app breached its limit on [date]. */
        val breachCount: Int = 0
    )
}

/**
 * Injectable port for transmitting an [AggregatedCounterPayload] to a remote backend.
 *
 * Kept as an interface so the sync flow is testable without a real network call and so the
 * concrete network implementation can be provided independently. Implementations MUST only
 * ever receive aggregated payloads — the type system prevents raw telemetry from reaching here.
 */
interface AggregatedCounterUploader {
    suspend fun upload(payload: AggregatedCounterPayload)
}

/** Outcome of a [AggregatedCounterSync.sync] attempt. */
sealed interface SyncResult {
    /** Upload completed successfully. */
    data object Synced : SyncResult
    /** Consent was not granted; nothing was transmitted. */
    data object SkippedNoConsent : SyncResult
    /** The upload was cancelled (e.g. consent revoked mid-flight or superseded). */
    data object Cancelled : SyncResult
}

private val Context.syncPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sync_preferences"
)
