package com.dopashift.interception

import com.dopashift.interception.SuppressionContextDetector.SuppressionResult
import timber.log.Timber
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the **deferral state machine** that decides when a limit-breach Intercept_Screen may be
 * presented, deferred, or discarded when a safety-critical [Suppression_Context] is active
 * (Requirements 5.3–5.6) or a [FocusExtensionManager] extension is suppressing re-triggering
 * (Requirement 4.11).
 *
 * ### Behaviour
 * When a breach occurs for a Monitored_App while presentation is suppressed — either a
 * [SuppressionContextDetector] Suppression_Context is active **or** a Focus_Extension is active for
 * the app — the coordinator records a deferral keyed by package and defers presentation for up to
 * [DEFERRAL_WINDOW] (300 seconds, Requirement 5.3). Each monitoring cycle the engine calls
 * [evaluate] with the app's current foreground state and the current instant; the coordinator
 * returns a [Decision]:
 *
 * - [Decision.Present] — suppression has cleared, the Monitored_App is still foreground, and the
 *   deferral is still within the 300s window; the caller shows the Intercept_Screen (within 2s of
 *   this cycle per Requirement 5.4). The deferral is cleared.
 * - [Decision.Discard] — the Monitored_App has left the foreground (Requirement 5.5) **or** the
 *   300s window has elapsed while still suppressed (Requirement 5.6). The deferral is cleared and
 *   the engine re-evaluates on the next monitoring cycle.
 * - [Decision.Hold] — still within the window and still suppressed; keep deferring.
 *
 * A breach that occurs while **not** suppressed is not this component's concern — the caller
 * presents immediately and does not begin a deferral (see [onBreach] return value).
 *
 * ### Testability
 * Time is read exclusively from the injectable [clock], and suppression state is delegated to the
 * already-testable [SuppressionContextDetector] and [FocusExtensionManager]. This makes every
 * transition deterministic in unit/property tests without Android runtime dependencies, mirroring
 * the [FocusExtensionManager] pattern in this module.
 *
 * State is held in memory only (deferrals are ephemeral and short-lived); it is never synced.
 */
