package com.dopashift.interception

// Feature: screen-time-interception-engine, Property 3

import com.dopashift.data.local.dao.TelemetryDao
import com.dopashift.data.local.entity.LocalTelemetryEvent
import com.dopashift.data.repository.LocalTelemetryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

/**
 * Property 3: Persistence cadence and failure retention.
 *
 * For any poll timeline spanning at least 60 seconds, at least one persistence flush
 * occurs (Requirement 1.5); and for any accumulated value whose flush fails, the
 * in-memory value is preserved unchanged and a flush is re-attempted on the next cycle,
 * so the eventual persisted total equals the true sum of all increments — no data loss,
 * no double-count (Requirement 1.6).
 *
 * **Validates: Requirements 1.5, 1.6**
 *
 * Property-based test following existing module conventions (JUnit4 +
 * `kotlin.random.Random` + `repeat(N >= 100)` + `kotlinx.coroutines.test.runTest`, as in
 * [AllowanceDepletionPropertyTest] / [MidnightAllowanceResetPropertyTest]); Kotest is not
 * on this module's test classpath, so the established randomized-input style is used. A
 * [MutableClock] provides a controllable/advanceable instant so both the 60-second flush
 * cadence and the retry-on-next-cycle behavior are deterministic.
 */
class PersistenceCadenceRetentionPropertyTest {

    private val testZone: ZoneId = ZoneOffset.UTC
    private val testUserId: UUID = testUuid("user-1")

    /** Wide daily limit so no iteration accidentally depletes and short-circuits the timeline. */
    private val wideLimitMinutes = 480

    /** Cadence bound under test (mirrors AllowanceTracker.flushIntervalSeconds). */
    private val flushIntervalSeconds = 60L

    /**
     * A [Clock] whose instant can be advanced by the test, allowing deterministic control of
     * the tracker's flush-cadence timing and midnight resolution against a fixed [ZoneId].
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
     * A [TelemetryDao] that delegates to an in-memory store but can be flipped to throw on
     * [upsert]. When [failNext] is true the next flush attempt fails (leaving the store
     * unchanged); clearing the flag lets the retry on the following cycle succeed. Also counts
     * successful upserts so the test can assert flush occurrence.
     */
    private class FaultInjectingTelemetryDao : TelemetryDao {
        private val events = mutableListOf<LocalTelemetryEvent>()

        /** When true, the next [upsert] throws to simulate a Local_Store write failure. */
        var failNext: Boolean = false

        /** Number of successful upserts (durable flushes) that have completed. */
        var successfulUpserts: Int = 0
            private set

        override suspend fun upsert(event: LocalTelemetryEvent) {
            if (failNext) {
                throw RuntimeException("injected telemetry write failure")
            }
            events.removeAll { it.id == event.id }
            events.add(event)
            successfulUpserts++
        }

        override suspend fun findByDate(date: String): List<LocalTelemetryEvent> {
            return events.filter { it.date == date }
        }

        override suspend fun getTotalForegroundSeconds(date: String, packageName: String): Long? {
            val total = events
                .filter { it.date == date && it.appPackageName == packageName }
                .sumOf { it.foregroundSeconds }
            return if (total == 0L) null else total
        }

        override suspend fun deleteOlderThan(cutoffDate: String) {
            events.removeAll { it.date < cutoffDate }
        }
    }

