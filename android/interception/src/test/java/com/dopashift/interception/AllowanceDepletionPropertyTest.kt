package com.dopashift.interception

import com.dopashift.data.repository.LocalTelemetryRepository
import com.dopashift.domain.entity.InterceptionRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

/**
 * Property 6: Daily Allowance Depletion Triggers Overlay
 *
 * For any interception rule with dailyAllowanceMinutes = N,
 * when accumulated foreground seconds for a tracked app reach or exceed N*60,
 * the system SHALL report [AllowanceStatus.DEPLETED] and fire the
 * [AllowanceTracker.OnAllowanceDepleted] callback exactly once per day.
 *
 * **Validates: Requirements 2.4**
 *
 * This is a property-based test using randomized inputs over many iterations.
 */
class AllowanceDepletionPropertyTest {

    private lateinit var fakeRuleRepository: FakeInterceptionRuleRepository
    private lateinit var fakeTelemetryDao: FakeTelemetryDao
    private lateinit var telemetryRepository: LocalTelemetryRepository
    private lateinit var tracker: AllowanceTracker
    private lateinit var fixedClock: Clock

    private val testZone = ZoneId.of("America/New_York")
    private val testUserId = testUuid("user-1")

    @Before
    fun setup() {
        fixedClock = Clock.fixed(
            Instant.parse("2024-06-15T14:00:00Z"),
            ZoneOffset.UTC
        )
        fakeRuleRepository = FakeInterceptionRuleRepository()
        fakeTelemetryDao = FakeTelemetryDao()
        telemetryRepository = LocalTelemetryRepository(fakeTelemetryDao)
        tracker = AllowanceTracker(fakeRuleRepository, telemetryRepository, fixedClock)
        tracker.userTimeZone = testZone
        tracker.userId = testUserId
    }

    private fun newTracker(): Pair<FakeInterceptionRuleRepository, AllowanceTracker> {
        val repo = FakeInterceptionRuleRepository()
        val telemetryRepo = LocalTelemetryRepository(FakeTelemetryDao())
        val t = AllowanceTracker(repo, telemetryRepo, fixedClock)
        t.userTimeZone = testZone
        t.userId = testUserId
        return repo to t
    }

    /**
     * Property: For any allowance in [1, 480] minutes, once accumulated seconds >= allowance*60,
     * the tracker MUST report DEPLETED.
     */
    @Test
    fun `property - any accumulated time meeting or exceeding allowance triggers DEPLETED`() = runTest {
        val random = Random(seed = 42)

        repeat(100) { iteration ->
            // Reset state for each iteration
            val (localRepo, localTracker) = newTracker()

            // Random allowance between 1 and 480 minutes
            val allowanceMinutes = random.nextInt(1, 481)
            val allowanceSeconds = allowanceMinutes.toLong() * 60L
            val packageName = "com.test.app.$iteration"

            localRepo.addRule(domainRule(testUserId, packageName, allowanceMinutes))

            // Accumulate exactly at the threshold (sum of random chunks)
            var accumulated = 0L
            while (accumulated < allowanceSeconds) {
                val chunk = random.nextLong(1, minOf(allowanceSeconds - accumulated + 1, 300L))
                val status = localTracker.onForegroundDetected(packageName, chunk)
                accumulated += chunk

                if (accumulated >= allowanceSeconds) {
                    assertEquals(
                        "Iteration $iteration: Expected DEPLETED when accumulated=$accumulated >= allowance=$allowanceSeconds",
                        AllowanceStatus.DEPLETED,
                        status
                    )
                } else {
                    assertEquals(
                        "Iteration $iteration: Expected WITHIN_ALLOWANCE when accumulated=$accumulated < allowance=$allowanceSeconds",
                        AllowanceStatus.WITHIN_ALLOWANCE,
                        status
                    )
                }
            }
        }
    }

