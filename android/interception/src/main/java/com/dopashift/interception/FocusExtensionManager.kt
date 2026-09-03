package com.dopashift.interception

import com.dopashift.domain.entity.FocusExtensionState
import timber.log.Timber
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the **Focus_Extension** state machine for each Monitored_App, per calendar day.
 *
 * A Focus_Extension is a short grace window granted after the user engages with the
 * Intercept_Screen (checking a habit or completing a to-do). While an extension is active
 * the Intercept_Screen must not re-trigger for that app (Requirement 4.11). The manager:
 *
 * - Grants an extension **only** when at least one action was recorded (Requirement 4.9),
 *   with expiry equal to the depletion (grant) time plus the configured duration.
 * - Reports whether an extension is currently active via [isExtensionActive] using a
 *   half-open interval `[start, expiry)` so an extension that expires exactly at `now`
 *   is *not* considered active (Requirement 4.11).
 * - Caps grants at [MAX_EXTENSIONS_PER_DAY] per app per calendar day (Requirement 4.12) and
 *   signals [GrantResult.CapReached] once exhausted so the caller can surface the localized
 *   "no additional extensions available today" message (Requirement 4.13).
 * - Resets per-app counters at the local midnight boundary, using the injectable [clock] and
 *   [timeZone] — mirroring the reset pattern in [AllowanceTracker].
 *
 * The extension duration itself is carried by [InterceptionConfig.focusExtensionMinutes]
 * (constrained 1..15, default 5 — Requirements 4.9, 4.10). Per **OQ-1**, the *count* cap of
 * 3/day is fixed and non-configurable for v1.
 *
 * State is held in memory only (extensions are ephemeral and day-scoped); it is never synced.
 * An injectable [Clock] + [ZoneId] make time-dependent behavior deterministic in tests.
 */
