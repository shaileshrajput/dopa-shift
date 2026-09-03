package com.dopashift.interception

// Feature: screen-time-interception-engine, Property 18

import com.dopashift.interception.InterceptionConfig.Companion.MAX_FOCUS_EXTENSION_MINUTES
import com.dopashift.interception.InterceptionConfig.Companion.MIN_FOCUS_EXTENSION_MINUTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

/**
 * Property 18: Extension cap never exceeds three per day, with cap signalling.
 *
 * *For any* number of engaged depletion events for an app within a single calendar day, the
 * count of granted extensions never exceeds [FocusExtensionManager.MAX_EXTENSIONS_PER_DAY] (3);
 * the first three engaged depletions each yield [GrantResult.Granted] with `remaining` counting
 * down 2, 1, 0, and every subsequent engaged depletion yields [GrantResult.CapReached] (no new
 * grant) — the point at which the caller still presents the Intercept_Screen but shows the
 * "no additional extensions available today" message (Requirements 4.12, 4.13).
 *
 * The second property verifies the local-midnight reset: after advancing the injectable clock
 * past midnight in the fixed [ZoneId], the per-app counter resets and the app can be granted
 * extensions again (Requirement 4.12 day-scoping).
 *
 * **Validates: Requirements 4.12, 4.13**
 *
 * Property-based test following existing module conventions (JUnit4 + randomized iterations via
 * `kotlin.random.Random` and `repeat(N>=100)`, as in [FocusExtensionDurationBoundsPropertyTest] /
 * [MidnightAllowanceResetPropertyTest]); Kotest is not on this module's test classpath, so the
 * established randomized-input style is used. A [MutableClock] provides a controllable/advanceable
 * instant against a fixed [ZoneId].
 */
class FocusExtensionCapPropertyTest {

    /**
     * A controllable clock whose instant can be advanced, used to drive
     * [FocusExtensionManager]'s day-boundary and expiry logic deterministically.
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

    private val cap = FocusExtensionManager.MAX_EXTENSIONS_PER_DAY

    /**
     * Property: within a single calendar day, over any sequence of engaged grant attempts, the
     * count of granted extensions never exceeds the cap; the first [cap] attempts return
     * [GrantResult.Granted] with descending `remaining` (2/1/0) and all subsequent attempts
     * return [GrantResult.CapReached]. `extensionsUsedToday` never exceeds the cap and
     * `canGrantMore` is false once the cap is reached.
     */
    @Test
    fun `property - extension grants never exceed cap and signal CapReached after three`() {
        val random = Random(seed = 418)

        repeat(200) { iteration ->
            // Fixed zone + a starting instant safely mid-day so no attempt crosses midnight.
            val zone = ZoneId.of("Asia/Kolkata")
            val start = Instant.parse("2024-06-15T00:00:00Z")
                .plus(Duration.ofHours(random.nextLong(6, 15)))
            val clock = MutableClock(start, ZoneOffset.UTC)
            val manager = FocusExtensionManager(clock, zone)

            val pkg = "com.cap.app.$iteration.${random.nextInt(0, 100_000)}"
            val focusMinutes = random.nextInt(MIN_FOCUS_EXTENSION_MINUTES, MAX_FOCUS_EXTENSION_MINUTES + 1)
            val config = InterceptionConfig(focusExtensionMinutes = focusMinutes)

            // Randomize how many engaged depletions we throw at it (always more than the cap).
            val attempts = cap + 1 + random.nextInt(0, 10)
            var grantedCount = 0

            for (attempt in 1..attempts) {
                val result = manager.grantIfEngaged(pkg, engaged = true, config = config)

                if (attempt <= cap) {
                    assertTrue(
                        "iteration $iteration attempt $attempt: expected Granted, got $result",
                        result is GrantResult.Granted
                    )
                    grantedCount++
                    val granted = result as GrantResult.Granted
                    assertEquals(
                        "iteration $iteration attempt $attempt: remaining should count down",
                        cap - attempt,
                        granted.remaining
                    )
                    assertEquals(
                        "iteration $iteration attempt $attempt: expiry = now + configured duration",
                        clock.instant().plus(Duration.ofMinutes(focusMinutes.toLong())),
                        granted.expiresAt
                    )
                } else {
                    assertEquals(
                        "iteration $iteration attempt $attempt: cap exhausted must signal CapReached",
                        GrantResult.CapReached,
                        result
                    )
                }

                // Invariant checked after every attempt.
                assertTrue(
                    "iteration $iteration attempt $attempt: extensionsUsedToday must never exceed cap",
                    manager.extensionsUsedToday(pkg) <= cap
                )
            }

            assertEquals(
                "iteration $iteration: exactly $cap grants should have succeeded",
                cap,
                grantedCount
            )
            assertEquals(
                "iteration $iteration: extensionsUsedToday should equal cap after exhaustion",
                cap,
                manager.extensionsUsedToday(pkg)
            )
            assertFalse(
                "iteration $iteration: canGrantMore must be false once cap reached",
                manager.canGrantMore(pkg)
            )
        }
    }

