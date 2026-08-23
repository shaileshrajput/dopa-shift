package com.dopashift.interception

import com.dopashift.data.local.entity.LocalInterceptionRule
import com.dopashift.data.repository.LocalTelemetryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/**
 * Property 20: Midnight Allowance Reset
 *
 * For any user timezone, when the clock crosses midnight in that timezone,
 * the daily allowance for every tracked app SHALL reset — accumulated seconds
 * return to zero and previously-depleted apps can be used again until their
 * new daily allowance is exhausted.
 *
 * **Validates: Requirements 2.12**
 *
 * This property test verifies:
 * 1. After midnight crossing, depleted apps become WITHIN_ALLOWANCE again.
 * 2. The reset is timezone-aware (midnight in the user's zone, not UTC).
 * 3. The depletion callback can fire again on the new day.
 * 4. Pre-midnight accumulation does NOT carry over to the new day.
 */
class MidnightAllowanceResetPropertyTest {

    /**
     * Property: For any timezone and any allowance, after crossing midnight the
     * tracker resets — a previously depleted app returns to WITHIN_ALLOWANCE.
     */
    @Test
    fun `property - midnight crossing resets depleted apps to WITHIN_ALLOWANCE`() = runTest {
        val random = Random(seed = 42)

        // Test across many different timezones
        val testZones = listOf(
            "America/New_York",    // UTC-5/-4
            "Europe/London",       // UTC+0/+1
            "Asia/Kolkata",        // UTC+5:30
            "Asia/Tokyo",          // UTC+9
            "Pacific/Auckland",    // UTC+12/+13
            "America/Los_Angeles", // UTC-8/-7
            "Australia/Sydney",    // UTC+10/+11
            "Europe/Berlin",       // UTC+1/+2
            "Asia/Shanghai",       // UTC+8
            "America/Chicago"      // UTC-6/-5
        )

        for (zoneName in testZones) {
            repeat(10) { iteration ->
                val zone = ZoneId.of(zoneName)

                // Pick a random time on day 1 (afternoon)
                val baseInstant = Instant.parse("2024-06-15T12:00:00Z")
                    .plus(random.nextLong(0, 8 * 3600), ChronoUnit.SECONDS)

                val day1Clock = Clock.fixed(baseInstant, ZoneOffset.UTC)

                val fakeRuleDao = FakeInterceptionRuleDao()
                val fakeTelemetryDao = FakeTelemetryDao()
                val telemetryRepo = LocalTelemetryRepository(fakeTelemetryDao)
                val tracker = AllowanceTracker(fakeRuleDao, telemetryRepo, day1Clock)
                tracker.userTimeZone = zone

                val allowanceMinutes = random.nextInt(1, 120)
                val packageName = "com.reset.app.$zoneName.$iteration"

                fakeRuleDao.addRule(
                    LocalInterceptionRule(
                        id = "rule-$zoneName-$iteration",
                        userId = "user-1",
                        goalId = null,
                        appPackageName = packageName,
                        siteDomain = null,
                        dailyAllowanceMinutes = allowanceMinutes,
                        isActive = true,
                        createdAt = System.currentTimeMillis()
                    )
                )

                // Deplete on day 1
                tracker.onForegroundDetected(packageName, allowanceMinutes.toLong() * 60L)
                assertTrue(
                    "$zoneName iteration $iteration: Should be depleted on day 1",
                    tracker.isAllowanceDepleted(packageName)
                )

                // Advance clock to the next day (add 24 hours to guarantee crossing midnight in any timezone)
                val day2Instant = baseInstant.plus(24, ChronoUnit.HOURS)
                val day2Clock = Clock.fixed(day2Instant, ZoneOffset.UTC)

                // Create a new tracker with the new clock (simulating time advancement)
                val day2Tracker = AllowanceTracker(fakeRuleDao, telemetryRepo, day2Clock)
                day2Tracker.userTimeZone = zone

                // The new day should show the app as NOT depleted (fresh accumulation in repo for new date)
                val status = day2Tracker.onForegroundDetected(packageName, 1L)
                assertEquals(
                    "$zoneName iteration $iteration: After midnight, a 1-second poll should be WITHIN_ALLOWANCE",
                    AllowanceStatus.WITHIN_ALLOWANCE,
                    status
                )
            }
        }
    }

