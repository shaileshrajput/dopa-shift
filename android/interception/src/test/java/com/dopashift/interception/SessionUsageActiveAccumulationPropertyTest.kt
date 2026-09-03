package com.dopashift.interception

import com.dopashift.domain.entity.InterceptionRule
import com.dopashift.domain.entity.LimitType
import com.dopashift.domain.entity.MonitoringState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

// Feature: android-app-limit-repitative, Property 4

/**
 * Property 4: Session_Usage accumulates only while ACTIVE
 *
 * For any sequence of foreground increments for a `Repetitive` Rule, `Session_Usage` equals
 * the sum of the increments applied while the Monitoring_State is `ACTIVE` and is unchanged
 * by any increment applied while `PAUSED_FOR_OVERLAY`.
 *
 * The tracker learns the interval from the Rule returned by
 * [FakeInterceptionRuleRepository.findActiveByPackage]. We back it with a `Repetitive`
 * Rule using a deliberately large interval (120 min = 7200s) relative to the generated
 * increments so the cycle never breaches — this keeps the accumulation-sum invariant clean
 * (a breach would not alter the running total, but keeping usage below the ceiling means the
 * observed [RepetitiveSessionTracker.sessionUsageSeconds] tracks the ACTIVE sum exactly with
 * no confounding transitions).
 *
 * Two facets are asserted:
 *  1. Across a mixed ACTIVE/PAUSED sequence, `Session_Usage` == sum of ACTIVE increments only.
 *  2. Every increment applied while `PAUSED_FOR_OVERLAY` leaves `Session_Usage` unchanged.
 *
 * **Validates: Requirements 2.2, 2.6**
 *
 * Module convention: JUnit4 + kotlin.random.Random + repeat(N>=100) + kotlinx.coroutines.test.runTest.
 */
class SessionUsageActiveAccumulationPropertyTest {

    private val testUserId = testUuid("user-repetitive")

    /** Builds a `Repetitive` domain Rule with the given interval (whole minutes, 1..120). */
    private fun repetitiveRule(
        packageName: String,
        intervalMinutes: Int
    ): InterceptionRule = InterceptionRule(
        id = testUuid(packageName),
        userId = testUserId,
        appPackageName = packageName,
        // dailyLimitMinutes is unused for Repetitive but must satisfy the 1..480 invariant.
        dailyLimitMinutes = 60,
        limitType = LimitType.Repetitive,
        repetitiveIntervalMinutes = intervalMinutes,
        enabled = true,
        pausedForDate = null,
        createdAt = Instant.ofEpochMilli(1_700_000_000_000L)
    )

    private fun newTracker(): Pair<FakeInterceptionRuleRepository, RepetitiveSessionTracker> {
        val repo = FakeInterceptionRuleRepository()
        val tracker = RepetitiveSessionTracker(repo)
        tracker.userId = testUserId
        return repo to tracker
    }

