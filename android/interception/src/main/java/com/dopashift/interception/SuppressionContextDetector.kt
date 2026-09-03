package com.dopashift.interception

import android.content.Context
import android.media.AudioManager
import android.telephony.TelephonyManager
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects whether a safety-critical **Suppression_Context** is currently active, during
 * which the Intercept_Screen must never be presented (Requirement 5.2).
 *
 * A Suppression_Context is any of:
 * - an **active call** (detected via [TelephonyManager] call state and/or
 *   [AudioManager.MODE_IN_CALL] / [AudioManager.MODE_IN_COMMUNICATION]),
 * - **active navigation** (a turn-by-turn navigation session signalled by the system),
 * - a **ringing alarm or timer** (audio mode / ringer signal),
 * - the **emergency dialer**.
 *
 * The detector exposes [isSuppressed] returning a [SuppressionResult] that names the active
 * context (useful for logging/telemetry) or [SuppressionResult.None] when nothing is suppressing.
 *
 * ### Testability
 * Signal acquisition is delegated to a [SuppressionSignalSource]. Production code uses
 * [AndroidSuppressionSignalSource] (backed by the real system services); tests supply a fake
 * source so the detection logic can be exercised deterministically without Android runtime deps
 * — mirroring the existing [InterceptionContext] abstraction pattern used elsewhere in this module.
 *
 * ### Scope limitation (OQ-3)
 * Per open question **OQ-3**, third-party video-call apps (e.g. a video meeting) are explicitly
 * **out of scope for v1**: [TelephonyManager] call state does not cover them, and no reliable
 * cross-app "in a video call" signal is available. Only [TelephonyManager], system
 * navigation, and alarm/ringer signals are consulted here. Extending suppression to video-call
 * apps is deferred to a future spec once a dependable signal is identified.
 */
@Singleton
class SuppressionContextDetector @Inject constructor(
    private val signalSource: SuppressionSignalSource
) {

    /**
     * The kind of Suppression_Context that is active, or [None] when the Intercept_Screen
     * may be presented (subject to other engine gates).
     */
    sealed interface SuppressionResult {
        /** No Suppression_Context is active — presentation is not blocked by this detector. */
        data object None : SuppressionResult

        /** An active or ringing phone call is in progress. */
        data object ActiveCall : SuppressionResult

        /** A turn-by-turn navigation session is active. */
        data object ActiveNavigation : SuppressionResult

        /** An alarm or timer is currently ringing. */
        data object RingingAlarm : SuppressionResult

        /** The emergency dialer is in the foreground. */
        data object EmergencyDialer : SuppressionResult

        /** True when this result represents an active Suppression_Context. */
        val isSuppressing: Boolean
            get() = this != None
    }

    /**
     * Determines whether a Suppression_Context is active at [now].
     *
     * The checks are evaluated in priority order (call → emergency dialer → alarm → navigation);
     * the first active context is returned so callers get a stable, single reason for logging.
     *
     * @param now the current instant (injected by the caller so callers using an injectable
     *   [java.time.Clock] stay deterministic; the underlying system signals are point-in-time).
     * @return the active [SuppressionResult], or [SuppressionResult.None] when nothing suppresses.
     */
    fun isSuppressed(now: Instant): SuppressionResult {
        val result = when {
            signalSource.isCallActive() -> SuppressionResult.ActiveCall
            signalSource.isEmergencyDialerActive() -> SuppressionResult.EmergencyDialer
            signalSource.isAlarmRinging() -> SuppressionResult.RingingAlarm
            signalSource.isNavigationActive() -> SuppressionResult.ActiveNavigation
            else -> SuppressionResult.None
        }
        if (result.isSuppressing) {
            Timber.d("Suppression_Context active at %s: %s", now, result::class.simpleName)
        }
        return result
    }
}

/**
 * Source of the raw device signals used by [SuppressionContextDetector].
 *
 * Extracted as an interface so the detection logic is unit-testable without Android system
 * services. Each method reports whether the corresponding Suppression_Context is currently active.
 */
interface SuppressionSignalSource {
    /** True when a phone call is active/ringing (per telephony call state or audio mode). */
    fun isCallActive(): Boolean

    /** True when the emergency dialer is the active context. */
    fun isEmergencyDialerActive(): Boolean

    /** True when an alarm or timer is currently ringing. */
    fun isAlarmRinging(): Boolean

    /** True when a turn-by-turn navigation session is active. */
    fun isNavigationActive(): Boolean
}

/**
 * Production [SuppressionSignalSource] backed by real Android system services.
 *
 * Call detection combines [TelephonyManager] call state with [AudioManager]'s audio mode, since
 * either can indicate an in-progress call depending on OEM/API level. Alarm detection uses the
 * ringer/audio mode signal. Navigation and emergency-dialer signals are supplied by the engine
 * via [setNavigationActive] / [setEmergencyDialerActive] (fed from the foreground-app sampling
 * loop and ongoing-navigation notification checks), keeping this class free of extra permissions.
 */
class AndroidSuppressionSignalSource(
    context: Context
) : SuppressionSignalSource {

    private val appContext: Context = context.applicationContext
    private val telephonyManager: TelephonyManager? =
        appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val audioManager: AudioManager? =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    @Volatile
    private var navigationActive: Boolean = false

    @Volatile
    private var emergencyDialerActive: Boolean = false

    /**
     * Updates the active-navigation signal. Called by the engine when it observes an active
     * navigation session (nav app foreground / ongoing-navigation notification).
     */
    fun setNavigationActive(active: Boolean) {
        navigationActive = active
    }

    /**
     * Updates the emergency-dialer signal. Called by the engine when it observes the emergency
     * dialer in the foreground.
     */
    fun setEmergencyDialerActive(active: Boolean) {
        emergencyDialerActive = active
    }

    override fun isCallActive(): Boolean {
        val audioInCall = audioManager?.mode?.let { mode ->
            mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
        } ?: false
        if (audioInCall) return true

        // Reading detailed call state requires READ_PHONE_STATE; guard against SecurityException
        // and fall back to the audio-mode signal above when the permission is unavailable.
        return try {
            @Suppress("DEPRECATION")
            telephonyManager?.callState?.let { it != TelephonyManager.CALL_STATE_IDLE } ?: false
        } catch (e: SecurityException) {
            Timber.d(e, "Call state unavailable (missing READ_PHONE_STATE); relying on audio mode")
            false
        }
    }

    override fun isEmergencyDialerActive(): Boolean = emergencyDialerActive

    override fun isAlarmRinging(): Boolean {
        return audioManager?.mode == AudioManager.MODE_RINGTONE
    }

    override fun isNavigationActive(): Boolean = navigationActive
}
