package com.dopashift.interception

import com.dopashift.domain.entity.LimitType
import com.dopashift.domain.entity.MonitoringState
import com.dopashift.domain.repository.InterceptionRuleRepository
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks per-cycle foreground usage for `Repetitive` [com.dopashift.domain.entity.InterceptionRule]s
 * and drives the interval-breach / pause / reset lifecycle (Requirements 2.2, 2.3, 2.5, 2.6, 2.7).
 *
 * ## Contract (how the interval is learned)
 * [onForeground] is the single sample entry point. It resolves the package's active Rule through
 * the domain [InterceptionRuleRepository] port (scoped by [userId], exactly like
 * [AllowanceTracker]) and reads `repetitiveIntervalMinutes` from it. Callers therefore do NOT
 * pass the interval — the tracker owns interval discovery so the routing in
 * [InterceptionService] (task 6.1) needs only the foreground package and the elapsed sample
 * duration, and the counter reset/pause transitions stay authoritative in one place.
 *
 * - A package with no active Rule, or an active `Once` Rule, yields [SessionResult.NotRepetitive].
 * - A `Repetitive` Rule accumulates `Session_Usage` **only while** its Monitoring_State is
 *   [MonitoringState.ACTIVE] (Requirement 2.2, 2.6).
 * - When `Session_Usage` reaches or exceeds `Repetitive_Interval` seconds, [onForeground] returns
 *   [SessionResult.IntervalBreached] (Requirement 2.3) so the service can route through the
 *   existing Suppression_Context deferral and present the overlay.
 * - While [MonitoringState.PAUSED_FOR_OVERLAY], samples are ignored and return
 *   [SessionResult.Paused] — no time is added to `Session_Usage` (Requirements 2.5, 2.6).
 * - [onOverlayDismissedAndReturned] resets `Session_Usage` to 0 and returns the state to
 *   [MonitoringState.ACTIVE] (Requirement 2.7).
 * - [reset] discards the cycle for a package (state back to [MonitoringState.ACTIVE],
 *   `Session_Usage` = 0); the service calls it on foreground loss during a deferred
 *   interception (OQ-2), consistent with the resume-and-reset rule.
 *
 * State is ephemeral and per-cycle — a partial cycle count is meaningless across a process
 * restart, so nothing here is persisted. All access to the in-memory session map is guarded by
 * [lock] because samples arrive from the interception service loop while overlay actions and
 * foreground-loss resets may run on other threads.
 */