    /**
     * Property (facet 1): Session_Usage equals the sum of only the ACTIVE-phase increments.
     *
     * Each step either stays ACTIVE (increment counts) or is applied while PAUSED (increment
     * ignored). PAUSED phases are entered via [RepetitiveSessionTracker.markPausedForOverlay]
     * and left via [RepetitiveSessionTracker.onOverlayDismissedAndReturned] — which also resets
     * the counter to 0, so the expected model resets its running sum on resume too.
     */
    @Test
    fun `property - session usage equals the sum of ACTIVE-phase increments only`() = runTest {
        val random = Random(seed = 424242)

        repeat(200) { iteration ->
            val (repo, tracker) = newTracker()

            // Large interval (up to 120 min = 7200s) so the cycle never breaches during the run.
            val intervalMinutes = 120
            val intervalSeconds = intervalMinutes.toLong() * 60L
            val packageName = "com.repetitive.app.$iteration"
            repo.addRule(repetitiveRule(packageName, intervalMinutes))

            val stepCount = random.nextInt(1, 40)
            // Expected Session_Usage per the spec model, mirroring the tracker's own reset rule.
            var expectedUsage = 0L
            var paused = false
            // The tracker's `markPausedForOverlay` is a deliberate no-op until a session exists
            // for the package — in production the overlay is only ever presented AFTER an
            // interval breach, which requires an existing ACTIVE session (see
            // RepetitiveSessionTracker.markPausedForOverlay: `sessions[pkg] ?: return`). The
            // model must honor that contract: only enter the paused state once at least one
            // `onForeground` sample has created the session, so the model's `paused` stays in
            // lockstep with the tracker's actual Monitoring_State.
            var hasSession = false

            repeat(stepCount) {
                // Randomly toggle pause state. Entering/leaving pause uses the tracker's
                // dedicated transitions so we exercise the real state machine.
                val wantPaused = random.nextInt(0, 3) == 0 // ~1/3 of steps attempt a toggle
                if (wantPaused && !paused && hasSession) {
                    // Only take effect once a session exists — matches the tracker's no-op guard.
                    tracker.markPausedForOverlay(packageName)
                    paused = true
                } else if (wantPaused && paused) {
                    // Dismiss + return: resets Session_Usage to 0 and resumes ACTIVE.
                    tracker.onOverlayDismissedAndReturned(packageName)
                    paused = false
                    expectedUsage = 0L
                }

                // Keep increments small so the sum stays well under the interval ceiling.
                val increment = random.nextLong(1, 30)
                tracker.onForeground(packageName, increment)
                // A session now exists for this package (ACTIVE or PAUSED), so a future pause
                // toggle will actually take effect in the tracker — as it does in the model.
                hasSession = true

                if (!paused) {
                    expectedUsage += increment
                }

                assertEquals(
                    "Iteration $iteration: Session_Usage must equal the sum of ACTIVE increments only",
                    expectedUsage,
                    tracker.sessionUsageSeconds(packageName)
                )
                // Sanity: usage stays below the interval so no breach confounds the run.
                assertTrue(
                    "Iteration $iteration: usage stayed below interval ceiling",
                    tracker.sessionUsageSeconds(packageName) < intervalSeconds
                )
            }
        }
    }

    /**
     * Property (facet 2): any increment applied while PAUSED_FOR_OVERLAY leaves Session_Usage
     * unchanged (Requirement 2.6).
     *
     * We accumulate some ACTIVE time, pause, then fire a burst of increments while paused and
     * assert the counter never moves and the state stays PAUSED_FOR_OVERLAY.
     */
    @Test
    fun `property - increments while paused never change session usage`() = runTest {
        val random = Random(seed = 998877)

        repeat(150) { iteration ->
            val (repo, tracker) = newTracker()

            val intervalMinutes = 120
            val packageName = "com.repetitive.paused.$iteration"
            repo.addRule(repetitiveRule(packageName, intervalMinutes))

            // Accumulate an initial ACTIVE amount below the interval ceiling. At least one
            // sample is applied so a session actually exists before we pause — the tracker's
            // `markPausedForOverlay` is a deliberate no-op with no tracked cycle (in production
            // the overlay only appears after a breach, which requires an existing session), so
            // pausing before any `onForeground` would never reach PAUSED_FOR_OVERLAY.
            val activeSteps = random.nextInt(1, 10)
            var expectedUsage = 0L
            repeat(activeSteps) {
                val increment = random.nextLong(1, 30)
                tracker.onForeground(packageName, increment)
                expectedUsage += increment
            }
            assertEquals(
                "Iteration $iteration: baseline ACTIVE usage",
                expectedUsage,
                tracker.sessionUsageSeconds(packageName)
            )

            // Enter PAUSED_FOR_OVERLAY.
            tracker.markPausedForOverlay(packageName)
            assertEquals(
                "Iteration $iteration: state is PAUSED_FOR_OVERLAY after pause",
                MonitoringState.PAUSED_FOR_OVERLAY,
                tracker.stateOf(packageName)
            )

            val usageAtPause = tracker.sessionUsageSeconds(packageName)

            // Fire a burst of increments while paused; none may change Session_Usage.
            val pausedSteps = random.nextInt(1, 20)
            repeat(pausedSteps) {
                val increment = random.nextLong(1, 100)
                val result = tracker.onForeground(packageName, increment)
                assertEquals(
                    "Iteration $iteration: paused sample returns Paused",
                    SessionResult.Paused,
                    result
                )
                assertEquals(
                    "Iteration $iteration: Session_Usage frozen while paused",
                    usageAtPause,
                    tracker.sessionUsageSeconds(packageName)
                )
                assertEquals(
                    "Iteration $iteration: state remains PAUSED_FOR_OVERLAY",
                    MonitoringState.PAUSED_FOR_OVERLAY,
                    tracker.stateOf(packageName)
                )
            }
        }
    }
}

// Fakes are in TestFakes.kt (shared across interception test files)
