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

// Feature: android-app-limit-repitative, Property 7

/**
 * Property 7: Dismissal resets and resumes
 *
 * For any Rule in [MonitoringState.PAUSED_FOR_OVERLAY], once the overlay is dismissed and the
 * Monitored_App returns to the foreground, the Monitoring_State returns to
 * [MonitoringState.ACTIVE] and Session_Usage equals 0. A subsequent foreground sample then
 * resumes accumulation from 0.
 *
 * The property is exercised end-to-end against [RepetitiveSessionTracker]: an arbitrary
 * generated sequence of foreground increments accumulates some Session_Usage, the cycle is then
 * paused via [RepetitiveSessionTracker.markPausedForOverlay], and dismissal-plus-return is
 * signalled via [RepetitiveSessionTracker.onOverlayDismissedAndReturned]. The test asserts the
 * reset invariant and that accumulation cleanly resumes from 0 afterward — covering both the
 * pre-pause "still accumulating" case and the pre-pause "interval already breached" case.
 *
 * **Validates: Requirements 2.7**
 *
 * Module convention: JUnit4 + kotlin.random.Random + repeat(N>=100) +
 * kotlinx.coroutines.test.runTest (matching the sibling property tests in this module; Kotest
 * is not on the module classpath).
 */
class DismissalResetsAndResumesPropertyTest {

    private val testUserId = testUuid("user-7")
    private val ruleUserId = testUuid("rule-user-7")

    private fun newTracker(): Pair<FakeInterceptionRuleRepository, RepetitiveSessionTracker> {
        val repo = FakeInterceptionRuleRepository()
        val tracker = RepetitiveSessionTracker(repo)
        tracker.userId = testUserId
        return repo to tracker
    }

    /** Builds an enabled `Repetitive` domain rule with the given interval (whole minutes, 1..120). */
    private fun repetitiveRule(packageName: String, intervalMinutes: Int): InterceptionRule =
        InterceptionRule(
            id = testUuid(packageName),
            userId = testUserId,
            appPackageName = packageName,
            dailyLimitMinutes = 60,
            limitType = LimitType.Repetitive,
            repetitiveIntervalMinutes = intervalMinutes,
            enabled = true,
            pausedForDate = null,
            createdAt = Instant.ofEpochMilli(1_700_000_000_000L)
        )

    /**
     * Property: after accumulating arbitrary usage, pausing for the overlay, then dismissing +
     * returning, the state is ACTIVE and Session_Usage is 0; a following below-interval sample
     * resumes accumulation from exactly that sample's elapsed value and reports Accumulating.
     */
    @Test
    fun `property - dismissal resets Session_Usage to 0, returns to ACTIVE, and resumes from 0`() = runTest {
        val random = Random(seed = 7_2024)

        repeat(200) { iteration ->
            val (repo, tracker) = newTracker()

            // Interval in 1..120 minutes; keep >= 2 min so a single small resume sample stays
            // comfortably below the interval.
            val intervalMinutes = random.nextInt(2, 121)
            val intervalSeconds = intervalMinutes.toLong() * 60L
            val packageName = "com.repetitive.dismiss.$iteration"
            repo.addRule(repetitiveRule(packageName, intervalMinutes))

            // Accumulate an arbitrary amount of Session_Usage while ACTIVE (may or may not breach).
            val incrementCount = random.nextInt(1, 20)
            repeat(incrementCount) {
                val elapsed = random.nextLong(1, intervalSeconds + 30L)
                tracker.onForeground(packageName, elapsed)
            }
            // Sanity: some usage was accumulated (a breach may already have happened, which is fine).
            assertTrue(
                "Iteration $iteration: expected some accumulation before pause",
                tracker.sessionUsageSeconds(packageName) > 0L
            )

            // Overlay is presented -> PAUSED_FOR_OVERLAY.
            tracker.markPausedForOverlay(packageName)
            assertEquals(
                "Iteration $iteration: state should be PAUSED_FOR_OVERLAY after markPausedForOverlay",
                MonitoringState.PAUSED_FOR_OVERLAY,
                tracker.stateOf(packageName)
            )

            // Dismissal + app returns to foreground (Requirement 2.7).
            tracker.onOverlayDismissedAndReturned(packageName)

            // Invariant: state back to ACTIVE and Session_Usage reset to 0.
            assertEquals(
                "Iteration $iteration: state must return to ACTIVE after dismissal + return",
                MonitoringState.ACTIVE,
                tracker.stateOf(packageName)
            )
            assertEquals(
                "Iteration $iteration: Session_Usage must be 0 after dismissal + return",
                0L,
                tracker.sessionUsageSeconds(packageName)
            )

            // Resume: a below-interval sample accumulates from 0 and reports Accumulating.
            val resumeElapsed = random.nextLong(1, 60L) // < interval (interval >= 120s)
            val resumeResult = tracker.onForeground(packageName, resumeElapsed)
            assertEquals(
                "Iteration $iteration: resume sample must accumulate from 0",
                resumeElapsed,
                tracker.sessionUsageSeconds(packageName)
            )
            assertEquals(
                "Iteration $iteration: below-interval resume must report Accumulating",
                SessionResult.Accumulating,
                resumeResult
            )
            assertEquals(
                "Iteration $iteration: state stays ACTIVE while accumulating",
                MonitoringState.ACTIVE,
                tracker.stateOf(packageName)
            )
        }
    }