@Singleton
class DeferredInterceptCoordinator @Inject constructor(
    private val suppressionDetector: SuppressionContextDetector,
    private val focusExtensionManager: FocusExtensionManager,
    private val clock: Clock
) {

    /**
     * In-memory per-app deferral records, keyed by package name. An entry exists only while a
     * breach is being deferred; it is removed on [Decision.Present] or [Decision.Discard].
     */
    private val deferrals: MutableMap<String, Deferral> = mutableMapOf()

    /**
     * Records that a limit breach occurred for [pkg] and decides whether presentation must be
     * deferred.
     *
     * If presentation is currently suppressed for [pkg] (a [SuppressionContextDetector]
     * Suppression_Context is active, or a Focus_Extension is active per Requirement 4.11), a
     * deferral is started at the current instant and this returns [BreachOutcome.Deferred]
     * (Requirement 5.3). Otherwise no deferral is recorded and this returns
     * [BreachOutcome.PresentNow] so the caller presents the Intercept_Screen immediately.
     *
     * An existing deferral for [pkg] is preserved (the original deferral start instant is kept so
     * the 300s window continues to run from the first breach).
     *
     * @param pkg the Monitored_App package whose limit was breached.
     * @return [BreachOutcome.Deferred] when suppressed (presentation deferred), or
     *   [BreachOutcome.PresentNow] when not suppressed (present immediately).
     */
    fun onBreach(pkg: String): BreachOutcome {
        val now = clock.instant()
        if (!isSuppressed(pkg, now)) {
            // Not suppressed — no deferral; the caller presents immediately.
            return BreachOutcome.PresentNow
        }
        val existing = deferrals[pkg]
        if (existing == null) {
            deferrals[pkg] = Deferral(startedAt = now)
            Timber.d("Deferring Intercept_Screen for %s at %s (suppressed)", pkg, now)
        }
        return BreachOutcome.Deferred
    }

    /**
     * Evaluates the deferral for [pkg] on a monitoring cycle and returns the [Decision] the engine
     * should act on.
     *
     * Decision order (Requirements 5.4–5.6):
     * 1. If there is no active deferral for [pkg], returns [Decision.Discard] (nothing to present).
     * 2. If the Monitored_App is no longer foreground, discards the deferral (Requirement 5.5).
     * 3. If the 300s window has elapsed, discards the deferral and the engine re-evaluates next
     *    cycle (Requirement 5.6) — regardless of whether suppression has cleared, since the window
     *    is the hard upper bound.
     * 4. If still suppressed (and within the window and foreground), returns [Decision.Hold].
     * 5. Otherwise suppression has cleared, the app is foreground, and the window has not elapsed:
     *    returns [Decision.Present] (Requirement 5.4). The deferral is cleared on both
     *    [Decision.Present] and [Decision.Discard].
     *
     * @param pkg the Monitored_App package being evaluated.
     * @param isAppForeground whether the Monitored_App is still in the foreground this cycle.
     * @param now the current instant (caller supplies from an injectable clock).
     * @return the [Decision] for this cycle.
     */
    fun evaluate(pkg: String, isAppForeground: Boolean, now: Instant): Decision {
        val deferral = deferrals[pkg] ?: return Decision.Discard

        // Requirement 5.5: the app left the foreground — discard without presenting.
        if (!isAppForeground) {
            deferrals.remove(pkg)
            Timber.d("Discarding deferred Intercept_Screen for %s: app left foreground", pkg)
            return Decision.Discard
        }

        // Requirement 5.6: the 300s window elapsed — discard and re-evaluate next cycle.
        // The window is inclusive of its bound: at exactly start + 300s the deferral expires.
        val elapsed = Duration.between(deferral.startedAt, now)
        if (elapsed >= DEFERRAL_WINDOW) {
            deferrals.remove(pkg)
            Timber.d(
                "Discarding deferred Intercept_Screen for %s: 300s window elapsed (%s)",
                pkg, elapsed
            )
            return Decision.Discard
        }

        // Still suppressed and within the window with the app foreground — keep deferring.
        if (isSuppressed(pkg, now)) {
            return Decision.Hold
        }

        // Requirement 5.4: suppression cleared, app foreground, within window — present.
        deferrals.remove(pkg)
        Timber.d("Presenting deferred Intercept_Screen for %s at %s", pkg, now)
        return Decision.Present
    }

    /**
     * Returns true when an Intercept_Screen presentation is currently being deferred for [pkg].
     */
    fun hasPendingDeferral(pkg: String): Boolean = deferrals.containsKey(pkg)

    /**
     * Clears any pending deferral for [pkg] without presenting (e.g. when the Rule is deleted or
     * paused). Safe to call when no deferral exists.
     */
    fun clear(pkg: String) {
        if (deferrals.remove(pkg) != null) {
            Timber.d("Cleared pending deferral for %s", pkg)
        }
    }

    /**
     * Reports whether presentation is currently suppressed for [pkg] at [now]: either a
     * Suppression_Context is active (Requirement 5.2/5.3) or a Focus_Extension is active for the
     * app (Requirement 4.11).
     */
    private fun isSuppressed(pkg: String, now: Instant): Boolean {
        val context: SuppressionResult = suppressionDetector.isSuppressed(now)
        if (context.isSuppressing) return true
        return focusExtensionManager.isExtensionActive(pkg, now)
    }

    /** A single in-flight deferral, capturing when the first breach was deferred. */
    private data class Deferral(val startedAt: Instant)

    companion object {
        /**
         * Maximum time a breach's Intercept_Screen presentation may be deferred while a
         * Suppression_Context remains active (Requirement 5.3). After this window elapses the
         * deferral is discarded and the engine re-evaluates on the next monitoring cycle
         * (Requirement 5.6).
         */
        @JvmField
        val DEFERRAL_WINDOW: Duration = Duration.ofSeconds(300)
    }
}

/**
 * Outcome of [DeferredInterceptCoordinator.onBreach].
 */
sealed interface BreachOutcome {
    /** Not suppressed — the caller presents the Intercept_Screen immediately (no deferral). */
    data object PresentNow : BreachOutcome

    /** Suppressed — presentation has been deferred; poll [DeferredInterceptCoordinator.evaluate]. */
    data object Deferred : BreachOutcome
}

/**
 * The action the engine should take for a deferred breach on a monitoring cycle
 * (Requirements 5.4–5.6).
 */
sealed interface Decision {
    /**
     * Suppression cleared, the Monitored_App is still foreground, and the 300s window has not
     * elapsed — present the deferred Intercept_Screen within 2s (Requirement 5.4).
     */
    data object Present : Decision

    /**
     * Discard the deferred Intercept_Screen without presenting: the app left the foreground
     * (Requirement 5.5) or the 300s window elapsed while still suppressed (Requirement 5.6). The
     * engine re-evaluates on the next monitoring cycle.
     */
    data object Discard : Decision

    /** Still within the window and still suppressed with the app foreground — keep deferring. */
    data object Hold : Decision
}
