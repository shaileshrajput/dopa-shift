package com.dopashift.interception

import com.dopashift.data.repository.LocalTelemetryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * Property 9: Pause-for-today suppresses then auto-resumes.
 *
 * *For any* Rule paused for calendar day D, while the device local date equals D the
 * engine attributes no accumulation and triggers no interception for that Rule; for any
 * local date after D the Rule behaves as enabled.
 *
 * **Validates: Requirements 3.5**
 *
 * // Feature: screen-time-interception-engine, Property 9
 *
 * The tracker is driven by an injectable [Clock] and a fixed [ZoneId] (Asia/Kolkata).
 * A [MutableClock] lets a single tracker instance be advanced across the local midnight
 * boundary so the auto-resume transition (isPausedOn(today) flipping false) is exercised
 * on the same tracker that observed the paused day.
 *
 * Two properties are asserted over randomized inputs (>= 100 iterations each):
 *
 *  (1) Suppression: while the local date equals the paused day D, no matter how many
 *      foreground polls occur — even far exceeding the daily limit — the tracker never
 *      returns DEPLETED, no depletion callback fires, and no accumulation is attributed
 *      (getRemainingSeconds stays at the full allowance and persisted seconds stay 0).
 *
 *  (2) Auto-resume: after advancing the clock past local midnight to the day after D
 *      (the Rule is paused only for the prior day), foreground polls resume accumulation
 *      and eventually return DEPLETED once the limit is exceeded.
 */
class PauseForTodayAutoResumePropertyTest {

    /** A fixed IST zone so the local-date/midnight boundary is deterministic. */
    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    private val testUserId = testUuid("user-pause-1")

