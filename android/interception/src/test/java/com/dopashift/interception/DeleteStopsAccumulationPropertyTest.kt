package com.dopashift.interception

import com.dopashift.data.repository.LocalTelemetryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

/**
 * Property 10: Deleting a Rule stops accumulation
 *
 * *For any* Rule, after it is deleted, subsequent foreground detections for its
 * package are reported as not tracked and add no further accumulated time.
 *
 * **Validates: Requirements 3.7**
 *
 * A deleted Rule is simulated by [FakeInterceptionRuleRepository.removeRule]. Once
 * removed, [AllowanceTracker.onForegroundDetected] looks the package up through the
 * domain [com.dopashift.domain.repository.InterceptionRuleRepository] port, finds
 * nothing, and returns [AllowanceStatus.NOT_TRACKED] on the very next poll (within one
 * sampling interval, Requirement 3.7) — dropping any buffered pending time and firing
 * no further depletion callback, even for polls that would otherwise have exceeded the
 * limit.
 *
 * This is a randomized-input property test (JUnit4 + `kotlin.random.Random` +
 * `repeat(N>=100)` + `kotlinx.coroutines.test.runTest`, matching the established style
 * of [AllowanceDepletionPropertyTest] / [MidnightAllowanceResetPropertyTest]; Kotest is
 * not on this module's test classpath). A [MutableClock] holds the instant within a
 * single calendar day so the tracker's per-day in-memory state (depletion set, buffers)
 * is stable across polls while deletion — not a midnight reset — governs the outcome.
 *
 * Feature: screen-time-interception-engine, Property 10
 */
class DeleteStopsAccumulationPropertyTest {

    /**
     * A controllable clock whose instant can be advanced within a day, used to drive
     * the tracker's flush cadence and keep the tracking date stable across polls.
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

    private val testZone = ZoneId.of("America/New_York")
    private val testUserId = testUuid("user-1")

    /**
     * Property: for a tracked app, after some polls (during which it is tracked —
     * WITHIN_ALLOWANCE or DEPLETED), deleting its Rule causes the very next
     * onForegroundDetected to return NOT_TRACKED, and no further depletion callback
     * fires regardless of subsequent polls — even ones that would have exceeded the
     * limit.
     */
    @Test
    fun `property - deleting a rule stops accumulation and interception`() = runTest {
        val random = Random(seed = 3107)

        repeat(200) { iteration ->
            // Start well before midnight in the configured zone so advancing the clock
            // by a few minutes across polls never crosses a day boundary.
            val start = Instant.parse("2024-06-15T09:00:00Z")
                .plus(Duration.ofSeconds(random.nextLong(0, 3_600)))
            val clock = MutableClock(start, ZoneOffset.UTC)

            val ruleRepository = FakeInterceptionRuleRepository()
            val telemetryRepository = LocalTelemetryRepository(FakeTelemetryDao())
            val tracker = AllowanceTracker(ruleRepository, telemetryRepository, clock)
            tracker.userTimeZone = testZone
            tracker.userId = testUserId

            val allowanceMinutes = random.nextInt(1, 481)
            val allowanceSeconds = allowanceMinutes.toLong() * 60L
            val packageName = "com.delete.app.$iteration"
            val ruleId = testUuid(packageName)

            ruleRepository.addRule(
                domainRule(testUserId, packageName, allowanceMinutes, id = ruleId)
            )

            var depletionCount = 0
            tracker.setOnAllowanceDepletedListener { pkg, _ ->
                if (pkg == packageName) depletionCount++
            }

            // Randomize how many polls happen (and how much time each adds) before the
            // deletion. Some iterations will breach the limit before deletion, some won't.
            val pollsBeforeDelete = random.nextInt(1, 8)
            var lastStatusBeforeDelete: AllowanceStatus? = null
            repeat(pollsBeforeDelete) {
                val increment = random.nextLong(1, allowanceSeconds + 120L)
                lastStatusBeforeDelete = tracker.onForegroundDetected(packageName, increment)
                // Advance a little (stays within the same day) to exercise flush cadence.
                clock.advance(Duration.ofSeconds(random.nextLong(1, 90)))
            }

            // Precondition: before deletion the app was actually tracked.
            assertTrue(
                "Iteration $iteration: app should be tracked (WITHIN_ALLOWANCE or DEPLETED) before deletion, was $lastStatusBeforeDelete",
                lastStatusBeforeDelete == AllowanceStatus.WITHIN_ALLOWANCE ||
                    lastStatusBeforeDelete == AllowanceStatus.DEPLETED
            )

            val depletionCountAtDelete = depletionCount

            // Simulate the delete.
            ruleRepository.removeRule(ruleId)

            // The very next poll must report NOT_TRACKED (within one interval, Req 3.7).
            val statusAfterDelete = tracker.onForegroundDetected(packageName, random.nextLong(1, 60))
            assertEquals(
                "Iteration $iteration: first poll after delete must be NOT_TRACKED",
                AllowanceStatus.NOT_TRACKED,
                statusAfterDelete
            )

            // Further polls — including ones large enough to have exceeded the limit —
            // must keep reporting NOT_TRACKED and never fire a new depletion callback.
            val pollsAfterDelete = random.nextInt(1, 12)
            repeat(pollsAfterDelete) {
                // Deliberately include increments that would exceed the daily limit.
                val increment = allowanceSeconds + random.nextLong(1, 600L)
                val status = tracker.onForegroundDetected(packageName, increment)
                assertEquals(
                    "Iteration $iteration: every poll after delete must remain NOT_TRACKED",
                    AllowanceStatus.NOT_TRACKED,
                    status
                )
                clock.advance(Duration.ofSeconds(random.nextLong(1, 90)))
            }

            assertEquals(
                "Iteration $iteration: no further depletion callback may fire after deletion",
                depletionCountAtDelete,
                depletionCount
            )
        }
    }
}

// Fakes are in TestFakes.kt (shared across interception test files)