    /**
     * Property (breached-before-pause case): even when Session_Usage reached or exceeded the
     * interval before the overlay was shown, dismissal + return still resets Session_Usage to 0,
     * returns to ACTIVE, and accumulation resumes cleanly toward a fresh interval.
     */
    @Test
    fun `property - breach before pause still resets to 0 and resumes cleanly on dismissal`() = runTest {
        val random = Random(seed = 99_2024)

        repeat(200) { iteration ->
            val (repo, tracker) = newTracker()

            val intervalMinutes = random.nextInt(1, 121)
            val intervalSeconds = intervalMinutes.toLong() * 60L
            val packageName = "com.repetitive.breach.$iteration"
            repo.addRule(repetitiveRule(packageName, intervalMinutes))

            // Drive usage past the interval so a breach is guaranteed before pausing.
            var accumulated = 0L
            while (accumulated < intervalSeconds) {
                val elapsed = random.nextLong(1, intervalSeconds + 1L)
                val result = tracker.onForeground(packageName, elapsed)
                accumulated += elapsed
                if (accumulated >= intervalSeconds) {
                    assertEquals(
                        "Iteration $iteration: reaching the interval must report IntervalBreached",
                        SessionResult.IntervalBreached,
                        result
                    )
                }
            }
            assertTrue(
                "Iteration $iteration: usage should be at/above interval before pause",
                tracker.sessionUsageSeconds(packageName) >= intervalSeconds
            )

            // Present overlay, then dismiss + return.
            tracker.markPausedForOverlay(packageName)
            tracker.onOverlayDismissedAndReturned(packageName)

            // Reset invariant holds regardless of pre-pause breach.
            assertEquals(
                "Iteration $iteration: state must be ACTIVE after dismissal + return",
                MonitoringState.ACTIVE,
                tracker.stateOf(packageName)
            )
            assertEquals(
                "Iteration $iteration: Session_Usage must be 0 after dismissal + return",
                0L,
                tracker.sessionUsageSeconds(packageName)
            )

            // Accumulation resumes toward a fresh interval from 0.
            val resumeElapsed = random.nextLong(1, intervalSeconds)
            tracker.onForeground(packageName, resumeElapsed)
            assertEquals(
                "Iteration $iteration: post-reset accumulation must start from the resume sample",
                resumeElapsed,
                tracker.sessionUsageSeconds(packageName)
            )
        }
    }
}

// Fakes are in TestFakes.kt (shared across interception test files)
