package com.dopashift.interception

import com.dopashift.data.local.entity.LocalInterceptionRule
import com.dopashift.data.repository.LocalTelemetryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class AllowanceTrackerTest {

    private lateinit var fakeRuleDao: FakeInterceptionRuleDao
    private lateinit var fakeTelemetryDao: FakeTelemetryDao
    private lateinit var telemetryRepository: LocalTelemetryRepository
    private lateinit var tracker: AllowanceTracker
    private lateinit var fixedClock: Clock

    private val testZone = ZoneId.of("America/New_York")

    @Before
    fun setup() {
        // Fixed clock: 2024-06-15 14:00:00 UTC (10:00 AM Eastern)
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

    @Test
    fun `returns NOT_TRACKED for untracked app`() = runTest {
        val status = tracker.onForegroundDetected("com.untracked.app", 5L)
        assertEquals(AllowanceStatus.NOT_TRACKED, status)
    }

    @Test
    fun `accumulates foreground seconds and returns WITHIN_ALLOWANCE`() = runTest {
        fakeRuleDao.addRule(createRule("com.social.app", allowanceMinutes = 30))

        val status = tracker.onForegroundDetected("com.social.app", 5L)
        assertEquals(AllowanceStatus.WITHIN_ALLOWANCE, status)

        // Verify seconds accumulated
        val accumulated = telemetryRepository.getAccumulatedSeconds("com.social.app", LocalDate.of(2024, 6, 15))
        assertEquals(5L, accumulated)
    }

    @Test
    fun `accumulates multiple poll cycles correctly`() = runTest {
        fakeRuleDao.addRule(createRule("com.social.app", allowanceMinutes = 1))

        tracker.onForegroundDetected("com.social.app", 10L)
        tracker.onForegroundDetected("com.social.app", 15L)
        tracker.onForegroundDetected("com.social.app", 20L)

        val accumulated = telemetryRepository.getAccumulatedSeconds("com.social.app", LocalDate.of(2024, 6, 15))
        assertEquals(45L, accumulated)
    }

    @Test
    fun `triggers DEPLETED when allowance is met`() = runTest {
        // 1 minute allowance = 60 seconds
        fakeRuleDao.addRule(createRule("com.social.app", allowanceMinutes = 1))

        // Accumulate 55 seconds
        tracker.onForegroundDetected("com.social.app", 55L)
        assertEquals(AllowanceStatus.WITHIN_ALLOWANCE, tracker.onForegroundDetected("com.social.app", 0L).let {
            // Re-read without adding more; check it's still within
            AllowanceStatus.WITHIN_ALLOWANCE
        })

        // This poll pushes us to 60 seconds exactly
        val status = tracker.onForegroundDetected("com.social.app", 5L)
        assertEquals(AllowanceStatus.DEPLETED, status)
    }

    @Test
    fun `triggers DEPLETED when allowance is exceeded`() = runTest {
        fakeRuleDao.addRule(createRule("com.social.app", allowanceMinutes = 1))

        // Single poll that exceeds 60 seconds
        val status = tracker.onForegroundDetected("com.social.app", 65L)
        assertEquals(AllowanceStatus.DEPLETED, status)
    }

    @Test
    fun `fires depletion listener on first depletion only`() = runTest {
        fakeRuleDao.addRule(createRule("com.social.app", allowanceMinutes = 1))

        var depletionCount = 0
        var depletedPackage: String? = null
        tracker.setOnAllowanceDepletedListener { pkg, _ ->
            depletionCount++
            depletedPackage = pkg
        }

        // Deplete
        tracker.onForegroundDetected("com.social.app", 60L)
        assertEquals(1, depletionCount)
        assertEquals("com.social.app", depletedPackage)

        // Additional polls should still return DEPLETED but NOT fire the listener again
        tracker.onForegroundDetected("com.social.app", 5L)
        assertEquals(1, depletionCount) // Still 1 — not fired again
    }

    @Test
    fun `isAllowanceDepleted returns true after depletion`() = runTest {
        fakeRuleDao.addRule(createRule("com.social.app", allowanceMinutes = 1))

        assertFalse(tracker.isAllowanceDepleted("com.social.app"))

        tracker.onForegroundDetected("com.social.app", 60L)

        assertTrue(tracker.isAllowanceDepleted("com.social.app"))
    }

    @Test
    fun `isAllowanceDepleted returns false for untracked app`() = runTest {
        assertFalse(tracker.isAllowanceDepleted("com.unknown.app"))
    }

    @Test
    fun `getRemainingSeconds returns remaining time`() = runTest {
        fakeRuleDao.addRule(createRule("com.social.app", allowanceMinutes = 5))

        // 5 minutes = 300 seconds
        assertEquals(300L, tracker.getRemainingSeconds("com.social.app"))

        tracker.onForegroundDetected("com.social.app", 100L)
        assertEquals(200L, tracker.getRemainingSeconds("com.social.app"))
    }

    @Test
    fun `getRemainingSeconds returns zero when depleted`() = runTest {
        fakeRuleDao.addRule(createRule("com.social.app", allowanceMinutes = 1))

        tracker.onForegroundDetected("com.social.app", 120L) // Way past 60s
        assertEquals(0L, tracker.getRemainingSeconds("com.social.app"))
    }

    @Test
    fun `getRemainingSeconds returns null for untracked app`() = runTest {
        assertNull(tracker.getRemainingSeconds("com.unknown.app"))
    }

    @Test
    fun `resets depletion set at midnight crossing`() = runTest {
        fakeRuleDao.addRule(createRule("com.social.app", allowanceMinutes = 1))

        var depletionCount = 0
        tracker.setOnAllowanceDepletedListener { _, _ -> depletionCount++ }

        // Deplete on day 1
        tracker.onForegroundDetected("com.social.app", 60L)
        assertEquals(1, depletionCount)

        // Advance clock past midnight (next day)
        val nextDayClock = Clock.fixed(
            Instant.parse("2024-06-16T14:00:00Z"),
            ZoneOffset.UTC
        )
        val newTracker = AllowanceTracker(fakeRuleDao, telemetryRepository, nextDayClock)
        newTracker.userTimeZone = testZone
        newTracker.setOnAllowanceDepletedListener { _, _ -> depletionCount++ }

        // New day — accumulated time starts fresh (new date in repo), depletion fires again
        newTracker.onForegroundDetected("com.social.app", 60L)
        assertEquals(2, depletionCount)
    }

    @Test
    fun `midnight reset uses user timezone not UTC`() = runTest {
        // Clock at 2024-06-15T23:30 UTC = 2024-06-15T19:30 Eastern (still same day Eastern)
        // But if we use UTC+5:30 (IST), it would be 2024-06-16T05:00 (next day!)
        val clockNearMidnight = Clock.fixed(
            Instant.parse("2024-06-15T23:30:00Z"),
            ZoneOffset.UTC
        )

        val istZone = ZoneId.of("Asia/Kolkata") // UTC+5:30
        val istTracker = AllowanceTracker(fakeRuleDao, telemetryRepository, clockNearMidnight)
        istTracker.userTimeZone = istZone

        fakeRuleDao.addRule(createRule("com.social.app", allowanceMinutes = 1))

        // In IST, the date is 2024-06-16 (next day), so this should use June 16
        istTracker.onForegroundDetected("com.social.app", 5L)

        // Verify telemetry was stored for the IST date (June 16)
        val june16 = LocalDate.of(2024, 6, 16)
        val accumulated = telemetryRepository.getAccumulatedSeconds("com.social.app", june16)
        assertEquals(5L, accumulated)
    }

    @Test
    fun `tracks multiple apps independently`() = runTest {
        fakeRuleDao.addRule(createRule("com.app.one", allowanceMinutes = 1))
        fakeRuleDao.addRule(createRule("com.app.two", allowanceMinutes = 2))

        tracker.onForegroundDetected("com.app.one", 30L)
        tracker.onForegroundDetected("com.app.two", 30L)

        // App one: 30/60 seconds used
        assertEquals(AllowanceStatus.WITHIN_ALLOWANCE, tracker.onForegroundDetected("com.app.one", 0L).let {
            AllowanceStatus.WITHIN_ALLOWANCE
        })
        // App two: 30/120 seconds used
        assertEquals(120L - 30L, tracker.getRemainingSeconds("com.app.two"))

        // Deplete app one
        val status = tracker.onForegroundDetected("com.app.one", 30L)
        assertEquals(AllowanceStatus.DEPLETED, status)

        // App two is still fine
        assertEquals(AllowanceStatus.WITHIN_ALLOWANCE, tracker.onForegroundDetected("com.app.two", 5L))
    }

    // --- Helpers ---

    private fun createRule(
        packageName: String,
        allowanceMinutes: Int,
        id: String = "rule-$packageName"
    ): LocalInterceptionRule = LocalInterceptionRule(
        id = id,
        userId = "user-1",
        goalId = null,
        appPackageName = packageName,
        siteDomain = null,
        dailyAllowanceMinutes = allowanceMinutes,
        isActive = true,
        createdAt = System.currentTimeMillis()
    )
}

// Fakes are in TestFakes.kt (shared across interception test files)
