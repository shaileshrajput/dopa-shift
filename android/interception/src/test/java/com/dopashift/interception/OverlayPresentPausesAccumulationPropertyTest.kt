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

// Feature: android-app-limit-repitative, Property 6

/**
 * Property 6: Overlay present pauses accumulation
 *
 * *For any* interval breach that results in the overlay being presented, the Rule's
 * Monitoring_State becomes `PAUSED_FOR_OVERLAY` and no elapsed time is added to
 * `Session_Usage` (or any daily counter) until dismissal.
 *
 * **Validates: Requirements 2.5, 2.6**
 *
 * The subject is [RepetitiveSessionTracker]. Each iteration:
 *  1. registers a `Repetitive` Rule with a small interval (1 minute = 60s) so the breach
 *     is reached quickly,
 *  2. drives [RepetitiveSessionTracker.onForeground] until it returns
 *     [SessionResult.IntervalBreached] (the trigger for presenting the overlay),
 *  3. calls [RepetitiveSessionTracker.markPausedForOverlay] to model the overlay being
 *     shown, asserting the state becomes [MonitoringState.PAUSED_FOR_OVERLAY], then
 *  4. feeds an arbitrary generated sequence of further `onForeground` samples while paused
 *     and asserts each returns [SessionResult.Paused] and `Session_Usage` never moves off
 *     the value captured at pause time.
 *
 * This is a randomized-input property test (JUnit4 + `kotlin.random.Random` +
 * `repeat(N>=100)` + `kotlinx.coroutines.test.runTest`) matching the established module
 * style of [AccumulationSumPropertyTest] / [DeleteStopsAccumulationPropertyTest]; Kotest is
 * not on this module's test classpath.
 */
class OverlayPresentPausesAccumulationPropertyTest {

    private val testUserId = testUuid("user-1")

    /** Builds a `Repetitive` domain rule with the given whole-minute interval. */
    private fun repetitiveRule(
        packageName: String,
        intervalMinutes: Int
    ): InterceptionRule = InterceptionRule(
        id = testUuid(packageName),
        userId = testUserId,
        appPackageName = packageName,
        dailyLimitMinutes = 60, // unused by the repetitive path but required (1..480)
        limitType = LimitType.Repetitive,
        repetitiveIntervalMinutes = intervalMinutes,
        enabled = true,
        pausedForDate = null,
        createdAt = Instant.ofEpochMilli(1_700_000_000_000L)
    )

    @Test
    fun `property - overlay present pauses accumulation and freezes Session_Usage`() = runTest {
        val random = Random(seed = 6060)

        repeat(200) { iteration ->
            val packageName = "com.pause.app.$iteration"
            // Small interval (1 minute = 60s) so the breach is reached quickly.
            val repo = FakeInterceptionRuleRepository()
            repo.addRule(repetitiveRule(packageName, intervalMinutes = 1))
            val activeTracker = RepetitiveSessionTracker(repo).also { it.userId = testUserId }

            val intervalSeconds = 60L

            // 1. Drive the tracker until it reports IntervalBreached. Small positive
            //    increments so it takes several samples to reach the interval.
            var breached = false
            var guard = 0
            while (!breached && guard < 1_000) {
                guard++
                val increment = random.nextLong(1, 20)
                val result = activeTracker.onForeground(packageName, increment)
                if (result == SessionResult.IntervalBreached) {
                    breached = true
                }
            }
            assertTrue(
                "Iteration $iteration: expected to reach IntervalBreached",
                breached
            )
            assertTrue(
                "Iteration $iteration: usage at breach should be >= interval",
                activeTracker.sessionUsageSeconds(packageName) >= intervalSeconds
            )

            // 2. Overlay is presented -> pause. State must be PAUSED_FOR_OVERLAY.
            activeTracker.markPausedForOverlay(packageName)
            assertEquals(
                "Iteration $iteration: state after present must be PAUSED_FOR_OVERLAY",
                MonitoringState.PAUSED_FOR_OVERLAY,
                activeTracker.stateOf(packageName)
            )

            val usageAtPause = activeTracker.sessionUsageSeconds(packageName)

            // 3. Arbitrary further foreground samples while the overlay is up: none may
            //    accumulate, each must report Paused, and the state must stay paused.
            val pausedSamples = random.nextInt(1, 30)
            repeat(pausedSamples) {
                val increment = random.nextLong(0, 120)
                val result = activeTracker.onForeground(packageName, increment)
                assertEquals(
                    "Iteration $iteration: sample while paused must return Paused",
                    SessionResult.Paused,
                    result
                )
                assertEquals(
                    "Iteration $iteration: Session_Usage must not change while paused",
                    usageAtPause,
                    activeTracker.sessionUsageSeconds(packageName)
                )
                assertEquals(
                    "Iteration $iteration: state must remain PAUSED_FOR_OVERLAY",
                    MonitoringState.PAUSED_FOR_OVERLAY,
                    activeTracker.stateOf(packageName)
                )
            }
        }
    }
}

// Fakes are in TestFakes.kt (shared across interception test files)
