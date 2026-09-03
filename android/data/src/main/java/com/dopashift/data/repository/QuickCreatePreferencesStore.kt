package com.dopashift.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dopashift.domain.creation.OnboardingStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.quickCreatePrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "quick_create_preferences"
)

/**
 * DataStore-backed local preferences that support the Dashboard quick-create surface.
 *
 * Persists the onboarding outcome (DQC-5.8), whether the "resume onboarding" Settings
 * entry has been dismissed (DQC-5.9), and the last time aggregated diagnostics were
 * synced so the ">= 1x / 24h" bound can be enforced (DQC-6.5).
 *
 * Onboarding is never re-presented after completion or skip: once [onboardingStatus]
 * is [OnboardingStatus.COMPLETED] or [OnboardingStatus.SKIPPED], [setOnboardingStatus]
 * will not reset it back to [OnboardingStatus.NOT_STARTED] (DQC-5.7, DQC-5.8).
 */
@Singleton
class QuickCreatePreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private companion object {
        val ONBOARDING_STATUS = stringPreferencesKey("onboarding_status")
        val RESUME_ONBOARDING_DISMISSED = booleanPreferencesKey("resume_onboarding_dismissed")
        val LAST_DIAGNOSTICS_SYNC_AT = longPreferencesKey("last_diagnostics_sync_at")
    }

    /**
     * Emits the current [QuickCreatePrefs] and re-emits on every persisted change.
     */
    fun observe(): Flow<QuickCreatePrefs> {
        return context.quickCreatePrefsDataStore.data.map { prefs ->
            QuickCreatePrefs(
                onboardingStatus = prefs[ONBOARDING_STATUS].toOnboardingStatus(),
                resumeOnboardingDismissed = prefs[RESUME_ONBOARDING_DISMISSED] ?: false,
                lastDiagnosticsSyncAt = prefs[LAST_DIAGNOSTICS_SYNC_AT]
            )
        }
    }

    /**
     * Sets the onboarding outcome (DQC-5.8).
     *
     * Terminal outcomes are sticky: once the persisted status is
     * [OnboardingStatus.COMPLETED] or [OnboardingStatus.SKIPPED], this call will not
     * downgrade it to [OnboardingStatus.NOT_STARTED], guaranteeing onboarding is never
     * re-presented after either outcome (DQC-5.7, DQC-5.8).
     */
    suspend fun setOnboardingStatus(status: OnboardingStatus) {
        context.quickCreatePrefsDataStore.edit { prefs ->
            val current = prefs[ONBOARDING_STATUS].toOnboardingStatus()
            val isTerminal =
                current == OnboardingStatus.COMPLETED || current == OnboardingStatus.SKIPPED
            if (isTerminal && status == OnboardingStatus.NOT_STARTED) {
                // Never re-present onboarding after completion/skip: ignore the downgrade.
                return@edit
            }
            prefs[ONBOARDING_STATUS] = status.name
        }
    }

    /**
     * Records whether the dismissible Settings "resume onboarding" entry has been
     * dismissed; the dismissal is remembered across launches (DQC-5.9).
     */
    suspend fun setResumeOnboardingDismissed(dismissed: Boolean) {
        context.quickCreatePrefsDataStore.edit { prefs ->
            prefs[RESUME_ONBOARDING_DISMISSED] = dismissed
        }
    }

    /**
     * Records the epoch-millis timestamp of the last aggregated diagnostics sync so the
     * aggregator can enforce the once-per-24h cap (DQC-6.5). Passing null clears it.
     */
    suspend fun setLastDiagnosticsSyncAt(epochMillis: Long?) {
        context.quickCreatePrefsDataStore.edit { prefs ->
            if (epochMillis != null) {
                prefs[LAST_DIAGNOSTICS_SYNC_AT] = epochMillis
            } else {
                prefs.remove(LAST_DIAGNOSTICS_SYNC_AT)
            }
        }
    }

    private fun String?.toOnboardingStatus(): OnboardingStatus {
        if (this == null) return OnboardingStatus.NOT_STARTED
        return try {
            OnboardingStatus.valueOf(this)
        } catch (_: IllegalArgumentException) {
            OnboardingStatus.NOT_STARTED
        }
    }
}

/**
 * DataStore-backed onboarding + diagnostics-sync state for the Dashboard quick-create
 * surface. Distinct from any Room table; persisted locally via DataStore Preferences.
 *
 * @property onboardingStatus the persisted onboarding outcome (DQC-5.8).
 * @property resumeOnboardingDismissed whether the Settings resume-onboarding entry was
 *   dismissed (DQC-5.9).
 * @property lastDiagnosticsSyncAt epoch millis of the last diagnostics sync, or null if
 *   diagnostics have never synced; used to enforce <= 1x / 24h (DQC-6.5).
 */
data class QuickCreatePrefs(
    val onboardingStatus: OnboardingStatus,
    val resumeOnboardingDismissed: Boolean,
    val lastDiagnosticsSyncAt: Long?
)
