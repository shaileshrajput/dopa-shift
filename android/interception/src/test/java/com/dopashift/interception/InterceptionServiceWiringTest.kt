package com.dopashift.interception

import com.dopashift.data.repository.LocalTelemetryRepository
import com.dopashift.domain.entity.InterceptionRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * Tests the wiring between AllowanceTracker and the InterceptionService's
 * depletion handling logic.
 *
 * Since InterceptionService is an Android Service that can't be unit-tested
 * without Robolectric/instrumented tests, these tests validate:
 * 1. AllowanceTracker correctly fires depletion callbacks.
 * 2. The depletion callback fires exactly once per app per day.
 * 3. The callback provides the correct package name and rule.
 * 4. Subsequent polls after depletion don't re-trigger.
 *
 * Validates: Requirements 2.3, 2.4, 2.10, 2.12
 */
class InterceptionServiceWiringTest {

    private lateinit var fakeRuleRepository: FakeInterceptionRuleRepository
    private lateinit var fakeTelemetryDao: FakeTelemetryDao
    private lateinit var telemetryRepository: LocalTelemetryRepository
    private lateinit var tracker: AllowanceTracker
    private lateinit var fixedClock: Clock

    private val testZone = ZoneId.of("America/New_York")
    private val testPackage = "com.social.distracting"
    private val testRuleId = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val testUserId = UUID.fromString("00000000-0000-0000-0000-000000000001")

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

    private fun createRule(
        packageName: String,
        allowanceMinutes: Int,
        id: UUID = testRuleId
    ): InterceptionRule {
        return InterceptionRule(
            id = id,
            userId = testUserId,
            appPackageName = packageName,
            dailyLimitMinutes = allowanceMinutes,
            enabled = true,
            pausedForDate = null,
            createdAt = Instant.ofEpochMilli(System.currentTimeMillis())
        )
    }

    // --- Requirement 2.3: Accumulate elapsed time against daily allowance ---

    @Test
    fun `handleForegroundApp accumulates time via AllowanceTracker`() = runTest {
        val rule = createRule(testPackage, allowanceMinutes = 5) // 300 seconds
        fakeRuleRepository.addRule(rule)

        // Simulate 3 poll cycles of 5 seconds each = 15 seconds accumulated.
        // Accumulation is now buffered in memory and flushed on a >=60s cadence,
        // so we assert the tracker's total accounting (persisted + pending) via
        // remaining allowance rather than the persisted store alone.
        repeat(3) {
            tracker.onForegroundDetected(testPackage, 5L)
        }

        // 300s allowance - 15s accumulated = 285s remaining.
        val remaining = tracker.getRemainingSeconds(testPackage)
        assertEquals(285L, remaining)
    }

    @Test
    fun `handleForegroundApp returns WITHIN_ALLOWANCE when under limit`() = runTest {
        val rule = createRule(testPackage, allowanceMinutes = 5)
        fakeRuleRepository.addRule(rule)

        val status = tracker.onForegroundDetected(testPackage, 5L)
        assertEquals(AllowanceStatus.WITHIN_ALLOWANCE, status)
    }

    @Test
    fun `handleForegroundApp skips untracked apps`() = runTest {
        // No rule added for this package
        val status = tracker.onForegroundDetected("com.untracked.app", 5L)
        assertEquals(AllowanceStatus.NOT_TRACKED, status)
    }

    // --- Requirement 2.4: Trigger overlay when allowance depleted ---

    @Test
    fun `depletion callback fires when allowance is fully consumed`() = runTest {
        val rule = createRule(testPackage, allowanceMinutes = 1) // 60 seconds
        fakeRuleRepository.addRule(rule)

        var depletedPackage: String? = null
        var depletedRule: InterceptionRule? = null
        tracker.setOnAllowanceDepletedListener { pkg, r ->
            depletedPackage = pkg
            depletedRule = r
        }

        // Accumulate 60 seconds (12 polls of 5s each)
        repeat(12) {
            tracker.onForegroundDetected(testPackage, 5L)
        }

        assertEquals(testPackage, depletedPackage)
        assertEquals(rule.id, depletedRule?.id)
    }

    @Test
    fun `depletion returns DEPLETED status`() = runTest {
        val rule = createRule(testPackage, allowanceMinutes = 1)
        fakeRuleRepository.addRule(rule)

        // Accumulate exactly to the limit
        repeat(11) {
            tracker.onForegroundDetected(testPackage, 5L)
        }
        // This poll crosses the threshold (55 + 5 = 60)
        val status = tracker.onForegroundDetected(testPackage, 5L)
        assertEquals(AllowanceStatus.DEPLETED, status)
    }

    @Test
    fun `depletion callback fires only once per app per day`() = runTest {
        val rule = createRule(testPackage, allowanceMinutes = 1)
        fakeRuleRepository.addRule(rule)

        var callbackCount = 0
        tracker.setOnAllowanceDepletedListener { _, _ -> callbackCount++ }

        // Deplete the allowance
        repeat(12) {
            tracker.onForegroundDetected(testPackage, 5L)
        }
        assertEquals(1, callbackCount)

        // Continue polling — should NOT re-trigger the callback
        repeat(10) {
            tracker.onForegroundDetected(testPackage, 5L)
        }
        assertEquals(1, callbackCount)
    }

