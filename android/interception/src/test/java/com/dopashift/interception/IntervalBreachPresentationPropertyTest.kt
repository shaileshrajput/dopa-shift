package com.dopashift.interception

import com.dopashift.domain.entity.InterceptionRule
import com.dopashift.domain.entity.LimitType
import com.dopashift.domain.entity.MonitoringState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

// Feature: android-app-limit-repitative, Property 5

/**
 * Property 5: Interval breach triggers presentation within one sampling interval
 * (subject to the Suppression_Context check).
 *
 * *For any* `Repetitive` Rule whose `Session_Usage` reaches or exceeds its interval, a
 * presentation is triggered within one sampling interval, subject to the Suppression_Context
 * check.
 *
 * **Validates: Requirements 2.3, 2.4**
 *
 * ### What this test exercises
 * [InterceptionService] is an Android [android.app.Service] and cannot be unit-tested without a
 * Robolectric/instrumented harness (see [InterceptionServiceWiringTest]). This test therefore
 * reconstructs the exact routing the service performs for a `Repetitive` Rule — the same
 * collaborators wired the same way — and asserts the routing decision, which is where Property 5
 * lives:
 *
 * 1. Each foreground sample is routed through [RepetitiveSessionTracker.onForeground]
 *    (as `handleForegroundApp` does). The sample that pushes `Session_Usage >= interval` returns
 *    [SessionResult.IntervalBreached].
 * 2. On `IntervalBreached`, the breach is routed through [DeferredInterceptCoordinator.onBreach]
 *    (as `onRepetitiveIntervalBreached` does):
 *    - not suppressed  -> [BreachOutcome.PresentNow]: the overlay is presented on the *same*
 *      sampling cycle as the breach (i.e. within one sampling interval — Requirement 2.3), and the
 *      Rule is frozen via [RepetitiveSessionTracker.markPausedForOverlay];
 *    - suppressed       -> [BreachOutcome.Deferred]: presentation is withheld (Requirement 2.4)
 *      until [DeferredInterceptCoordinator.evaluate] clears it on a later cycle (still within the
 *      300s window while the app stays foreground), at which point it is presented.
 *
 * The "presentation" side effect the service performs (`presentOverlay`) is a fire-and-forget
 * Android call, so this test models it with a small recorder that captures which package the
 * service would have presented and on which cycle, mirroring the service's own control flow
 * exactly.
 *
 * A controllable/advanceable [MutableClock] backs the coordinator's breach timestamping and a
 * toggleable [ToggleableSuppressionSignalSource] drives the Suppression_Context deterministically,
 * matching the conventions established in [DeferredInterceptPresentationPropertyTest]. The
 * [FocusExtensionManager] is kept quiescent (no grants) so suppression comes solely from the
 * signal source.
 *
 * Module convention: JUnit4 + kotlin.random.Random + repeat(N>=100) + kotlinx.coroutines.test.runTest;
 * Kotest is not on this module's test classpath, so the established randomized-input style is used.
 */
class IntervalBreachPresentationPropertyTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val testUserId = testUuid("user-property5")
    private val window: Duration = DeferredInterceptCoordinator.DEFERRAL_WINDOW // 300s

    /** Advanceable [Clock] driving the coordinator's `onBreach`/`evaluate` timestamping. */
    private class MutableClock(
        private var current: Instant,
        private val zone: ZoneId
    ) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(z: ZoneId): Clock = MutableClock(current, z)
        override fun instant(): Instant = current
        fun set(instant: Instant) {
            current = instant
        }
    }

    /** Toggleable suppression signal so the Suppression_Context can be flipped deterministically. */
    private class ToggleableSuppressionSignalSource(
        @Volatile var suppressing: Boolean = false
    ) : SuppressionSignalSource {
        override fun isCallActive(): Boolean = suppressing
        override fun isEmergencyDialerActive(): Boolean = false
        override fun isAlarmRinging(): Boolean = false
        override fun isNavigationActive(): Boolean = false
    }

    /**
     * Minimal reconstruction of the service's breach routing for a `Repetitive` Rule, wiring the
     * real [RepetitiveSessionTracker] and [DeferredInterceptCoordinator] the same way
     * [InterceptionService] does. Captures the presentations the service would perform.
     */
    private class RoutingHarness(
        val tracker: RepetitiveSessionTracker,
        val coordinator: DeferredInterceptCoordinator,
        val clock: MutableClock
    ) {
        /** Packages presented this run, in order (one entry per `presentOverlay`). */
        val presented = mutableListOf<String>()

        /** Packages currently deferred and awaiting re-evaluation, mirroring `pendingDeferrals`. */
        val pendingDeferrals = mutableSetOf<String>()

        /**
         * Mirrors [InterceptionService.handleForegroundApp] for the `Repetitive` path: routes the
         * sample through the tracker and, on breach, through [onRepetitiveIntervalBreached].
         * Returns the [SessionResult] so the caller can assert the breach point.
         */
        suspend fun onSample(pkg: String, elapsedSeconds: Long): SessionResult {
            val result = tracker.onForeground(pkg, elapsedSeconds)
            if (result == SessionResult.IntervalBreached) {
                onRepetitiveIntervalBreached(pkg)
            }
            return result
        }

        /** Mirrors [InterceptionService.onRepetitiveIntervalBreached]. */
        private fun onRepetitiveIntervalBreached(pkg: String) {
            when (coordinator.onBreach(pkg)) {
                BreachOutcome.PresentNow -> {
                    presentOverlay(pkg)
                    tracker.markPausedForOverlay(pkg)
                }
                BreachOutcome.Deferred -> {
                    pendingDeferrals.add(pkg)
                }
            }
        }

        /**
         * Mirrors [InterceptionService.evaluatePendingDeferrals] for `Repetitive` deferrals: on
         * [Decision.Present] present + pause; on [Decision.Discard] reset the cycle.
         */
        fun evaluatePendingDeferrals(foregroundPkg: String?, now: Instant) {
            val snapshot = pendingDeferrals.toList()
            for (pkg in snapshot) {
                when (coordinator.evaluate(pkg, isAppForeground = pkg == foregroundPkg, now = now)) {
                    Decision.Present -> {
                        pendingDeferrals.remove(pkg)
                        presentOverlay(pkg)
                        tracker.markPausedForOverlay(pkg)
                    }
                    Decision.Discard -> {
                        pendingDeferrals.remove(pkg)
                        tracker.reset(pkg)
                    }
                    Decision.Hold -> { /* keep deferring */ }
                }
            }
        }

        private fun presentOverlay(pkg: String) {
            presented.add(pkg)
        }
    }

    /** Builds a `Repetitive` domain Rule with the given interval (whole minutes, 1..120). */
    private fun repetitiveRule(
        packageName: String,
        intervalMinutes: Int
    ): InterceptionRule = InterceptionRule(
        id = testUuid(packageName),
        userId = testUserId,
        appPackageName = packageName,
        dailyLimitMinutes = 60, // unused for Repetitive but must satisfy the 1..480 invariant
        limitType = LimitType.Repetitive,
        repetitiveIntervalMinutes = intervalMinutes,
        enabled = true,
        pausedForDate = null,
        createdAt = Instant.ofEpochMilli(1_700_000_000_000L)
    )

    /** Wires the harness with suppression initially [suppressing] and the clock fixed at [t0]. */
    private fun buildHarness(
        t0: Instant,
        suppressing: Boolean
    ): Pair<RoutingHarness, ToggleableSuppressionSignalSource> {
        val repo = FakeInterceptionRuleRepository()
        val tracker = RepetitiveSessionTracker(repo).apply { userId = testUserId }
        val signal = ToggleableSuppressionSignalSource(suppressing = suppressing)
        val detector = SuppressionContextDetector(signal)
        val fem = FocusExtensionManager(Clock.fixed(t0, ZoneOffset.UTC), zone) // quiescent
        val clock = MutableClock(t0, zone)
        val coordinator = DeferredInterceptCoordinator(detector, fem, clock)
        val harness = RoutingHarness(tracker, coordinator, clock)
        // Expose the repo through the tracker's repository by returning it for rule setup.
        harnessRepo = repo
        return harness to signal
    }

    /** Side channel to the repo backing the most recently built harness (single-threaded test). */
    private lateinit var harnessRepo: FakeInterceptionRuleRepository

    /**
     * Property (Requirement 2.3): when no Suppression_Context is active, the sampling cycle whose
     * `Session_Usage` first reaches or exceeds the interval both reports [SessionResult.IntervalBreached]
     * and results in a presentation on that same cycle (within one sampling interval), transitioning
     * the Rule to [MonitoringState.PAUSED_FOR_OVERLAY].
     */
    @Test
    fun `property - unsuppressed breach presents within the breaching sampling cycle`() = runTest {
        val random = Random(seed = 20302)

        repeat(300) { iteration ->
            val t0 = Instant.parse("2024-06-15T08:00:00Z").plusSeconds(random.nextLong(0, 6 * 3600))
            val (harness, _) = buildHarness(t0, suppressing = false)

            // Interval 1..120 min; sample size 3..30s so several cycles are needed to breach.
            val intervalMinutes = random.nextInt(1, 121)
            val intervalSeconds = intervalMinutes.toLong() * 60L
            val sampleSeconds = random.nextLong(3, 31)
            val pkg = "com.repetitive.present.$iteration"
            harnessRepo.addRule(repetitiveRule(pkg, intervalMinutes))

            var breachedCycle = -1
            var cycle = 0
            // Sample until the interval is reached (bounded generously to avoid a runaway loop).
            val maxCycles = (intervalSeconds / 3L + 5L).toInt()
            while (cycle < maxCycles) {
                val result = harness.onSample(pkg, sampleSeconds)
                if (result == SessionResult.IntervalBreached) {
                    breachedCycle = cycle
                    break
                }
                // Not yet breached: nothing should have been presented and state stays ACTIVE.
                assertTrue(
                    "iteration $iteration: no presentation before the breach",
                    harness.presented.isEmpty()
                )
                assertEquals(
                    "iteration $iteration: state ACTIVE before the breach",
                    MonitoringState.ACTIVE,
                    harness.tracker.stateOf(pkg)
                )
                cycle++
            }

            assertTrue("iteration $iteration: interval must be breached within bound", breachedCycle >= 0)
            // Requirement 2.3: the breach cycle itself presents (within one sampling interval).
            assertEquals(
                "iteration $iteration: exactly one presentation on the breach cycle",
                listOf(pkg),
                harness.presented
            )
            // Requirement 2.5 wiring: presenting freezes the cycle.
            assertEquals(
                "iteration $iteration: Rule paused for overlay after presenting",
                MonitoringState.PAUSED_FOR_OVERLAY,
                harness.tracker.stateOf(pkg)
            )
        }
    }

    /**
     * Property (Requirement 2.4): when a Suppression_Context is active at breach time, presentation
     * is withheld (deferred, not presented). Once suppression clears while the app is still
     * foreground and within the 300s window, the very next evaluate cycle presents.
     */
    @Test
    fun `property - suppressed breach defers then presents once suppression clears`() = runTest {
        val random = Random(seed = 71177)

        repeat(300) { iteration ->
            val t0 = Instant.parse("2024-06-15T09:00:00Z").plusSeconds(random.nextLong(0, 6 * 3600))
            val (harness, signal) = buildHarness(t0, suppressing = true)

            val intervalMinutes = random.nextInt(1, 121)
            val intervalSeconds = intervalMinutes.toLong() * 60L
            val sampleSeconds = random.nextLong(3, 31)
            val pkg = "com.repetitive.defer.$iteration"
            harnessRepo.addRule(repetitiveRule(pkg, intervalMinutes))

            // Drive samples until the interval breaches.
            var breached = false
            val maxCycles = (intervalSeconds / 3L + 5L).toInt()
            var cycle = 0
            while (cycle < maxCycles && !breached) {
                breached = harness.onSample(pkg, sampleSeconds) == SessionResult.IntervalBreached
                cycle++
            }
            assertTrue("iteration $iteration: interval must breach", breached)

            // Requirement 2.4: suppressed at breach -> deferred, nothing presented yet.
            assertTrue(
                "iteration $iteration: suppressed breach must not present",
                harness.presented.isEmpty()
            )
            assertTrue(
                "iteration $iteration: a deferral must be pending while suppressed",
                harness.pendingDeferrals.contains(pkg)
            )
            assertTrue(
                "iteration $iteration: deferral tracked in coordinator",
                harness.coordinator.hasPendingDeferral(pkg)
            )

            // While still suppressed within the window, a re-evaluation holds (still no present).
            val holdOffset = random.nextLong(0, window.seconds)
            val holdNow = t0.plusSeconds(holdOffset)
            harness.clock.set(holdNow)
            harness.evaluatePendingDeferrals(foregroundPkg = pkg, now = holdNow)
            assertTrue(
                "iteration $iteration: still suppressed within window must not present",
                harness.presented.isEmpty()
            )
            assertTrue(
                "iteration $iteration: still deferred while suppressed",
                harness.pendingDeferrals.contains(pkg)
            )

            // Suppression clears, app still foreground, still within window -> present next cycle.
            signal.suppressing = false
            val clearOffset = random.nextLong(holdOffset, window.seconds)
            val clearNow = t0.plusSeconds(clearOffset)
            harness.clock.set(clearNow)
            harness.evaluatePendingDeferrals(foregroundPkg = pkg, now = clearNow)

            assertEquals(
                "iteration $iteration: presents exactly once when suppression clears in time",
                listOf(pkg),
                harness.presented
            )
            assertFalse(
                "iteration $iteration: deferral cleared once presented",
                harness.coordinator.hasPendingDeferral(pkg)
            )
            assertEquals(
                "iteration $iteration: Rule paused for overlay after deferred present",
                MonitoringState.PAUSED_FOR_OVERLAY,
                harness.tracker.stateOf(pkg)
            )
        }
    }

    /**
     * Property (Requirement 2.4, discard path): if the Suppression_Context stays active until the
     * 300s window elapses, the deferred breach is discarded (never presented) and the cycle is
     * reset — a stale count cannot immediately re-trigger.
     */
    @Test
    fun `property - suppressed past the window discards without presenting and resets`() = runTest {
        val random = Random(seed = 90909)

        repeat(200) { iteration ->
            val t0 = Instant.parse("2024-06-15T10:00:00Z").plusSeconds(random.nextLong(0, 6 * 3600))
            val (harness, _) = buildHarness(t0, suppressing = true)

            val intervalMinutes = random.nextInt(1, 121)
            val intervalSeconds = intervalMinutes.toLong() * 60L
            val sampleSeconds = random.nextLong(3, 31)
            val pkg = "com.repetitive.timeout.$iteration"
            harnessRepo.addRule(repetitiveRule(pkg, intervalMinutes))

            var breached = false
            val maxCycles = (intervalSeconds / 3L + 5L).toInt()
            var cycle = 0
            while (cycle < maxCycles && !breached) {
                breached = harness.onSample(pkg, sampleSeconds) == SessionResult.IntervalBreached
                cycle++
            }
            assertTrue("iteration $iteration: interval must breach", breached)
            assertTrue("iteration $iteration: deferred while suppressed", harness.pendingDeferrals.contains(pkg))

            // Remain suppressed past the 300s window, app still foreground -> discard.
            val elapsed = window.seconds + random.nextLong(0, 600)
            val now = t0.plusSeconds(elapsed)
            harness.clock.set(now)
            harness.evaluatePendingDeferrals(foregroundPkg = pkg, now = now)

            assertTrue(
                "iteration $iteration: suppressed past window must never present",
                harness.presented.isEmpty()
            )
            assertFalse(
                "iteration $iteration: deferral discarded after the window",
                harness.coordinator.hasPendingDeferral(pkg)
            )
            // Cycle reset on discard (OQ-2): Session_Usage back to 0, state ACTIVE.
            assertEquals(
                "iteration $iteration: Session_Usage reset after discard",
                0L,
                harness.tracker.sessionUsageSeconds(pkg)
            )
            assertEquals(
                "iteration $iteration: state ACTIVE after discard",
                MonitoringState.ACTIVE,
                harness.tracker.stateOf(pkg)
            )
        }
    }
}

// Fakes are in TestFakes.kt (shared across interception test files)