    /**
     * Property: The depletion callback fires exactly once per app per day,
     * regardless of how many additional polls occur after depletion.
     */
    @Test
    fun `property - depletion callback fires exactly once regardless of additional polls`() = runTest {
        val random = Random(seed = 123)

        repeat(50) { iteration ->
            val (localRepo, localTracker) = newTracker()

            val allowanceMinutes = random.nextInt(1, 120)
            val packageName = "com.callback.app.$iteration"

            localRepo.addRule(domainRule(testUserId, packageName, allowanceMinutes))

            var callbackCount = 0
            localTracker.setOnAllowanceDepletedListener { _, _ -> callbackCount++ }

            // Deplete immediately
            localTracker.onForegroundDetected(packageName, allowanceMinutes.toLong() * 60L)
            assertEquals("Iteration $iteration: Callback should fire once on depletion", 1, callbackCount)

            // Continue polling N more times
            val additionalPolls = random.nextInt(1, 20)
            repeat(additionalPolls) {
                localTracker.onForegroundDetected(packageName, random.nextLong(1, 30))
            }

            assertEquals(
                "Iteration $iteration: Callback must NOT fire again after $additionalPolls additional polls",
                1,
                callbackCount
            )
        }
    }

    /**
     * Property: For any allowance amount, the status is WITHIN_ALLOWANCE when
     * accumulated < allowance*60 and DEPLETED when accumulated >= allowance*60.
     * This is the core invariant — no false positives or false negatives.
     */
    @Test
    fun `property - depletion threshold is exact with no false positives`() = runTest {
        val random = Random(seed = 77)

        repeat(100) { iteration ->
            val (localRepo, localTracker) = newTracker()

            val allowanceMinutes = random.nextInt(1, 480)
            val allowanceSeconds = allowanceMinutes.toLong() * 60L
            val packageName = "com.precise.app.$iteration"

            localRepo.addRule(domainRule(testUserId, packageName, allowanceMinutes))

            // Accumulate exactly 1 second less than the threshold
            val almostThere = allowanceSeconds - 1
            if (almostThere > 0) {
                val status = localTracker.onForegroundDetected(packageName, almostThere)
                assertEquals(
                    "Iteration $iteration: accumulated=$almostThere should be WITHIN_ALLOWANCE (threshold=$allowanceSeconds)",
                    AllowanceStatus.WITHIN_ALLOWANCE,
                    status
                )
            }

            // The single second that crosses the threshold
            val finalStatus = localTracker.onForegroundDetected(packageName, 1L)
            assertEquals(
                "Iteration $iteration: accumulated=$allowanceSeconds should be DEPLETED",
                AllowanceStatus.DEPLETED,
                finalStatus
            )
        }
    }

    /**
     * Property: Depletion detection must occur within the same poll cycle that
     * causes accumulation to meet/exceed the threshold — no delayed detection.
     */
    @Test
    fun `property - depletion is detected immediately in the crossing poll cycle`() = runTest {
        val random = Random(seed = 999)

        repeat(100) { iteration ->
            val (localRepo, localTracker) = newTracker()

            val allowanceMinutes = random.nextInt(1, 240)
            val allowanceSeconds = allowanceMinutes.toLong() * 60L
            val packageName = "com.immediate.app.$iteration"

            localRepo.addRule(domainRule(testUserId, packageName, allowanceMinutes))

            localTracker.setOnAllowanceDepletedListener { _, _ -> }

            // Pre-accumulate some random amount below threshold
            val preAccumulated = random.nextLong(0, allowanceSeconds)
            if (preAccumulated > 0) {
                localTracker.onForegroundDetected(packageName, preAccumulated)
            }

            // Now push exactly to or over the threshold in one poll
            val remaining = allowanceSeconds - preAccumulated
            val overshoot = random.nextLong(0, 60) // up to 60 extra seconds
            val crossingPoll = remaining + overshoot

            val status = localTracker.onForegroundDetected(packageName, crossingPoll)
            assertEquals(
                "Iteration $iteration: Crossing poll must immediately report DEPLETED",
                AllowanceStatus.DEPLETED,
                status
            )
        }
    }
}

// Fakes are in TestFakes.kt (shared across interception test files)