    /**
     * Property: The depletion callback fires again on the new day after midnight reset.
     */
    @Test
    fun `property - depletion callback fires again after midnight reset`() = runTest {
        val random = Random(seed = 88)

        repeat(50) { iteration ->
            val zone = ZoneId.of("America/New_York")
            val baseInstant = Instant.parse("2024-06-15T18:00:00Z")
            val day1Clock = Clock.fixed(baseInstant, ZoneOffset.UTC)

            val fakeRuleDao = FakeInterceptionRuleDao()
            val fakeTelemetryDao = FakeTelemetryDao()
            val telemetryRepo = LocalTelemetryRepository(fakeTelemetryDao)
            val tracker = AllowanceTracker(fakeRuleDao, telemetryRepo, day1Clock)
            tracker.userTimeZone = zone

            val allowanceMinutes = random.nextInt(1, 60)
            val packageName = "com.callback.reset.$iteration"

            fakeRuleDao.addRule(
                LocalInterceptionRule(
                    id = "rule-cbr-$iteration",
                    userId = "user-1",
                    goalId = null,
                    appPackageName = packageName,
                    siteDomain = null,
                    dailyAllowanceMinutes = allowanceMinutes,
                    isActive = true,
                    createdAt = System.currentTimeMillis()
                )
            )

            var callbackCount = 0
            tracker.setOnAllowanceDepletedListener { _, _ -> callbackCount++ }

            // Deplete on day 1
            tracker.onForegroundDetected(packageName, allowanceMinutes.toLong() * 60L)
            assertEquals("Iteration $iteration: Day 1 callback", 1, callbackCount)

            // Day 2 — new clock past midnight
            val day2Instant = baseInstant.plus(24, ChronoUnit.HOURS)
            val day2Clock = Clock.fixed(day2Instant, ZoneOffset.UTC)
            val day2Tracker = AllowanceTracker(fakeRuleDao, telemetryRepo, day2Clock)
            day2Tracker.userTimeZone = zone

            var day2CallbackCount = 0
            day2Tracker.setOnAllowanceDepletedListener { _, _ -> day2CallbackCount++ }

            // Deplete again on day 2
            day2Tracker.onForegroundDetected(packageName, allowanceMinutes.toLong() * 60L)
            assertEquals(
                "Iteration $iteration: Day 2 callback should fire independently",
                1,
                day2CallbackCount
            )
        }
    }

