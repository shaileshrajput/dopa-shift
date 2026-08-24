package com.dopashift.interception

import com.dopashift.data.local.entity.LocalInterceptionRule
import com.dopashift.data.repository.LocalTelemetryRepository
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

/**
 * Integration test suite: Screen-Time Interception Engine
 *
 * Requirement 21.10: Permission revocation, Android Go detection,
 * overlay trigger consistency across API levels.
 *
 * Tests the full interception flow:
 * - Permission checks and revocation handling
 * - Device capability detection (Android Go)
 * - Overlay trigger timing and consistency
 * - Multi-app tracking independence
 * - Edge cases in allowance computation
 */
class InterceptionIntegrationTest {

    private lateinit var fakeRuleDao: FakeInterceptionRuleDao
    private lateinit var fakeTelemetryDao: FakeTelemetryDao
    private lateinit var telemetryRepository: LocalTelemetryRepository
    private lateinit var tracker: AllowanceTracker
    private lateinit var fixedClock: Clock

    private val testZone = ZoneId.of("America/New_York")

    @Before
    fun setup() {
        fixedClock = Clock.fixed(
            Instant.parse("2024-06-15T14:00:00Z"),
            ZoneOffset.UTC
        )
        fakeRuleDao = FakeInterceptionRuleDao()
        fakeTelemetryDao = FakeTelemetryDao()
        telemetryRepository = LocalTelemetryRepository(fakeTelemetryDao)
        tracker = AllowanceTracker(fakeRuleDao, telemetryRepository, fixedClock)
        tracker.userTimeZone = testZone
    }

    // === Permission Revocation Scenarios ===

    @Test
    fun `service stops when permissions are revoked mid-operation`() {
        // Simulate permission check returning false
        val permHelper = InterceptionPermissionHelper()
        // Permission revocation is detected at the service level;
        // this test validates the helper correctly reports missing permissions
        val status = permHelper.checkPermissionStatus(FakeContext(hasUsageStats = false, hasOverlay = true))
        assertFalse("Should detect missing usage stats permission", status.allGranted)
        assertFalse("Usage stats should be false", status.hasUsageStats)
        assertTrue("Overlay should still be true", status.hasOverlay)
    }

    @Test
    fun `both permissions revoked results in correct status`() {
        val permHelper = InterceptionPermissionHelper()
        val status = permHelper.checkPermissionStatus(FakeContext(hasUsageStats = false, hasOverlay = false))
        assertFalse(status.allGranted)
        assertFalse(status.hasUsageStats)
        assertFalse(status.hasOverlay)
    }

    @Test
    fun `all permissions granted results in correct status`() {
        val permHelper = InterceptionPermissionHelper()
        val status = permHelper.checkPermissionStatus(FakeContext(hasUsageStats = true, hasOverlay = true))
        assertTrue(status.allGranted)
    }

    // === Android Go / Low-RAM Detection ===

    @Test
    fun `Android Go device returns unsupported`() {
        val checker = DeviceCapabilityChecker()
        val result = checker.checkInterceptionSupport(FakeContext(isLowRamDevice = true))
        assertTrue(
            "Low-RAM device should be unsupported",
            result is DeviceCapabilityChecker.DeviceSupport.Unsupported
        )
    }

    @Test
    fun `standard device returns supported`() {
        val checker = DeviceCapabilityChecker()
        val result = checker.checkInterceptionSupport(FakeContext(isLowRamDevice = false))
        assertTrue(
            "Standard device should be supported",
            result is DeviceCapabilityChecker.DeviceSupport.Supported
        )
    }

    // === Overlay Trigger Consistency ===

    @Test
    fun `overlay triggers at exact threshold regardless of poll chunk sizes`() = runTest {
        val allowanceMinutes = 10
        val allowanceSeconds = 600L
        val packageName = "com.test.exact"

        fakeRuleDao.addRule(createRule(packageName, allowanceMinutes))

        // Simulate various poll chunk sizes
        val chunks = listOf(50L, 100L, 75L, 125L, 80L, 90L, 80L) // Total = 600
        var totalAccumulated = 0L

        for (chunk in chunks) {
            val status = tracker.onForegroundDetected(packageName, chunk)
            totalAccumulated += chunk

            if (totalAccumulated >= allowanceSeconds) {
                assertEquals(
                    "Should be DEPLETED at accumulated=$totalAccumulated",
                    AllowanceStatus.DEPLETED,
                    status
                )
                break
            } else {
                assertEquals(
                    "Should be WITHIN_ALLOWANCE at accumulated=$totalAccumulated",
                    AllowanceStatus.WITHIN_ALLOWANCE,
                    status
                )
            }
        }
    }