    /**
     * Property (Req 1.5 — cadence): across a series of polls, the persisted store receives a
     * flush at the first observation, and at least once per 60s of elapsed time thereafter; the
     * persisted total never overshoots the true accumulated sum, and once drained the
     * persisted+buffered total equals the true sum (no loss, no double-count).
     */
    @Test
    fun `property - flush occurs at least once per 60s and never overshoots the true sum`() = runTest {
        val random = Random(seed = 6041)

        repeat(150) { iteration ->
            // Keep the whole timeline within a single UTC day to avoid a midnight reset.
            val start = Instant.parse("2024-06-15T00:00:00Z")
                .plus(Duration.ofSeconds(random.nextLong(0, 3_600)))
            val clock = MutableClock(start, testZone)
            val ruleRepo = FakeInterceptionRuleRepository()
            val dao = FaultInjectingTelemetryDao()
            val telemetryRepo = LocalTelemetryRepository(dao)
            val tracker = AllowanceTracker(ruleRepo, telemetryRepo, clock)
            tracker.userTimeZone = testZone
            tracker.userId = testUserId
            val today = LocalDate.ofInstant(start, testZone)
            val pkg = "com.cadence.app.$iteration.${random.nextInt(0, 100_000)}"
            // Wide rule so the timeline never depletes.
            ruleRepo.addRule(domainRule(testUserId, pkg, wideLimitMinutes))

            var trueSum = 0L
            var sawFirstPoll = false

            val pollCount = random.nextInt(2, 40)
            repeat(pollCount) {
                val increment = random.nextLong(1, 30)
                tracker.onForegroundDetected(pkg, increment)
                trueSum += increment
                sawFirstPoll = true

                val persisted = telemetryRepo.getAccumulatedSeconds(pkg, today)
                // The persisted store must never record more than the true accumulated sum.
                assertTrue(
                    "Iteration $iteration: persisted=$persisted overshoots trueSum=$trueSum",
                    persisted <= trueSum
                )

                // Advance the clock by a random inter-poll gap.
                clock.advance(Duration.ofSeconds(random.nextLong(1, 45)))
            }

            // The first observation always flushes immediately to establish a baseline (Req 1.5).
            assertTrue("Iteration $iteration: expected a first-observation flush", sawFirstPoll)
            assertTrue(
                "Iteration $iteration: first-observation flush should have persisted at least one event",
                dao.successfulUpserts >= 1
            )

            // Advance beyond the flush window and poll once with zero elapsed to force a final
            // due flush, draining any buffered seconds into the persisted store.
            clock.advance(Duration.ofSeconds(flushIntervalSeconds + 1))
            tracker.onForegroundDetected(pkg, 0L)

            val persistedFinal = telemetryRepo.getAccumulatedSeconds(pkg, today)
            assertEquals(
                "Iteration $iteration: after a due flush the persisted total must equal the true sum",
                trueSum,
                persistedFinal
            )
        }
    }

    /**
     * Property (Req 1.5 — cadence bound): if the clock advances by at least 60s since the last
     * flush, the next poll persists the buffered value — a flush occurs at least once per 60s of
     * elapsed time.
     */
    @Test
    fun `property - a poll after 60s elapsed forces a flush of the buffered value`() = runTest {
        val random = Random(seed = 7052)

        repeat(150) { iteration ->
            val start = Instant.parse("2024-06-15T08:00:00Z")
            val clock = MutableClock(start, testZone)
            val ruleRepo = FakeInterceptionRuleRepository()
            val dao = FaultInjectingTelemetryDao()
            val telemetryRepo = LocalTelemetryRepository(dao)
            val tracker = AllowanceTracker(ruleRepo, telemetryRepo, clock)
            tracker.userTimeZone = testZone
            tracker.userId = testUserId
            val today = LocalDate.ofInstant(start, testZone)
            val pkg = "com.window.app.$iteration"
            ruleRepo.addRule(domainRule(testUserId, pkg, wideLimitMinutes))

            // First poll flushes immediately (baseline).
            val first = random.nextLong(1, 30)
            tracker.onForegroundDetected(pkg, first)
            assertEquals(
                "Iteration $iteration: first-observation flush should persist the first increment",
                first,
                telemetryRepo.getAccumulatedSeconds(pkg, today)
            )

            // A poll strictly within the 60s window should NOT flush the newly buffered seconds.
            clock.advance(Duration.ofSeconds(random.nextLong(1, flushIntervalSeconds)))
            val bufferedWithinWindow = random.nextLong(1, 30)
            tracker.onForegroundDetected(pkg, bufferedWithinWindow)
            assertEquals(
                "Iteration $iteration: persisted must stay at the baseline within the <60s window",
                first,
                telemetryRepo.getAccumulatedSeconds(pkg, today)
            )

            // Cross the 60s boundary since the last flush; the next poll must flush the buffer.
            clock.advance(Duration.ofSeconds(flushIntervalSeconds))
            val extra = random.nextLong(1, 30)
            tracker.onForegroundDetected(pkg, extra)
            assertEquals(
                "Iteration $iteration: crossing the 60s window must flush the full buffered total",
                first + bufferedWithinWindow + extra,
                telemetryRepo.getAccumulatedSeconds(pkg, today)
            )
        }
    }