    /**
     * Property: The timezone determines WHICH midnight matters.
     * Two users viewing the same UTC instant but in different timezones
     * may be on different calendar days, and their resets happen independently.
     */
    @Test
    fun `property - timezone determines which midnight is used for reset`() = runTest {
        // At 2024-06-15T23:30:00Z:
        // - In UTC: still June 15
        // - In IST (UTC+5:30): June 16, 05:00 (next day!)
        val utcInstant = Instant.parse("2024-06-15T23:30:00Z")

        val utcZone = ZoneId.of("UTC")
        val istZone = ZoneId.of("Asia/Kolkata") // UTC+5:30

        val clock = Clock.fixed(utcInstant, ZoneOffset.UTC)
        val packageName = "com.timezone.app"
        val allowanceMinutes = 5

        // Setup for UTC user
        val utcRuleDao = FakeInterceptionRuleDao()
        val utcTelemetryDao = FakeTelemetryDao()
        val utcTelemetryRepo = LocalTelemetryRepository(utcTelemetryDao)
        utcRuleDao.addRule(createRule(packageName, allowanceMinutes, "rule-utc"))

        val utcTracker = AllowanceTracker(utcRuleDao, utcTelemetryRepo, clock)
        utcTracker.userTimeZone = utcZone

        // Setup for IST user
        val istRuleDao = FakeInterceptionRuleDao()
        val istTelemetryDao = FakeTelemetryDao()
        val istTelemetryRepo = LocalTelemetryRepository(istTelemetryDao)
        istRuleDao.addRule(createRule(packageName, allowanceMinutes, "rule-ist"))

        val istTracker = AllowanceTracker(istRuleDao, istTelemetryRepo, clock)
        istTracker.userTimeZone = istZone

        // Both deplete
        utcTracker.onForegroundDetected(packageName, allowanceMinutes.toLong() * 60L)
        istTracker.onForegroundDetected(packageName, allowanceMinutes.toLong() * 60L)

        assertTrue("UTC user should be depleted", utcTracker.isAllowanceDepleted(packageName))
        assertTrue("IST user should be depleted", istTracker.isAllowanceDepleted(packageName))

        // Advance 1 hour: 2024-06-16T00:30:00Z
        // - UTC: Now June 16 (crossed midnight!)
        // - IST: June 16, 06:00 (still same day as before — already June 16)
        val nextHourInstant = utcInstant.plus(1, ChronoUnit.HOURS)
        val nextHourClock = Clock.fixed(nextHourInstant, ZoneOffset.UTC)

        val utcDay2Tracker = AllowanceTracker(utcRuleDao, utcTelemetryRepo, nextHourClock)
        utcDay2Tracker.userTimeZone = utcZone

        val istSameDayTracker = AllowanceTracker(istRuleDao, istTelemetryRepo, nextHourClock)
        istSameDayTracker.userTimeZone = istZone

        // UTC user: crossed midnight, fresh day — should be WITHIN_ALLOWANCE
        val utcStatus = utcDay2Tracker.onForegroundDetected(packageName, 1L)
        assertEquals(
            "UTC user crossed midnight; should be WITHIN_ALLOWANCE on new day",
            AllowanceStatus.WITHIN_ALLOWANCE,
            utcStatus
        )

        // IST user: still same day (June 16) — still depleted
        assertTrue(
            "IST user did NOT cross midnight again; should still be depleted",
            istSameDayTracker.isAllowanceDepleted(packageName)
        )
    }

    /**
     * Property: Pre-midnight accumulation does NOT carry over to the next day.
     * Each day starts with zero accumulated seconds.
     */
    @Test
    fun `property - accumulation does not carry over across midnight`() = runTest {
        val random = Random(seed = 555)

        repeat(50) { iteration ->
            val zone = ZoneId.of("Europe/London")
            val baseInstant = Instant.parse("2024-06-15T20:00:00Z")
            val day1Clock = Clock.fixed(baseInstant, ZoneOffset.UTC)

            val fakeRuleDao = FakeInterceptionRuleDao()
            val fakeTelemetryDao = FakeTelemetryDao()
            val telemetryRepo = LocalTelemetryRepository(fakeTelemetryDao)
            val tracker = AllowanceTracker(fakeRuleDao, telemetryRepo, day1Clock)
            tracker.userTimeZone = zone

            val allowanceMinutes = random.nextInt(5, 120)
            val allowanceSeconds = allowanceMinutes.toLong() * 60L
            val packageName = "com.nocarry.app.$iteration"

            fakeRuleDao.addRule(createRule(packageName, allowanceMinutes, "rule-nc-$iteration"))

            // Accumulate most of the allowance on day 1 (but don't deplete)
            val day1Usage = random.nextLong(1, allowanceSeconds)
            tracker.onForegroundDetected(packageName, day1Usage)

            // Advance to day 2
            val day2Instant = baseInstant.plus(24, ChronoUnit.HOURS)
            val day2Clock = Clock.fixed(day2Instant, ZoneOffset.UTC)
            val day2Tracker = AllowanceTracker(fakeRuleDao, telemetryRepo, day2Clock)
            day2Tracker.userTimeZone = zone

            // On day 2, remaining should be full allowance (day 1 usage NOT carried over)
            val remaining = day2Tracker.getRemainingSeconds(packageName)
            assertEquals(
                "Iteration $iteration: Day 2 should have full allowance ($allowanceSeconds seconds)",
                allowanceSeconds,
                remaining
            )
        }
    }

    private fun createRule(
        packageName: String,
        allowanceMinutes: Int,
        id: String
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
