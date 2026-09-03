package com.dopashift.interception

// Feature: screen-time-interception-engine, Property 20

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

/**
 * Property 20: Deferral holds up to the window bound.
 *
 * *For any* breach recorded at time `t0` while a Suppression_Context is active, the deferred
 * Intercept_Screen remains held (not presented, not discarded) for every elapsed time strictly
 * less than the 300s [DeferredInterceptCoordinator.DEFERRAL_WINDOW], provided suppression stays
 * active and the Monitored_App stays foreground: [DeferredInterceptCoordinator.evaluate] returns
 * [Decision.Hold]. At exactly `t0 + 300s` (and beyond) while still suppressed, the window bound is
 * reached and [DeferredInterceptCoordinator.evaluate] returns [Decision.Discard] (Requirement 5.6
 * upper bound of the 5.3 deferral window).
 *
 * **Validates: Requirements 5.3**
 *
 * Property-based test following existing module conventions (JUnit4 + randomized iterations via
 * `kotlin.random.Random` and `repeat(N>=100)`, as in [FocusExtensionCapPropertyTest] /
 * [SuppressionContextPropertyTest]); Kotest is not on this module's test classpath, so the
 * established randomized-input style is used. A [MutableClock] provides a controllable/advanceable
 * instant, and a toggleable [FakeSuppressionSignalSource] keeps a Suppression_Context active so the
 * window bound — not suppression clearing — governs the outcome.
 */
class DeferralWindowBoundPropertyTest {

    /**
     * A controllable clock whose instant can be advanced, used to drive the coordinator's
     * `onBreach` timestamp deterministically (mirrors the [FocusExtensionCapPropertyTest] clock).
     */
    private class MutableClock(
        private var current: Instant,
        private val zone: ZoneId
    ) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(z: ZoneId): Clock = MutableClock(current, z)
        override fun instant(): Instant = current
        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }

    /**
     * Fake [SuppressionSignalSource] whose signals are configurable; here we keep a call active so
     * the Suppression_Context remains active for the whole deferral window.
     */
    private class FakeSuppressionSignalSource(
        var callActive: Boolean = false,
        var emergencyDialerActive: Boolean = false,
        var alarmRinging: Boolean = false,
        var navigationActive: Boolean = false
    ) : SuppressionSignalSource {
        override fun isCallActive(): Boolean = callActive
        override fun isEmergencyDialerActive(): Boolean = emergencyDialerActive
        override fun isAlarmRinging(): Boolean = alarmRinging
        override fun isNavigationActive(): Boolean = navigationActive
    }

    private val window = DeferredInterceptCoordinator.DEFERRAL_WINDOW
    private val windowSeconds = window.seconds // 300

    private fun coordinator(clock: Clock): DeferredInterceptCoordinator {
        // Keep a call active so the Suppression_Context is continuously suppressing; a fresh
        // FocusExtensionManager (no grants) never suppresses, so suppression is driven solely by
        // the signal source.
        val detector = SuppressionContextDetector(FakeSuppressionSignalSource(callActive = true))
        val focus = FocusExtensionManager(clock, ZoneId.of("Asia/Kolkata"))
        return DeferredInterceptCoordinator(detector, focus, clock)
    }

    /**
     * Property: while suppression stays active and the app stays foreground, every elapsed offset
     * strictly within [0, 300)s yields [Decision.Hold]; the boundary at exactly 300s (and beyond)
     * yields [Decision.Discard].
     */
    @Test
    fun `property - deferral holds strictly within the 300s window and discards at the bound`() {
        val random = Random(seed = 2003)

        repeat(200) { iteration ->
            val start = Instant.parse("2024-06-15T00:00:00Z")
                .plus(Duration.ofSeconds(random.nextLong(0, 86_400)))
            val clock = MutableClock(start, ZoneOffset.UTC)
            val coordinator = coordinator(clock)
            val pkg = "com.defer.app.$iteration.${random.nextInt(0, 100_000)}"

            // Breach while suppressed → a deferral is recorded at t0 = start.
            assertEquals(
                "iteration $iteration: breach while suppressed must defer",
                BreachOutcome.Deferred,
                coordinator.onBreach(pkg)
            )

            // A batch of random offsets strictly within the window must all Hold. The deferral
            // start is pinned to `start`; evaluate() computes elapsed from that instant, so we can
            // probe arbitrary offsets in any order without mutating state (Hold is non-terminal).
            repeat(5) {
                val offsetSeconds = random.nextLong(0, windowSeconds) // [0, 300)
                val now = start.plus(Duration.ofSeconds(offsetSeconds))
                assertEquals(
                    "iteration $iteration: elapsed ${offsetSeconds}s (< 300s) suppressed+foreground must Hold",
                    Decision.Hold,
                    coordinator.evaluate(pkg, isAppForeground = true, now = now)
                )
            }

            // Explicitly pin the just-below-bound instant (299s) as Hold.
            assertEquals(
                "iteration $iteration: elapsed 299s must still Hold",
                Decision.Hold,
                coordinator.evaluate(
                    pkg,
                    isAppForeground = true,
                    now = start.plus(Duration.ofSeconds(windowSeconds - 1))
                )
            )

            // The window bound at exactly 300s: the deferral expires → Discard (Req 5.6),
            // even though the Suppression_Context is still active.
            assertEquals(
                "iteration $iteration: elapsed exactly 300s must Discard (window bound reached)",
                Decision.Discard,
                coordinator.evaluate(
                    pkg,
                    isAppForeground = true,
                    now = start.plus(window)
                )
            )
        }
    }

    /**
     * Property: beyond the bound (any offset > 300s) also discards while still suppressed. Uses a
     * fresh coordinator per iteration since Discard clears the deferral. Confirms the window is a
     * hard upper bound, not merely satisfied at the exact boundary.
     */
    @Test
    fun `property - any elapsed at or beyond 300s discards while still suppressed`() {
        val random = Random(seed = 5006)

        repeat(150) { iteration ->
            val start = Instant.parse("2024-01-01T12:00:00Z")
                .plus(Duration.ofSeconds(random.nextLong(0, 86_400)))
            val clock = MutableClock(start, ZoneOffset.UTC)
            val coordinator = coordinator(clock)
            val pkg = "com.defer.beyond.$iteration.${random.nextInt(0, 100_000)}"

            assertEquals(BreachOutcome.Deferred, coordinator.onBreach(pkg))

            // Offset in [300, 300 + 3600]s — at or beyond the bound.
            val offsetSeconds = windowSeconds + random.nextLong(0, 3_600)
            val now = start.plus(Duration.ofSeconds(offsetSeconds))
            assertEquals(
                "iteration $iteration: elapsed ${offsetSeconds}s (>= 300s) must Discard",
                Decision.Discard,
                coordinator.evaluate(pkg, isAppForeground = true, now = now)
            )
        }
    }
}
