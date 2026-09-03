package com.dopashift.interception

import com.dopashift.domain.entity.InterceptionRule
import com.dopashift.domain.entity.LimitType
import com.dopashift.domain.entity.MonitoringState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Wiring tests for the repetitive-breach routing contract that [InterceptionService] composes for a
 * `Repetitive` Rule (task 6.1). [InterceptionService] is an Android `Service` that cannot be
 * unit-tested directly, so — like the sibling [InterceptionServiceWiringTest] and
 * [InterceptionDepletionRoutingWiringTest] — these tests exercise the *collaborators' contract* the
 * service wires together, using the real [RepetitiveSessionTracker] and the real
 * [DeferredInterceptCoordinator] (over a controllable [Clock] and toggleable suppression signals).
 *
 * The routing rule the service depends on for a `Repetitive` breach ([InterceptionService.onRepetitiveIntervalBreached]
 * / [InterceptionService.evaluatePendingDeferrals]) is:
 *
 * 1. A foreground sample of a `Repetitive`-governed app whose `Session_Usage` reaches the interval
 *    yields [SessionResult.IntervalBreached] from [RepetitiveSessionTracker.onForeground].
 * 2. On that breach the service calls [DeferredInterceptCoordinator.onBreach] **first** (the
 *    Suppression_Context / Focus_Extension deferral check) **before** presenting the overlay.
 * 3. Only on [BreachOutcome.PresentNow] does the service present the overlay and then call
 *    [RepetitiveSessionTracker.markPausedForOverlay], transitioning the Rule to
 *    [MonitoringState.PAUSED_FOR_OVERLAY] (Requirement 2.5) which freezes accumulation.
 * 4. On [BreachOutcome.Deferred] the service does NOT present or pause yet; the deferral is
 *    re-evaluated each cycle and, once suppression clears with the app still foreground, [evaluate]
 *    returns [Decision.Present] — at which point the service presents and pauses.
 *
 * These assertions validate the exact branch [InterceptionService] takes for a repetitive breach
 * without needing the Android Service runtime.
 *
 * Validates: Requirements 2.4, 2.5
 */
class RepetitiveInterceptionRoutingWiringTest {

    /** Controllable clock mirroring [InterceptionDepletionRoutingWiringTest.MutableClock]. */
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

    /** Toggleable [SuppressionSignalSource], same pattern as the sibling wiring tests. */
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
    private val testUserId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private lateinit var fakeRuleRepository: FakeInterceptionRuleRepository
    private lateinit var tracker: RepetitiveSessionTracker

    @Before
    fun setup() {
        fakeRuleRepository = FakeInterceptionRuleRepository()
        tracker = RepetitiveSessionTracker(fakeRuleRepository)
        tracker.userId = testUserId
    }

    private fun repetitiveRule(intervalMinutes: Int): InterceptionRule = InterceptionRule(
        id = testUuid(testPackage),
        userId = testUserId,
        appPackageName = testPackage,
        dailyLimitMinutes = 60,
        limitType = LimitType.Repetitive,
        repetitiveIntervalMinutes = intervalMinutes,
        enabled = true,
        pausedForDate = null,
        createdAt = Instant.ofEpochMilli(1_700_000_000_000L)
    )

    private fun build(
        signalSource: FakeSuppressionSignalSource,
        clock: Clock
    ): DeferredInterceptCoordinator {
        val detector = SuppressionContextDetector(signalSource)
        val focus = FocusExtensionManager(clock, zone)
        return DeferredInterceptCoordinator(detector, focus, clock)
    }

    /**
     * Drives the tracker until it reports an interval breach, mirroring the per-poll sampling the
     * service performs in [InterceptionService.handleForegroundApp].
     */
    private suspend fun accumulateUntilBreach(elapsedSecondsPerPoll: Long, maxPolls: Int = 1000) {
        var result: SessionResult = SessionResult.Accumulating
        var polls = 0
        while (result != SessionResult.IntervalBreached && polls < maxPolls) {
            result = tracker.onForeground(testPackage, elapsedSecondsPerPoll)
            polls++
        }
        assertEquals(
            "tracker should breach the interval after sustained ACTIVE accumulation",
            SessionResult.IntervalBreached,
            result
        )
    }

    // --- Requirement 2.4 / 2.5: breach routes through deferral, then presents + pauses ---

    @Test
    fun `repetitive breach with no suppression presents now then transitions to PAUSED_FOR_OVERLAY`() =
        runTest {
            val clock = MutableClock(startInstant, zone)
            val signals = FakeSuppressionSignalSource() // nothing suppressing
            val coordinator = build(signals, clock)
            fakeRuleRepository.addRule(repetitiveRule(intervalMinutes = 1)) // 60s interval

            // 1) The tracker accumulates while ACTIVE and eventually reports the breach.
            accumulateUntilBreach(elapsedSecondsPerPoll = 5L)
            assertEquals(MonitoringState.ACTIVE, tracker.stateOf(testPackage))

            // 2) The service consults the coordinator FIRST (deferral check) before presenting.
            val outcome = coordinator.onBreach(testPackage)
            assertEquals(BreachOutcome.PresentNow, outcome)
            assertFalse(
                "no deferral is recorded when presenting immediately",
                coordinator.hasPendingDeferral(testPackage)
            )

            // 3) Only on PresentNow does the service present and pause the cycle.
            tracker.markPausedForOverlay(testPackage)
            assertEquals(
                MonitoringState.PAUSED_FOR_OVERLAY,
                tracker.stateOf(testPackage)
            )
        }