    // --- Requirement 2.10: Simultaneous todo reminder notification ---

    @Test
    fun `depletion callback provides correct package for notification`() = runTest {
        val rule = createRule(testPackage, allowanceMinutes = 1)
        fakeRuleRepository.addRule(rule)

        val depletedPackages = mutableListOf<String>()
        tracker.setOnAllowanceDepletedListener { pkg, _ -> depletedPackages.add(pkg) }

        repeat(12) {
            tracker.onForegroundDetected(testPackage, 5L)
        }

        assertEquals(1, depletedPackages.size)
        assertEquals(testPackage, depletedPackages[0])
    }

    // --- Multiple tracked apps are independent ---

    @Test
    fun `multiple tracked apps accumulate independently`() = runTest {
        val app1 = "com.social.one"
        val app2 = "com.social.two"
        fakeRuleRepository.addRule(
            createRule(app1, allowanceMinutes = 1, id = UUID.fromString("00000000-0000-0000-0000-00000000000a"))
        )
        fakeRuleRepository.addRule(
            createRule(app2, allowanceMinutes = 2, id = UUID.fromString("00000000-0000-0000-0000-00000000000b"))
        )

        val depletedApps = mutableListOf<String>()
        tracker.setOnAllowanceDepletedListener { pkg, _ -> depletedApps.add(pkg) }

        // Deplete app1 (60s)
        repeat(12) { tracker.onForegroundDetected(app1, 5L) }
        assertEquals(listOf(app1), depletedApps)

        // app2 should still be within allowance at 60s (limit is 120s)
        repeat(12) { tracker.onForegroundDetected(app2, 5L) }
        assertEquals(listOf(app1), depletedApps) // still just app1

        // Deplete app2 (another 60s, total 120s)
        repeat(12) { tracker.onForegroundDetected(app2, 5L) }
        assertEquals(listOf(app1, app2), depletedApps)
    }

    // --- Requirement 2.12: Midnight reset ---

    @Test
    fun `isAllowanceDepleted returns true after depletion`() = runTest {
        val rule = createRule(testPackage, allowanceMinutes = 1)
        fakeRuleRepository.addRule(rule)

        repeat(12) {
            tracker.onForegroundDetected(testPackage, 5L)
        }

        assertTrue(tracker.isAllowanceDepleted(testPackage))
    }

    @Test
    fun `isAllowanceDepleted returns false when under limit`() = runTest {
        val rule = createRule(testPackage, allowanceMinutes = 5)
        fakeRuleRepository.addRule(rule)

        tracker.onForegroundDetected(testPackage, 5L)
        assertFalse(tracker.isAllowanceDepleted(testPackage))
    }

    // --- Edge cases for the service wiring ---

    @Test
    fun `zero-second poll does not accumulate time`() = runTest {
        val rule = createRule(testPackage, allowanceMinutes = 1)
        fakeRuleRepository.addRule(rule)

        tracker.onForegroundDetected(testPackage, 0L)

        val accumulated = telemetryRepository.getAccumulatedSeconds(
            testPackage,
            java.time.LocalDate.now(fixedClock.withZone(testZone))
        )
        // Zero-second events are still recorded but sum stays 0
        assertEquals(0L, accumulated)
    }

    @Test
    fun `depletion on exact boundary triggers correctly`() = runTest {
        val rule = createRule(testPackage, allowanceMinutes = 1) // 60 seconds
        fakeRuleRepository.addRule(rule)

        var triggered = false
        tracker.setOnAllowanceDepletedListener { _, _ -> triggered = true }

        // Single poll of exactly 60 seconds (edge case)
        tracker.onForegroundDetected(testPackage, 60L)
        assertTrue(triggered)
    }

    @Test
    fun `getRemainingSeconds returns correct value`() = runTest {
        val rule = createRule(testPackage, allowanceMinutes = 2) // 120 seconds
        fakeRuleRepository.addRule(rule)

        tracker.onForegroundDetected(testPackage, 30L)

        val remaining = tracker.getRemainingSeconds(testPackage)
        assertEquals(90L, remaining)
    }

    @Test
    fun `getRemainingSeconds returns null for untracked app`() = runTest {
        val remaining = tracker.getRemainingSeconds("com.nonexistent.app")
        assertEquals(null, remaining)
    }

    @Test
    fun `getRemainingSeconds returns zero when fully depleted`() = runTest {
        val rule = createRule(testPackage, allowanceMinutes = 1)
        fakeRuleRepository.addRule(rule)

        tracker.onForegroundDetected(testPackage, 100L) // Over the limit
        val remaining = tracker.getRemainingSeconds(testPackage)
        assertEquals(0L, remaining)
    }
}