@Singleton
class RepetitiveSessionTracker @Inject constructor(
    private val ruleRepository: InterceptionRuleRepository
) {

    /**
     * The authenticated user whose Rules are consulted. Set by [InterceptionService] from the
     * started intent; every Rule lookup is scoped to this id (Requirement 3.8), mirroring
     * [AllowanceTracker.userId].
     */
    @Volatile
    var userId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

    private val lock = Any()

    /** In-memory per-package cycle state. Guarded by [lock]. */
    private val sessions: MutableMap<String, RepetitiveSessionState> = mutableMapOf()

    /**
     * Records a foreground sample for [pkg]. Resolves the package's active Rule to learn the
     * repetitive interval, then applies the accumulate/pause/breach rules above.
     *
     * @param pkg the package name observed in the foreground this sample.
     * @param elapsedSeconds seconds elapsed since the previous sample (the poll interval); a
     *   non-positive value adds nothing but still evaluates the current state.
     * @return the [SessionResult] describing what the service should do next.
     */
    suspend fun onForeground(pkg: String, elapsedSeconds: Long): SessionResult {
        val rule = ruleRepository.findActiveByPackage(userId, pkg)
        if (rule == null || rule.limitType != LimitType.Repetitive) {
            // Not governed by a repetitive rule (deleted, disabled, or Once): drop any stale
            // cycle state so a later re-enable starts a fresh cycle.
            synchronized(lock) { sessions.remove(pkg) }
            return SessionResult.NotRepetitive
        }

        val intervalMinutes = rule.repetitiveIntervalMinutes
            ?: return SessionResult.NotRepetitive // invariant: non-null for Repetitive
        val intervalSeconds = intervalMinutes * 60

        return synchronized(lock) {
            val current = sessions[pkg] ?: RepetitiveSessionState(
                packageName = pkg,
                intervalSeconds = intervalSeconds,
                sessionUsageSeconds = 0L,
                monitoringState = MonitoringState.ACTIVE
            )

            // Keep the cached interval in sync if the Rule was edited mid-cycle.
            val synced = if (current.intervalSeconds != intervalSeconds) {
                current.copy(intervalSeconds = intervalSeconds)
            } else {
                current
            }

            when (synced.monitoringState) {
                MonitoringState.PAUSED_FOR_OVERLAY -> {
                    // Overlay is showing: freeze the timer, add nothing (Requirements 2.5, 2.6).
                    sessions[pkg] = synced
                    SessionResult.Paused
                }

                MonitoringState.SUPPRESSED,
                MonitoringState.ACTIVE -> {
                    val added = if (elapsedSeconds > 0L) elapsedSeconds else 0L
                    val updated = synced.copy(
                        sessionUsageSeconds = synced.sessionUsageSeconds + added,
                        monitoringState = MonitoringState.ACTIVE
                    )
                    sessions[pkg] = updated
                    if (updated.sessionUsageSeconds >= updated.intervalSeconds) {
                        Timber.d(
                            "RepetitiveSessionTracker: %s breached interval (%ds >= %ds)",
                            pkg, updated.sessionUsageSeconds, updated.intervalSeconds
                        )
                        SessionResult.IntervalBreached
                    } else {
                        SessionResult.Accumulating
                    }
                }
            }
        }
    }

    /**
     * Marks [pkg] as [MonitoringState.PAUSED_FOR_OVERLAY] so subsequent samples add no time
     * (Requirements 2.5, 2.6). No-op for a package with no tracked cycle.
     */
    fun markPausedForOverlay(pkg: String) {
        synchronized(lock) {
            val current = sessions[pkg] ?: return
            sessions[pkg] = current.copy(monitoringState = MonitoringState.PAUSED_FOR_OVERLAY)
        }
        Timber.d("RepetitiveSessionTracker: %s paused for overlay", pkg)
    }

    /**
     * Handles overlay dismissal followed by the Monitored_App returning to the foreground:
     * resets `Session_Usage` to 0 and returns the state to [MonitoringState.ACTIVE]
     * (Requirement 2.7).
     */
    fun onOverlayDismissedAndReturned(pkg: String) {
        synchronized(lock) {
            val current = sessions[pkg] ?: return
            sessions[pkg] = current.copy(
                sessionUsageSeconds = 0L,
                monitoringState = MonitoringState.ACTIVE
            )
        }
        Timber.d("RepetitiveSessionTracker: %s dismissed + returned — Session_Usage reset", pkg)
    }

    /**
     * Discards the current cycle for [pkg]: `Session_Usage` back to 0 and state to
     * [MonitoringState.ACTIVE]. Called by the service on foreground loss during a deferred
     * interception (OQ-2), consistent with the resume-and-reset rule in Requirement 2.7.
     */
    fun reset(pkg: String) {
        synchronized(lock) {
            sessions.remove(pkg)
        }
        Timber.v("RepetitiveSessionTracker: %s cycle reset (foreground loss / deferral discard)", pkg)
    }

    /**
     * Returns the current Monitoring_State for [pkg], defaulting to [MonitoringState.ACTIVE]
     * for a package with no tracked cycle yet.
     */
    fun stateOf(pkg: String): MonitoringState {
        return synchronized(lock) {
            sessions[pkg]?.monitoringState ?: MonitoringState.ACTIVE
        }
    }

    /**
     * Returns the current `Session_Usage` seconds for [pkg], or 0 if no cycle is tracked.
     * Exposed for the service (task 6.1) and tests; the state is otherwise encapsulated.
     */
    fun sessionUsageSeconds(pkg: String): Long {
        return synchronized(lock) {
            sessions[pkg]?.sessionUsageSeconds ?: 0L
        }
    }
}

/**
 * Result of a [RepetitiveSessionTracker.onForeground] sample.
 */
sealed interface SessionResult {
    /** The sample was counted toward the current cycle; the interval is not yet reached. */
    data object Accumulating : SessionResult

    /** The overlay is showing ([MonitoringState.PAUSED_FOR_OVERLAY]); no time was added. */
    data object Paused : SessionResult

    /** `Session_Usage` reached or exceeded the interval; the service should present the overlay. */
    data object IntervalBreached : SessionResult

    /** The package is not governed by an active `Repetitive` Rule. */
    data object NotRepetitive : SessionResult
}

/**
 * Ephemeral per-cycle state for a `Repetitive` Rule (in-memory only; never persisted).
 *
 * @property packageName the Monitored_App package this cycle belongs to.
 * @property intervalSeconds the configured Repetitive_Interval expressed in seconds.
 * @property sessionUsageSeconds accumulated foreground seconds in the current cycle (0..interval+).
 * @property monitoringState the current Monitoring_State for the Rule.
 */
data class RepetitiveSessionState(
    val packageName: String,
    val intervalSeconds: Int,
    val sessionUsageSeconds: Long,
    val monitoringState: MonitoringState
)