    @Test
    fun `deferral is checked before pausing - a suppressed repetitive breach does not pause yet`() =
        runTest {
            val clock = MutableClock(startInstant, zone)
            val signals = FakeSuppressionSignalSource(callActive = true) // active call suppresses
            val coordinator = build(signals, clock)
            fakeRuleRepository.addRule(repetitiveRule(intervalMinutes = 1))

            accumulateUntilBreach(elapsedSecondsPerPoll = 5L)

            // The service routes the breach through the coordinator first — it defers.
            val outcome = coordinator.onBreach(testPackage)
            assertEquals(BreachOutcome.Deferred, outcome)
            assertTrue(
                "a deferral must be pending so the service does NOT present",
                coordinator.hasPendingDeferral(testPackage)
            )

            // Because it deferred, the service must NOT pause the cycle — the Rule stays ACTIVE so
            // that dismissal/reset semantics remain correct until the overlay is actually shown.
            assertEquals(MonitoringState.ACTIVE, tracker.stateOf(testPackage))
        }

    @Test
    fun `deferred repetitive breach presents and pauses once suppression clears while foreground`() =
        runTest {
            val clock = MutableClock(startInstant, zone)
            val signals = FakeSuppressionSignalSource(callActive = true)
            val coordinator = build(signals, clock)
            fakeRuleRepository.addRule(repetitiveRule(intervalMinutes = 1))

            accumulateUntilBreach(elapsedSecondsPerPoll = 5L)

            // Breach during the call → deferred, not paused.
            assertEquals(BreachOutcome.Deferred, coordinator.onBreach(testPackage))
            assertEquals(MonitoringState.ACTIVE, tracker.stateOf(testPackage))

            // While the call continues and the app stays foreground, the coordinator holds.
            clock.advance(Duration.ofSeconds(30))
            assertEquals(
                Decision.Hold,
                coordinator.evaluate(testPackage, isAppForeground = true, now = clock.instant())
            )
            assertEquals(MonitoringState.ACTIVE, tracker.stateOf(testPackage))

            // The call ends; app still foreground and within the 300s window → present.
            signals.callActive = false
            clock.advance(Duration.ofSeconds(10))
            assertEquals(
                Decision.Present,
                coordinator.evaluate(testPackage, isAppForeground = true, now = clock.instant())
            )

            // On Present the service presents the overlay and freezes the cycle (Requirement 2.5).
            tracker.markPausedForOverlay(testPackage)
            assertEquals(MonitoringState.PAUSED_FOR_OVERLAY, tracker.stateOf(testPackage))
        }

    @Test
    fun `deferred repetitive breach is discarded and the cycle is reset when app leaves foreground`() =
        runTest {
            val clock = MutableClock(startInstant, zone)
            val signals = FakeSuppressionSignalSource(callActive = true)
            val coordinator = build(signals, clock)
            fakeRuleRepository.addRule(repetitiveRule(intervalMinutes = 1))

            accumulateUntilBreach(elapsedSecondsPerPoll = 5L)
            assertTrue(tracker.sessionUsageSeconds(testPackage) >= 60L)

            // Deferred while suppressed.
            assertEquals(BreachOutcome.Deferred, coordinator.onBreach(testPackage))

            // The app leaves the foreground → the coordinator discards the deferral (Req 5.5)…
            assertEquals(
                Decision.Discard,
                coordinator.evaluate(testPackage, isAppForeground = false, now = clock.instant())
            )

            // …and the service resets the repetitive cycle so a stale count cannot re-trigger (OQ-2).
            tracker.reset(testPackage)
            assertEquals(MonitoringState.ACTIVE, tracker.stateOf(testPackage))
            assertEquals(0L, tracker.sessionUsageSeconds(testPackage))
        }

    // --- Requirement 2.5: while PAUSED_FOR_OVERLAY no further time accumulates ---

    @Test
    fun `while PAUSED_FOR_OVERLAY subsequent samples add no time and report Paused`() = runTest {
        val clock = MutableClock(startInstant, zone)
        val signals = FakeSuppressionSignalSource()
        val coordinator = build(signals, clock)
        fakeRuleRepository.addRule(repetitiveRule(intervalMinutes = 1))

        accumulateUntilBreach(elapsedSecondsPerPoll = 5L)
        assertEquals(BreachOutcome.PresentNow, coordinator.onBreach(testPackage))
        tracker.markPausedForOverlay(testPackage)

        val usageAtPause = tracker.sessionUsageSeconds(testPackage)

        // Further foreground samples must be frozen while the overlay is presented.
        repeat(5) {
            val result = tracker.onForeground(testPackage, 5L)
            assertEquals(SessionResult.Paused, result)
        }
        assertEquals(usageAtPause, tracker.sessionUsageSeconds(testPackage))
        assertEquals(MonitoringState.PAUSED_FOR_OVERLAY, tracker.stateOf(testPackage))
    }
}
