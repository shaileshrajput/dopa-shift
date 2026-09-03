package com.dopashift.interception

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Wiring tests for the depletion-routing contract that [InterceptionService] relies on when a
 * Monitored_App breaches its daily limit.
 *
 * [InterceptionService] is an Android `Service` that cannot be unit-tested directly, so — like the
 * sibling [InterceptionServiceWiringTest] — these tests exercise the *collaborators' contract* that
 * the service composes: on depletion the service consults [FocusExtensionManager] (active
 * extension) and the [DeferredInterceptCoordinator] (suppression / deferral) **before** presenting
 * the overlay. The routing rule the service depends on is:
 *
 * - A breach while a Focus_Extension is active for the app → [BreachOutcome.Deferred] (the service
 *   must NOT present — Requirement 4.11 active-extension suppression).
 * - A breach while a Suppression_Context is active → [BreachOutcome.Deferred] (the service defers
 *   rather than presents — Requirement 5.x suppression defers).
 * - A breach while neither is active → [BreachOutcome.PresentNow] (the service presents the
 *   Intercept_Screen immediately).
 *
 * These assertions validate the exact branch [InterceptionService] takes before showing the
 * overlay without needing the Android Service runtime.
 *
 * Validates: Requirements 4.4, 4.5, 4.11
 */
class InterceptionDepletionRoutingWiringTest {

    /** Controllable clock mirroring [DeferralWindowBoundPropertyTest.MutableClock]. */
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

    /** Toggleable [SuppressionSignalSource], same pattern as the property tests. */
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

    private val zone = ZoneId.of("Asia/Kolkata")
    private val startInstant = Instant.parse("2024-06-15T09:00:00Z")
    private val testPackage = "com.social.distracting"

    private fun build(
        signalSource: FakeSuppressionSignalSource,
        clock: Clock
    ): Pair<FocusExtensionManager, DeferredInterceptCoordinator> {
        val detector = SuppressionContextDetector(signalSource)
        val focus = FocusExtensionManager(clock, zone)
        val coordinator = DeferredInterceptCoordinator(detector, focus, clock)
        return focus to coordinator
    }

    // --- Requirement 4.11: an active Focus_Extension suppresses re-trigger (route: defer) ---

    @Test
    fun `breach while a Focus_Extension is active defers instead of presenting`() {
        val clock = MutableClock(startInstant, zone)
        val signals = FakeSuppressionSignalSource() // nothing suppressing
        val (focus, coordinator) = build(signals, clock)

        // Grant a Focus_Extension (user engaged) → an extension becomes active for the app.
        val grant = focus.grantIfEngaged(testPackage, engaged = true, config = InterceptionConfig())
        assertTrue("engagement should grant an extension", grant is GrantResult.Granted)
        assertTrue(
            "the granted extension must be active now",
            focus.isExtensionActive(testPackage, clock.instant())
        )

        // The service consults the coordinator on depletion; with an active extension it defers.
        assertEquals(BreachOutcome.Deferred, coordinator.onBreach(testPackage))
        assertTrue(
            "a deferral must be pending so the service does NOT present",
            coordinator.hasPendingDeferral(testPackage)
        )
    }

    @Test
    fun `once the Focus_Extension expires a subsequent breach presents immediately`() {
        val clock = MutableClock(startInstant, zone)
        val signals = FakeSuppressionSignalSource()
        val (focus, coordinator) = build(signals, clock)

        // 1-minute extension; after it expires the app is no longer suppressed.
        val config = InterceptionConfig(focusExtensionMinutes = 1)
        focus.grantIfEngaged(testPackage, engaged = true, config = config)
        assertEquals(BreachOutcome.Deferred, coordinator.onBreach(testPackage))

        // Advance past the 1-minute window; the extension is no longer active.
        clock.advance(Duration.ofMinutes(1))
        assertFalse(focus.isExtensionActive(testPackage, clock.instant()))

        // A fresh breach on an app that is no longer suppressed presents now.
        assertEquals(BreachOutcome.PresentNow, coordinator.onBreach("com.other.app"))
    }

    // --- Requirement 5.x: an active Suppression_Context defers presentation ---

    @Test
    fun `breach during a Suppression_Context defers instead of presenting`() {
        val clock = MutableClock(startInstant, zone)
        val signals = FakeSuppressionSignalSource(callActive = true) // active call
        val (_, coordinator) = build(signals, clock)

        assertEquals(BreachOutcome.Deferred, coordinator.onBreach(testPackage))
        assertTrue(coordinator.hasPendingDeferral(testPackage))
    }

    // --- No suppression and no extension: route straight to presentation ---

    @Test
    fun `breach with neither extension nor suppression presents immediately`() {
        val clock = MutableClock(startInstant, zone)
        val signals = FakeSuppressionSignalSource() // nothing suppressing
        val (_, coordinator) = build(signals, clock)

        assertEquals(BreachOutcome.PresentNow, coordinator.onBreach(testPackage))
        assertFalse(
            "no deferral should be recorded when presenting immediately",
            coordinator.hasPendingDeferral(testPackage)
        )
    }

    @Test
    fun `deferred intercept presents once suppression clears while app stays foreground`() {
        val clock = MutableClock(startInstant, zone)
        val signals = FakeSuppressionSignalSource(callActive = true)
        val (_, coordinator) = build(signals, clock)

        // Breach during the call → deferred.
        assertEquals(BreachOutcome.Deferred, coordinator.onBreach(testPackage))

        // While the call continues (within the window) the coordinator holds.
        clock.advance(Duration.ofSeconds(30))
        assertEquals(
            Decision.Hold,
            coordinator.evaluate(testPackage, isAppForeground = true, now = clock.instant())
        )

        // The call ends; app still foreground and within the window → present.
        signals.callActive = false
        clock.advance(Duration.ofSeconds(10))
        assertEquals(
            Decision.Present,
            coordinator.evaluate(testPackage, isAppForeground = true, now = clock.instant())
        )
    }
}