    /**
     * Property (Req 1.6 — failure retention): when a due flush throws, the persisted store is
     * left unchanged and the buffered value is retained; on the next due cycle (fault cleared)
     * the full retained amount is flushed exactly once — the eventual persisted total equals the
     * sum of all increments (no loss, no double-count).
     */
    @Test
    fun `property - a failed flush retains the buffer and the retry persists it exactly once`() = runTest {
        val random = Random(seed = 8063)

        repeat(150) { iteration ->
            val start = Instant.parse("2024-06-15T10:00:00Z")
            val clock = MutableClock(start, testZone)
            val ruleRepo = FakeInterceptionRuleRepository()
            val dao = FaultInjectingTelemetryDao()
            val telemetryRepo = LocalTelemetryRepository(dao)
            val tracker = AllowanceTracker(ruleRepo, telemetryRepo, clock)
            tracker.userTimeZone = testZone
            tracker.userId = testUserId
            val today = LocalDate.ofInstant(start, testZone)
            val pkg = "com.retry.app.$iteration"
            ruleRepo.addRule(domainRule(testUserId, pkg, wideLimitMinutes))

            var trueSum = 0L

            // Make the very first observation's baseline flush fail. The buffer must be retained
            // and the persisted store left empty (no baseline written).
            dao.failNext = true
            val firstInc = random.nextLong(1, 30)
            tracker.onForegroundDetected(pkg, firstInc)
            trueSum += firstInc
            assertEquals(
                "Iteration $iteration: a failed baseline flush must leave the persisted store empty",
                0L,
                telemetryRepo.getAccumulatedSeconds(pkg, today)
            )

            // Some more polls within the window keep buffering; keep failing every due flush.
            val midPolls = random.nextInt(0, 5)
            repeat(midPolls) {
                clock.advance(Duration.ofSeconds(flushIntervalSeconds + 1))
                dao.failNext = true // this cycle's due flush also fails
                val inc = random.nextLong(1, 30)
                tracker.onForegroundDetected(pkg, inc)
                trueSum += inc
                assertEquals(
                    "Iteration $iteration: while flushes keep failing, the persisted store stays empty",
                    0L,
                    telemetryRepo.getAccumulatedSeconds(pkg, today)
                )
            }

            // Now clear the fault and advance past the window; the next due flush persists the
            // full retained buffer exactly once (no double-count of the retained seconds).
            val upsertsBefore = dao.successfulUpserts
            clock.advance(Duration.ofSeconds(flushIntervalSeconds + 1))
            dao.failNext = false
            val finalInc = random.nextLong(1, 30)
            tracker.onForegroundDetected(pkg, finalInc)
            trueSum += finalInc

            assertEquals(
                "Iteration $iteration: recovery flush must persist the full retained total (no loss)",
                trueSum,
                telemetryRepo.getAccumulatedSeconds(pkg, today)
            )
            assertEquals(
                "Iteration $iteration: recovery must persist with exactly one successful upsert (no double-count)",
                upsertsBefore + 1,
                dao.successfulUpserts
            )
        }
    }
}