    @Test
    fun `rapid sequential polls accumulate correctly`() = runTest {
        val packageName = "com.test.rapid"
        fakeRuleDao.addRule(createRule(packageName, 5)) // 5 minutes = 300 seconds

        // Simulate 60 rapid 5-second polls (total: 300 seconds = exactly at threshold)
        var lastStatus = AllowanceStatus.WITHIN_ALLOWANCE
        for (i in 1..60) {
            lastStatus = tracker.onForegroundDetected(packageName, 5L)
        }

        assertEquals(AllowanceStatus.DEPLETED, lastStatus)
    }

    @Test
    fun `multiple apps tracked independently — depleting one does not affect another`() = runTest {
        val app1 = "com.social.one"
        val app2 = "com.social.two"

        fakeRuleDao.addRule(createRule(app1, 5))   // 300 seconds
        fakeRuleDao.addRule(createRule(app2, 10))  // 600 seconds

        // Deplete app1
        tracker.onForegroundDetected(app1, 300L)
        assertEquals(AllowanceStatus.DEPLETED, tracker.onForegroundDetected(app1, 1L))

        // App2 should still be within allowance
        val app2Status = tracker.onForegroundDetected(app2, 100L)
        assertEquals(AllowanceStatus.WITHIN_ALLOWANCE, app2Status)
        assertEquals(500L, tracker.getRemainingSeconds(app2))
    }

    @Test
    fun `zero-second poll does not change state`() = runTest {
        val packageName = "com.test.zero"
        fakeRuleDao.addRule(createRule(packageName, 5))

        tracker.onForegroundDetected(packageName, 100L)
        val remaining1 = tracker.getRemainingSeconds(packageName)

        // Zero-second poll should not change accumulated time
        tracker.onForegroundDetected(packageName, 0L)
        val remaining2 = tracker.getRemainingSeconds(packageName)

        assertEquals(remaining1, remaining2)
    }

    // === Edge Cases ===

    @Test
    fun `very large single poll exceeding allowance triggers DEPLETED`() = runTest {
        val packageName = "com.test.large"
        fakeRuleDao.addRule(createRule(packageName, 1)) // 60 seconds

        // Single poll of 3600 seconds (way over 60s allowance)
        val status = tracker.onForegroundDetected(packageName, 3600L)
        assertEquals(AllowanceStatus.DEPLETED, status)
    }

    @Test
    fun `deactivated rule is not tracked`() = runTest {
        val packageName = "com.test.inactive"
        fakeRuleDao.addRule(
            LocalInterceptionRule(
                id = "rule-inactive",
                userId = "user-1",
                goalId = null,
                appPackageName = packageName,
                siteDomain = null,
                dailyAllowanceMinutes = 5,
                isActive = false, // Inactive!
                createdAt = System.currentTimeMillis()
            )
        )

        val status = tracker.onForegroundDetected(packageName, 1000L)
        assertEquals(AllowanceStatus.NOT_TRACKED, status)
    }

    // === Helpers ===

    private fun createRule(packageName: String, allowanceMinutes: Int): LocalInterceptionRule {
        return LocalInterceptionRule(
            id = "rule-$packageName",
            userId = "user-1",
            goalId = null,
            appPackageName = packageName,
            siteDomain = null,
            dailyAllowanceMinutes = allowanceMinutes,
            isActive = true,
            createdAt = System.currentTimeMillis()
        )
    }
}

/**
 * Fake context for testing permission and capability checks without real Android Context.
 */
class FakeContext(
    override val hasUsageStats: Boolean = true,
    override val hasOverlay: Boolean = true,
    override val isLowRamDevice: Boolean = false
) : InterceptionContext