    /**
     * A [Clock] whose instant can be advanced in-place, letting a single tracker
     * instance observe a day boundary crossing.
     */
    private class MutableClock(
        var current: Instant,
        private val zone: ZoneId
    ) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(z: ZoneId): Clock = MutableClock(current, z)
        override fun instant(): Instant = current
        fun advance(duration: java.time.Duration) {
            current = current.plus(duration)
        }
    }

    /**
     * Resolves an [Instant] that falls on local calendar day [date] at [hour] o'clock
     * in the fixed [zone]. Used to place "today" precisely and to jump into the next day.
     */
    private fun instantOn(date: LocalDate, hour: Int): Instant =
        date.atTime(hour, 0).atZone(zone).toInstant()

    @Test
    fun `property - paused rule attributes no accumulation and never depletes on the paused day`() =
        runTest {
            val random = Random(seed = 3905)

            repeat(150) { iteration ->
                // The paused day D. Vary it so the property does not hinge on one date.
                val pausedDay = LocalDate.of(2024, 1, 1).plusDays(random.nextLong(0, 900))

                // Start the clock somewhere in the middle of day D so many polls fit
                // before midnight without ambiguity around the boundary.
                val clock = MutableClock(instantOn(pausedDay, hour = 8), zone)

                val ruleRepository = FakeInterceptionRuleRepository()
                val telemetryDao = FakeTelemetryDao()
                val telemetryRepository = LocalTelemetryRepository(telemetryDao)
                val tracker = AllowanceTracker(ruleRepository, telemetryRepository, clock)
                tracker.userTimeZone = zone
                tracker.userId = testUserId

                val allowanceMinutes = random.nextInt(1, 481)
                val allowanceSeconds = allowanceMinutes.toLong() * 60L
                val packageName = "com.paused.app.$iteration"

                // Rule paused for the very day the clock currently sits on.
                ruleRepository.addRule(
                    domainRule(
                        userId = testUserId,
                        packageName = packageName,
                        dailyLimitMinutes = allowanceMinutes,
                        pausedForDate = pausedDay
                    )
                )

                var callbackCount = 0
                tracker.setOnAllowanceDepletedListener { _, _ -> callbackCount++ }

                // Poll many times, far exceeding the daily limit in total elapsed time.
                var totalPolled = 0L
                val polls = random.nextInt(5, 40)
                repeat(polls) {
                    val increment = random.nextLong(1, 600L)
                    totalPolled += increment
                    val status = tracker.onForegroundDetected(packageName, increment)
                    assertEquals(
                        "iter $iteration: paused rule must report WITHIN_ALLOWANCE, never DEPLETED",
                        AllowanceStatus.WITHIN_ALLOWANCE,
                        status
                    )
                }

                // No depletion callback ever fired while paused, regardless of totalPolled.
                assertTrue(
                    "iter $iteration: at least one poll occurred",
                    totalPolled > 0L
                )
                assertEquals(
                    "iter $iteration: no depletion callback may fire while paused",
                    0,
                    callbackCount
                )

                // No accumulation attributed: remaining is the full allowance and the
                // Local_Store recorded zero foreground seconds for this app today.
                assertEquals(
                    "iter $iteration: remaining must equal full allowance while paused",
                    allowanceSeconds,
                    tracker.getRemainingSeconds(packageName)
                )
                assertEquals(
                    "iter $iteration: no telemetry may be persisted while paused",
                    0L,
                    telemetryRepository.getAccumulatedSeconds(packageName, pausedDay)
                )
                assertTrue(
                    "iter $iteration: isAllowanceDepleted must stay false while paused",
                    !tracker.isAllowanceDepleted(packageName)
                )
            }
        }

    @Test
    fun `property - after local midnight past the paused day accumulation resumes and eventually depletes`() =
        runTest {
            val random = Random(seed = 71121)

            repeat(150) { iteration ->
                val pausedDay = LocalDate.of(2024, 1, 1).plusDays(random.nextLong(0, 900))
                val nextDay = pausedDay.plusDays(1)

                // Start late on the paused day so a single advance crosses local midnight.
                val startInstant = instantOn(pausedDay, hour = 22)
                val clock = MutableClock(startInstant, zone)

                val ruleRepository = FakeInterceptionRuleRepository()
                val telemetryDao = FakeTelemetryDao()
                val telemetryRepository = LocalTelemetryRepository(telemetryDao)
                val tracker = AllowanceTracker(ruleRepository, telemetryRepository, clock)
                tracker.userTimeZone = zone
                tracker.userId = testUserId

                val allowanceMinutes = random.nextInt(1, 60)
                val allowanceSeconds = allowanceMinutes.toLong() * 60L
                val packageName = "com.resume.app.$iteration"

                ruleRepository.addRule(
                    domainRule(
                        userId = testUserId,
                        packageName = packageName,
                        dailyLimitMinutes = allowanceMinutes,
                        pausedForDate = pausedDay
                    )
                )

                var callbackCount = 0
                tracker.setOnAllowanceDepletedListener { _, _ -> callbackCount++ }

                // While still on the paused day: suppressed.
                tracker.onForegroundDetected(packageName, random.nextLong(1, 300))
                assertEquals(
                    "iter $iteration: no callback while paused",
                    0,
                    callbackCount
                )

                // Advance the clock past local midnight into the next day (isPausedOn(today)
                // is now false, so the rule behaves as enabled).
                clock.advance(java.time.Duration.ofHours(3)) // 22:00 IST -> 01:00 IST next day
                assertNotEquals(
                    "iter $iteration: clock must be on the day after the paused day",
                    pausedDay,
                    LocalDate.ofInstant(clock.instant(), zone)
                )
                assertEquals(nextDay, LocalDate.ofInstant(clock.instant(), zone))

                // Accumulate in whole-limit-sized increments until the limit is exceeded.
                var accumulated = 0L
                var sawDepleted = false
                val chunk = maxOf(1L, allowanceSeconds / 4)
                var guard = 0
                while (accumulated < allowanceSeconds + chunk && guard < 1000) {
                    guard++
                    val status = tracker.onForegroundDetected(packageName, chunk)
                    accumulated += chunk
                    if (status == AllowanceStatus.DEPLETED) {
                        sawDepleted = true
                        break
                    } else {
                        assertEquals(
                            "iter $iteration: before threshold status must be WITHIN_ALLOWANCE (acc=$accumulated / $allowanceSeconds)",
                            AllowanceStatus.WITHIN_ALLOWANCE,
                            status
                        )
                    }
                }

                assertTrue(
                    "iter $iteration: rule must eventually deplete after auto-resume on $nextDay",
                    sawDepleted
                )
                assertEquals(
                    "iter $iteration: depletion callback must fire exactly once after resume",
                    1,
                    callbackCount
                )
                // Accumulation is now attributed on the new day.
                assertTrue(
                    "iter $iteration: telemetry must accumulate on the resumed day",
                    telemetryRepository.getAccumulatedSeconds(packageName, nextDay) > 0L
                )
            }
        }
}
