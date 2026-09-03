package com.dopashift.interception

import com.dopashift.data.repository.LocalTelemetryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

// Feature: screen-time-interception-engine, Property 1: Accumulation equals the sum of increments (monotonic)

/**
 * Property 1: Accumulation equals the sum of increments (monotonic)
 *
 * For any tracked app and any sequence of positive foreground poll increments within a
 * single calendar day, the accumulated foreground seconds the tracker accounts for equals
 * the exact sum of those increments and is non-decreasing across the sequence — no loss,
 * no double-count. This holds regardless of the 60-second flush cadence: some increments
 * are flushed to the Local_Store while others remain buffered in memory, and the tracker's
 * total (persisted + pending) must always equal the running sum.
 *
 * The property is observed through [AllowanceTracker.getRemainingSeconds], which returns
 * `allowance - (persisted + pending)`; with a deliberately large daily limit the app never
 * depletes, so `remaining == allowance - runningSum` throughout. A final clock advance forces
 * a flush so the invariant is also verified once everything is persisted.
 *
 * **Validates: Requirements 1.4**
 *
 * Module convention: JUnit4 + kotlin.random.Random + repeat(N>=100) + kotlinx.coroutines.test.runTest.
 */
class AccumulationSumPropertyTest {

    private val testZone = ZoneId.of("America/New_York")
    private val testUserId = testUuid("user-1")

    /**
     * A minimal advanceable clock so the tracker's >=60s flush cadence can be exercised:
     * advancing past 60s forces buffered increments to persist, while shorter gaps leave
     * them pending. The tracker's accounted total must equal the sum of increments in
     * either case.
     */
    private class MutableClock(
        private var now: Instant,
        private val zone: ZoneId = ZoneOffset.UTC
    ) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(z: ZoneId): Clock = MutableClock(now, z)
        override fun instant(): Instant = now
        fun advanceSeconds(seconds: Long) {
            now = now.plusSeconds(seconds)
        }
    }

    private fun newTracker(clock: Clock): Pair<FakeInterceptionRuleRepository, AllowanceTracker> {
        val repo = FakeInterceptionRuleRepository()
        val telemetryRepo = LocalTelemetryRepository(FakeTelemetryDao())
        val tracker = AllowanceTracker(repo, telemetryRepo, clock)
        tracker.userTimeZone = testZone
        tracker.userId = testUserId
        return repo to tracker
    }

    /**
     * Property: total accounted time == exact sum of all increments, verified after every
     * increment (monotonic, no loss, no double-count) via remaining = allowance - sum.
     */
    @Test
    fun `property - accumulated total equals the running sum of increments across the sequence`() = runTest {
        val random = Random(seed = 2024)

        repeat(200) { iteration ->
            val clock = MutableClock(Instant.parse("2024-06-15T09:00:00Z"))
            val (repo, tracker) = newTracker(clock)

            // Large daily limit so the app never depletes within the sequence.
            // 480 min = 28800 s, the max allowed limit.
            val allowanceMinutes = 480
            val allowanceSeconds = allowanceMinutes.toLong() * 60L
            val packageName = "com.accumulate.app.$iteration"
            repo.addRule(domainRule(testUserId, packageName, allowanceMinutes))

            val incrementCount = random.nextInt(1, 40)
            var runningSum = 0L
            var previousAccounted = 0L

            repeat(incrementCount) {
                // Positive increment; keep the sequence well under the allowance.
                val increment = random.nextLong(1, 50)
                // Occasionally advance the clock past the 60s flush cadence so some
                // increments are persisted and others remain buffered in memory.
                if (random.nextBoolean()) {
                    clock.advanceSeconds(random.nextLong(0, 120))
                }

                tracker.onForegroundDetected(packageName, increment)
                runningSum += increment

                // remaining = allowance - accountedTotal  =>  accountedTotal = allowance - remaining
                val remaining = tracker.getRemainingSeconds(packageName)!!
                val accountedTotal = allowanceSeconds - remaining

                assertEquals(
                    "Iteration $iteration: accounted total must equal running sum",
                    runningSum,
                    accountedTotal
                )
                // Monotonic non-decreasing across the sequence.
                org.junit.Assert.assertTrue(
                    "Iteration $iteration: accounted total must be non-decreasing",
                    accountedTotal >= previousAccounted
                )
                previousAccounted = accountedTotal
            }

            // Force a final flush by advancing well past the flush cadence, then observe
            // one more time. The persisted+pending total must still equal the exact sum.
            clock.advanceSeconds(120)
            tracker.onForegroundDetected(packageName, 0L)
            val remainingAfterFlush = tracker.getRemainingSeconds(packageName)!!
            assertEquals(
                "Iteration $iteration: after forced flush, accounted total equals sum",
                runningSum,
                allowanceSeconds - remainingAfterFlush
            )
        }
    }

    /**
     * Property: the same total holds when increments are read directly from the combined
     * persisted + pending store — i.e., no double-count occurs when a flush lands mid-sequence.
     * We advance the clock every increment to force a flush each time (everything persisted),
     * and confirm the accounted total tracks the sum exactly.
     */
    @Test
    fun `property - forcing a flush on every increment never loses or double-counts`() = runTest {
        val random = Random(seed = 7)

        repeat(150) { iteration ->
            val clock = MutableClock(Instant.parse("2024-06-15T09:00:00Z"))
            val (repo, tracker) = newTracker(clock)

            val allowanceMinutes = 480
            val allowanceSeconds = allowanceMinutes.toLong() * 60L
            val packageName = "com.flushed.app.$iteration"
            repo.addRule(domainRule(testUserId, packageName, allowanceMinutes))

            val incrementCount = random.nextInt(1, 30)
            var runningSum = 0L

            repeat(incrementCount) { step ->
                val increment = random.nextLong(1, 60)
                // Always advance past the 60s cadence so each increment flushes to the store.
                if (step > 0) clock.advanceSeconds(61)
                tracker.onForegroundDetected(packageName, increment)
                runningSum += increment
            }

            // Final flush to persist the last buffered increment.
            clock.advanceSeconds(61)
            tracker.onForegroundDetected(packageName, 0L)

            val remaining = tracker.getRemainingSeconds(packageName)!!
            assertEquals(
                "Iteration $iteration: accounted total equals sum with per-increment flushing",
                runningSum,
                allowanceSeconds - remaining
            )
        }
    }
}

// Fakes are in TestFakes.kt (shared across interception test files)