    /**
     * Property: after advancing the clock past the local-midnight boundary in the fixed zone,
     * the per-app extension counter resets so the app can be granted extensions again.
     * Randomizes package name, focus duration, and how far into the next day the clock advances.
     */
    @Test
    fun `property - counter resets after crossing local midnight so app can be granted again`() {
        val random = Random(seed = 1813)

        // Cover a spread of zones (positive/negative/half-hour offsets) to prove the reset is
        // keyed off local midnight in the injected zone, not UTC midnight.
        val zones = listOf(
            "Asia/Kolkata",        // UTC+5:30
            "America/New_York",    // UTC-5/-4
            "Europe/London",       // UTC+0/+1
            "Pacific/Auckland",    // UTC+12/+13
            "America/Los_Angeles"  // UTC-8/-7
        )

        repeat(120) { iteration ->
            val zone = ZoneId.of(zones[iteration % zones.size])
            // Start early in the local day so exhausting the cap stays within day D.
            val start = Instant.parse("2024-06-15T01:00:00Z")
                .plus(Duration.ofMinutes(random.nextLong(0, 120)))
            val clock = MutableClock(start, ZoneOffset.UTC)
            val manager = FocusExtensionManager(clock, zone)

            val pkg = "com.reset.app.$iteration.${random.nextInt(0, 100_000)}"
            val focusMinutes = random.nextInt(MIN_FOCUS_EXTENSION_MINUTES, MAX_FOCUS_EXTENSION_MINUTES + 1)
            val config = InterceptionConfig(focusExtensionMinutes = focusMinutes)

            // Exhaust the cap on day D.
            repeat(cap) {
                assertTrue(manager.grantIfEngaged(pkg, engaged = true, config = config) is GrantResult.Granted)
            }
            assertEquals(cap, manager.extensionsUsedToday(pkg))
            assertEquals(
                "iteration $iteration: further grant on same day must be capped",
                GrantResult.CapReached,
                manager.grantIfEngaged(pkg, engaged = true, config = config)
            )

            // Advance past local midnight (25h guarantees crossing midnight in any zone),
            // plus a randomized extra offset into the next day.
            clock.advance(Duration.ofHours(25).plus(Duration.ofMinutes(random.nextLong(0, 600))))

            // Counter has reset: app can be granted again.
            assertEquals(
                "iteration $iteration: counter should reset after midnight",
                0,
                manager.extensionsUsedToday(pkg)
            )
            assertTrue(
                "iteration $iteration: canGrantMore should be true on the new day",
                manager.canGrantMore(pkg)
            )
            val newDayGrant = manager.grantIfEngaged(pkg, engaged = true, config = config)
            assertTrue(
                "iteration $iteration: expected a fresh Granted on the new day, got $newDayGrant",
                newDayGrant is GrantResult.Granted
            )
            assertEquals(
                "iteration $iteration: first grant on new day leaves ${cap - 1} remaining",
                cap - 1,
                (newDayGrant as GrantResult.Granted).remaining
            )
        }
    }
}
