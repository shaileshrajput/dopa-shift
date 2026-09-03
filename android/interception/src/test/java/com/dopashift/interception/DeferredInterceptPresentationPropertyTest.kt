package com.dopashift.interception

// Feature: screen-time-interception-engine, Property 21

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

/**
 * Property 21: Deferred intercept presents only when suppression clears in time and the
 * Monitored_App is still in the foreground.
 *
 * After a deferral begins while suppressed, on the next monitoring cycle
 * [DeferredInterceptCoordinator.evaluate] transitions to [Decision.Present] **if and only if**
 * all three conditions hold:
 *  - suppression has cleared (Requirement 5.4), AND
 *  - the Monitored_App is still in the foreground (Requirement 5.5), AND
 *  - the elapsed time since the deferral began is `< 300s` (Requirement 5.6).
 *
 * Otherwise the coordinator MUST NOT present:
 *  - if the app has left the foreground it discards regardless of suppression (Requirement 5.5);
 *  - if suppression remains active until `elapsed >= 300s` it discards (Requirement 5.6);
 *  - while still suppressed and within the window with the app foreground it holds.
 *
 * **Validates: Requirements 5.4, 5.5, 5.6**
 *
 * Property-based test following existing module conventions (JUnit4 + randomized iterations via
 * `kotlin.random.Random` and `repeat(N >= 100)`, as in [FocusExtensionCapPropertyTest] /
 * [SuppressionContextPropertyTest]); Kotest is not on this module's test classpath, so the
 * established randomized-input style with >=100 iterations is used. A controllable/advanceable
 * [MutableClock] backs the coordinator's `onBreach` timestamping, and a toggleable
 * [ToggleableSuppressionSignalSource] drives suppression state deterministically. The
 * [FocusExtensionManager] is kept quiescent (no grants) so suppression comes solely from the
 * signal source and never spuriously blocks presentation.
 */
class DeferredInterceptPresentationPropertyTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    /**
     * Advanceable [Clock] whose current instant can be moved forward, letting us drive
     * [DeferredInterceptCoordinator.onBreach]'s timestamping deterministically.
     */
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

    /**
     * Fake [SuppressionSignalSource] whose single active-call signal is toggleable so the
     * coordinator's suppression state can be flipped between cycles without Android deps.
     */
    private class ToggleableSuppressionSignalSource(
        @Volatile var suppressing: Boolean = false
    ) : SuppressionSignalSource {
        override fun isCallActive(): Boolean = suppressing
        override fun isEmergencyDialerActive(): Boolean = false
        override fun isAlarmRinging(): Boolean = false
        override fun isNavigationActive(): Boolean = false
    }

    private val window: Duration = DeferredInterceptCoordinator.DEFERRAL_WINDOW // 300s

    /**
     * Builds a coordinator with the breach clock fixed at [t0], suppression initially active
     * (so the breach defers), and a quiescent FocusExtensionManager (no grants).
     */
    private fun buildDeferred(
        t0: Instant
    ): Triple<DeferredInterceptCoordinator, ToggleableSuppressionSignalSource, MutableClock> {
        val signal = ToggleableSuppressionSignalSource(suppressing = true)
        val detector = SuppressionContextDetector(signal)
        // FocusExtensionManager with no grants -> isExtensionActive always false, so suppression
        // is driven solely by the toggleable signal source.
        val fem = FocusExtensionManager(Clock.fixed(t0, ZoneOffset.UTC), zone)
        val clock = MutableClock(t0, zone)
        val coordinator = DeferredInterceptCoordinator(detector, fem, clock)
        return Triple(coordinator, signal, clock)
    }

    /**
     * Central property: over randomized clearance times, foreground states, and elapsed offsets,
     * [Decision.Present] is returned from a single evaluate cycle **iff** suppression has cleared
     * AND the app is foreground AND elapsed < 300s. This exercises all three requirement branches
     * (5.4 present, 5.5 discard-on-background, 5.6 discard-on-timeout) in one sweep.
     */
    @Test
    fun `property - present iff suppression cleared and foreground and within window`() {
        val random = Random(seed = 5406)

        repeat(400) { iteration ->
            val t0 = Instant.parse("2024-06-15T08:00:00Z")
                .plusSeconds(random.nextLong(0, 6 * 3600))
            val (coordinator, signal, clock) = buildDeferred(t0)
            val pkg = "com.deferred.app.$iteration"

            // Breach while suppressed -> deferral begins.
            val outcome = coordinator.onBreach(pkg)
            assertEquals(
                "iteration $iteration: breach while suppressed must defer",
                BreachOutcome.Deferred,
                outcome
            )
            assertTrue(
                "iteration $iteration: a deferral must be pending",
                coordinator.hasPendingDeferral(pkg)
            )

            // Randomize the three independent inputs.
            // elapsed offset in [0, 400s): straddles the 300s window boundary on both sides.
            val elapsedSeconds = random.nextLong(0, 400)
            val suppressionCleared = random.nextBoolean()
            val foreground = random.nextBoolean()

            // Toggle suppression for this cycle; advance the evaluate `now` by elapsed.
            signal.suppressing = !suppressionCleared
            val now = t0.plusSeconds(elapsedSeconds)
            clock.set(now)

            val decision = coordinator.evaluate(pkg, isAppForeground = foreground, now = now)

            val withinWindow = elapsedSeconds < window.seconds // 300s is the exclusive bound
            val shouldPresent = suppressionCleared && foreground && withinWindow

            if (shouldPresent) {
                assertEquals(
                    "iteration $iteration: cleared=$suppressionCleared fg=$foreground " +
                        "elapsed=${elapsedSeconds}s -> expected Present",
                    Decision.Present,
                    decision
                )
            } else {
                assertNotEquals(
                    "iteration $iteration: cleared=$suppressionCleared fg=$foreground " +
                        "elapsed=${elapsedSeconds}s -> Present must NOT be returned",
                    Decision.Present,
                    decision
                )
            }
        }
    }

    /**
     * Property (Req 5.4): when suppression clears within the window and the app stays foreground,
     * the deferred intercept is presented and the deferral is cleared.
     */
    @Test
    fun `property - suppression clears in time with foreground app presents and clears deferral`() {
        val random = Random(seed = 111)

        repeat(200) { iteration ->
            val t0 = Instant.parse("2024-06-15T10:00:00Z")
                .plusSeconds(random.nextLong(0, 3600))
            val (coordinator, signal, clock) = buildDeferred(t0)
            val pkg = "com.present.app.$iteration"

            assertEquals(BreachOutcome.Deferred, coordinator.onBreach(pkg))

            // elapsed strictly inside [0, 300): still within window.
            val elapsedSeconds = random.nextLong(0, window.seconds)
            signal.suppressing = false // cleared
            val now = t0.plusSeconds(elapsedSeconds)
            clock.set(now)

            val decision = coordinator.evaluate(pkg, isAppForeground = true, now = now)

            assertEquals(
                "iteration $iteration: elapsed=${elapsedSeconds}s cleared+foreground must Present",
                Decision.Present,
                decision
            )
            assertTrue(
                "iteration $iteration: deferral must be cleared once presented",
                !coordinator.hasPendingDeferral(pkg)
            )
        }
    }

    /**
     * Property (Req 5.5): if the app has left the foreground when evaluate is called, the result is
     * [Decision.Discard] regardless of suppression state — Present is never returned when the app
     * is not foreground.
     */
    @Test
    fun `property - not foreground always discards and never presents`() {
        val random = Random(seed = 222)

        repeat(200) { iteration ->
            val t0 = Instant.parse("2024-06-15T11:00:00Z")
                .plusSeconds(random.nextLong(0, 3600))
            val (coordinator, signal, clock) = buildDeferred(t0)
            val pkg = "com.background.app.$iteration"

            assertEquals(BreachOutcome.Deferred, coordinator.onBreach(pkg))

            // Randomize suppression state and elapsed (inside or past the window) — none of it
            // should matter once the app is not foreground.
            signal.suppressing = random.nextBoolean()
            val elapsedSeconds = random.nextLong(0, 400)
            val now = t0.plusSeconds(elapsedSeconds)
            clock.set(now)

            val decision = coordinator.evaluate(pkg, isAppForeground = false, now = now)

            assertEquals(
                "iteration $iteration: app not foreground must Discard (elapsed=${elapsedSeconds}s)",
                Decision.Discard,
                decision
            )
            assertNotEquals(
                "iteration $iteration: Present must never be returned when app not foreground",
                Decision.Present,
                decision
            )
            assertTrue(
                "iteration $iteration: discard must clear the deferral",
                !coordinator.hasPendingDeferral(pkg)
            )
        }
    }

    /**
     * Property (Req 5.6): if suppression remains active until `elapsed >= 300s`, evaluate discards
     * the deferred intercept and never presents — the 300s window is the hard upper bound even
     * while still suppressed and foreground.
     */
    @Test
    fun `property - still suppressed past window discards and never presents`() {
        val random = Random(seed = 333)

        repeat(200) { iteration ->
            val t0 = Instant.parse("2024-06-15T12:00:00Z")
                .plusSeconds(random.nextLong(0, 3600))
            val (coordinator, signal, clock) = buildDeferred(t0)
            val pkg = "com.timedout.app.$iteration"

            assertEquals(BreachOutcome.Deferred, coordinator.onBreach(pkg))

            // Remain suppressed the whole time; evaluate at elapsed >= 300s.
            signal.suppressing = true
            val elapsedSeconds = window.seconds + random.nextLong(0, 600) // [300s, 900s)
            val now = t0.plusSeconds(elapsedSeconds)
            clock.set(now)

            val decision = coordinator.evaluate(pkg, isAppForeground = true, now = now)

            assertEquals(
                "iteration $iteration: still suppressed at elapsed=${elapsedSeconds}s must Discard",
                Decision.Discard,
                decision
            )
            assertNotEquals(
                "iteration $iteration: Present must never be returned past the window",
                Decision.Present,
                decision
            )
        }
    }

    /**
     * Property: while still suppressed, foreground, and within the window, the coordinator holds
     * (neither presents nor discards) — confirming Present is withheld until suppression clears.
     */
    @Test
    fun `property - within window still suppressed and foreground holds`() {
        val random = Random(seed = 444)

        repeat(150) { iteration ->
            val t0 = Instant.parse("2024-06-15T13:00:00Z")
                .plusSeconds(random.nextLong(0, 3600))
            val (coordinator, signal, clock) = buildDeferred(t0)
            val pkg = "com.holding.app.$iteration"

            assertEquals(BreachOutcome.Deferred, coordinator.onBreach(pkg))

            signal.suppressing = true // still suppressed
            val elapsedSeconds = random.nextLong(0, window.seconds) // within window
            val now = t0.plusSeconds(elapsedSeconds)
            clock.set(now)

            val decision = coordinator.evaluate(pkg, isAppForeground = true, now = now)

            assertEquals(
                "iteration $iteration: suppressed+foreground+within-window must Hold",
                Decision.Hold,
                decision
            )
            assertTrue(
                "iteration $iteration: Hold keeps the deferral pending",
                coordinator.hasPendingDeferral(pkg)
            )
        }
    }
}