@Singleton
class FocusExtensionManager @Inject constructor(
    private val clock: Clock,
    private var timeZone: ZoneId = ZoneId.systemDefault()
) {

    /**
     * In-memory per-app Focus_Extension state, keyed by package name. Entries are refreshed to the
     * current calendar day on access (see [currentState]); stale (previous-day) entries are reset.
     */
    private val states: MutableMap<String, FocusExtensionState> = mutableMapOf()

    /**
     * Updates the timezone used for the midnight-reset boundary. Mirrors
     * [AllowanceTracker.userTimeZone]; should be set from the user's preferences.
     */
    fun setTimeZone(zone: ZoneId) {
        timeZone = zone
    }

    /**
     * Grants a Focus_Extension for [pkg] when the Intercept_Screen engagement was satisfied.
     *
     * Grants **iff** [engaged] is true (at least one action recorded — Requirement 4.9) **and**
     * fewer than [MAX_EXTENSIONS_PER_DAY] extensions have already been granted for [pkg] today
     * (Requirement 4.12). The granted extension expires at the current instant plus
     * [InterceptionConfig.focusExtensionMinutes].
     *
     * @param pkg the Monitored_App package the depletion occurred for.
     * @param engaged whether the user recorded at least one action on the Intercept_Screen.
     * @param config the active interception config supplying the extension duration.
     * @return [GrantResult.Granted] with the expiry and remaining grants when an extension is
     *   granted; [GrantResult.NoActionRecorded] when [engaged] is false; [GrantResult.CapReached]
     *   when the 3/day cap is already exhausted.
     */
    fun grantIfEngaged(
        pkg: String,
        engaged: Boolean,
        config: InterceptionConfig
    ): GrantResult {
        val today = resolveToday()
        val current = currentState(pkg, today)

        if (!engaged) {
            // No action recorded — no extension is granted (Requirement 4.9).
            return GrantResult.NoActionRecorded
        }

        if (current.extensionsGranted >= MAX_EXTENSIONS_PER_DAY) {
            // 3/day cap exhausted — present the Intercept_Screen without a further grant
            // and signal the caller to show the "no extensions left today" message (Req 4.13).
            Timber.i("FocusExtension cap reached for %s (%d/day)", pkg, MAX_EXTENSIONS_PER_DAY)
            return GrantResult.CapReached
        }

        val now = clock.instant()
        val expiresAt = now.plus(Duration.ofMinutes(config.focusExtensionMinutes.toLong()))
        val granted = current.extensionsGranted + 1
        states[pkg] = current.copy(
            extensionsGranted = granted,
            activeUntil = expiresAt
        )
        val remaining = MAX_EXTENSIONS_PER_DAY - granted
        Timber.i(
            "FocusExtension granted for %s until %s (%d used, %d remaining)",
            pkg, expiresAt, granted, remaining
        )
        return GrantResult.Granted(expiresAt = expiresAt, remaining = remaining)
    }

    /**
     * Reports whether a Focus_Extension is currently active for [pkg] at [now].
     *
     * Uses a half-open interval `[start, activeUntil)`: an extension whose expiry equals [now]
     * is treated as **expired** (not active), so re-triggering resumes exactly at expiry
     * (Requirement 4.11).
     *
     * @param pkg the Monitored_App package to check.
     * @param now the instant to evaluate against (caller supplies from an injectable clock).
     * @return true when an unexpired extension covers [now]; false otherwise.
     */
    fun isExtensionActive(pkg: String, now: Instant): Boolean {
        val today = LocalDate.ofInstant(now, timeZone)
        val state = states[pkg] ?: return false
        // A grant from a prior calendar day never keeps an app suppressed today.
        if (state.date != today) return false
        val activeUntil = state.activeUntil ?: return false
        return now.isBefore(activeUntil)
    }

    /**
     * Returns the number of Focus_Extensions already granted for [pkg] on the current calendar
     * day (0..[MAX_EXTENSIONS_PER_DAY]). Stale previous-day state resets to 0 (Requirement 4.12).
     */
    fun extensionsUsedToday(pkg: String): Int {
        val today = resolveToday()
        return currentState(pkg, today).extensionsGranted
    }

    /**
     * Returns true when at least one more Focus_Extension can be granted for [pkg] today
     * (i.e. the 3/day cap is not yet reached — Requirement 4.13).
     */
    fun canGrantMore(pkg: String): Boolean {
        return extensionsUsedToday(pkg) < MAX_EXTENSIONS_PER_DAY
    }

    /**
     * Resolves the [FocusExtensionState] for [pkg] on [today], resetting any stale entry from a
     * previous calendar day (local-midnight reset — Requirement 4.12). The refreshed state is
     * written back so subsequent reads within the same day are consistent.
     */
    private fun currentState(pkg: String, today: LocalDate): FocusExtensionState {
        val existing = states[pkg]
        if (existing == null || existing.date != today) {
            if (existing != null) {
                Timber.d("FocusExtension midnight reset for %s (was %s, now %s)", pkg, existing.date, today)
            }
            val fresh = FocusExtensionState(packageName = pkg, date = today)
            states[pkg] = fresh
            return fresh
        }
        return existing
    }

    /** Resolves "today" from the injectable [clock] in the configured [timeZone]. */
    private fun resolveToday(): LocalDate = LocalDate.now(clock.withZone(timeZone))

    companion object {
        /**
         * Maximum Focus_Extensions granted per app per calendar day. Fixed at 3 for v1 and
         * intentionally non-configurable (OQ-1, Requirement 4.12).
         */
        const val MAX_EXTENSIONS_PER_DAY = 3
    }
}

/**
 * Outcome of a [FocusExtensionManager.grantIfEngaged] call.
 */
sealed interface GrantResult {
    /**
     * A Focus_Extension was granted.
     *
     * @param expiresAt the instant at which the extension expires (grant time + duration).
     * @param remaining the number of further extensions still available for the app today.
     */
    data class Granted(val expiresAt: Instant, val remaining: Int) : GrantResult

    /** No action was recorded on the Intercept_Screen — no extension granted (Requirement 4.9). */
    data object NoActionRecorded : GrantResult

    /** The 3/day cap is already exhausted — present without granting a further extension (Req 4.13). */
    data object CapReached : GrantResult
}
