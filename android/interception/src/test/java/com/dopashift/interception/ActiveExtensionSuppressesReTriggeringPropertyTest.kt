package com.dopashift.interception

// Feature: screen-time-interception-engine, Property 17

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
 * Property 17: Active extension suppresses re-triggering (half-open interval `[t0, t0+d)`).
 *
 * After granting a Focus_Extension for a Monitored_App at grant instant `t0` with a configured
 * duration of `d` minutes, [FocusExtensionManager.isExtensionActive] SHALL report:
 *  - `true` for every query instant `now` in the half-open interval `[t0, t0 + d)` — the
 *    Intercept_Screen must not re-trigger while the extension is active; and
 *  - `false` at `now == t0 + d` (the exact expiry instant) and for every `now > t0 + d` — the
 *    extension has expired and re-triggering resumes.
 *
 * A package that was never granted an extension SHALL report `false` at any instant.
 *
 * **Validates: Requirements 4.11**
 *
 * Property-based test following existing module conventions (JUnit4 + randomized iterations via
 * `kotlin.random.Random` and `repeat(N >= 100)`, as in [MidnightAllowanceResetPropertyTest] /
 * [FocusExtensionDurationBoundsPropertyTest]); Kotest is not on this module's test classpath, so
 * the established randomized-input style with >=100 iterations is used. Each scenario re-creates a
 * [Clock.fixed] at the chosen grant instant `t0` so the grant expiry is deterministic, then queries
 * [FocusExtensionManager.isExtensionActive] with query instants swept across and around the boundary.
 */
class ActiveExtensionSuppressesReTriggeringPropertyTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    /**
     * Property: for a granted extension of `d` minutes, every instant in `[t0, t0+d)` is active and
     * every instant at or after `t0+d` is inactive. Query offsets are drawn randomly across the
     * interval and around the boundary, and the exact endpoints (`t0`, one nanosecond before expiry,
     * exactly expiry, and past expiry) are pinned every iteration.
     */
    @Test
    fun `property - active within half-open interval and inactive at or after expiry`() {
        val random = Random(seed = 1711)

        repeat(300) { iteration ->
            // Randomize the grant instant t0 within a stable base day (keeps the query instants on
            // the same calendar day as t0 so the day-scoping guard never interferes with the
            // interval semantics under test — the prior-day case is covered separately below).
            val baseDayStart = Instant.parse("2024-06-15T00:00:00Z")
            val t0 = baseDayStart.plusSeconds(random.nextLong(0, 12 * 3600))

            // Randomize the configured duration across the valid Focus_Extension range (1..15).
            val durationMinutes = random.nextInt(1, 16)
            val config = InterceptionConfig(focusExtensionMinutes = durationMinutes)
            val duration = Duration.ofMinutes(durationMinutes.toLong())
            val expiry = t0.plus(duration)

            // Clock fixed at t0 so grantIfEngaged stamps the expiry at exactly t0 + d.
            val clock = Clock.fixed(t0, ZoneOffset.UTC)
            val manager = FocusExtensionManager(clock, zone)
            val pkg = "com.suppress.app.$iteration"

            val result = manager.grantIfEngaged(pkg, engaged = true, config = config)
            assertTrue(
                "iteration $iteration: expected a grant (engaged=true, under cap)",
                result is GrantResult.Granted
            )

            // --- Pinned boundary instants -------------------------------------------------
            // Exactly the grant instant: active (interval is closed on the left).
            assertTrue(
                "iteration $iteration: t0 must be active",
                manager.isExtensionActive(pkg, t0)
            )
            // One nanosecond before expiry: still active.
            assertTrue(
                "iteration $iteration: just before expiry must be active",
                manager.isExtensionActive(pkg, expiry.minusNanos(1))
            )
            // Exactly the expiry instant: NOT active (interval is open on the right).
            assertFalse(
                "iteration $iteration: exactly at expiry ($expiry) must be inactive",
                manager.isExtensionActive(pkg, expiry)
            )
            // Just after expiry: NOT active.
            assertFalse(
                "iteration $iteration: just after expiry must be inactive",
                manager.isExtensionActive(pkg, expiry.plusNanos(1))
            )

            // --- Randomized instants strictly inside [t0, expiry): active -----------------
            val durationNanos = duration.toNanos()
            repeat(4) {
                val offsetNanos = (random.nextDouble() * durationNanos).toLong()
                    .coerceIn(0L, durationNanos - 1)
                val within = t0.plusNanos(offsetNanos)
                assertTrue(
                    "iteration $iteration: $within in [t0, expiry) must be active",
                    manager.isExtensionActive(pkg, within)
                )
            }

            // --- Randomized instants at or after expiry: inactive -------------------------
            repeat(4) {
                // 0..3600s past expiry (still same calendar day given t0 <= noon + <=15min).
                val beyond = expiry.plusSeconds(random.nextLong(0, 3600))
                assertFalse(
                    "iteration $iteration: $beyond at/after expiry must be inactive",
                    manager.isExtensionActive(pkg, beyond)
                )
            }
        }
    }

    /**
     * Property: a package that was never granted a Focus_Extension is never active, at any instant.
     */
    @Test
    fun `property - never-granted package is never active`() {
        val random = Random(seed = 42)

        repeat(200) { iteration ->
            val t0 = Instant.parse("2024-06-15T00:00:00Z")
                .plusSeconds(random.nextLong(0, 24 * 3600))
            val clock = Clock.fixed(t0, ZoneOffset.UTC)
            val manager = FocusExtensionManager(clock, zone)

            // Grant to a different package to ensure lookups are per-package, not global.
            manager.grantIfEngaged(
                "com.other.app.$iteration",
                engaged = true,
                config = InterceptionConfig(focusExtensionMinutes = random.nextInt(1, 16))
            )

            val ungranted = "com.never.granted.$iteration"
            val queryInstant = t0.plusSeconds(random.nextLong(0, 24 * 3600))
            assertFalse(
                "iteration $iteration: never-granted package must be inactive",
                manager.isExtensionActive(ungranted, queryInstant)
            )
        }
    }

    /**
     * Property: a grant made on a prior calendar day never keeps the app suppressed today, even if
     * the raw expiry instant would still be in the future relative to a "now" that has crossed
     * local midnight. This guards the day-scoping check in [FocusExtensionManager.isExtensionActive].
     */
    @Test
    fun `property - prior-day grant is inactive after local midnight`() {
        val random = Random(seed = 907)

        repeat(150) { iteration ->
            // Grant at ~23:55 IST (June 15) with a 15-minute duration so the raw expiry lands at
            // ~00:10 IST (June 16). A query at ~00:05 IST (June 16) is BEFORE the raw expiry, so the
            // half-open interval alone would say "active" — but the grant is from the prior local
            // day, so isExtensionActive MUST still report false via the day-scoping guard.
            // 23:55 IST == 18:25 UTC.
            val grantInstantUtc = Instant.parse("2024-06-15T18:25:00Z")
                .plusSeconds(random.nextLong(0, 120)) // 23:55..23:57 IST, still June 15 IST
            val clock = Clock.fixed(grantInstantUtc, ZoneOffset.UTC)
            val manager = FocusExtensionManager(clock, zone)
            val pkg = "com.priorday.app.$iteration"

            manager.grantIfEngaged(
                pkg,
                engaged = true,
                config = InterceptionConfig(focusExtensionMinutes = 15)
            )
            val rawExpiry = grantInstantUtc.plus(Duration.ofMinutes(15))

            // Query a few minutes after the grant: next local day (June 16 IST) but BEFORE rawExpiry.
            val nextDayQuery = grantInstantUtc.plus(Duration.ofMinutes(8))
            assertTrue(
                "iteration $iteration: sanity - query must precede the raw expiry",
                nextDayQuery.isBefore(rawExpiry)
            )
            assertFalse(
                "iteration $iteration: prior-day grant must be inactive on the next local day",
                manager.isExtensionActive(pkg, nextDayQuery)
            )
        }
    }
}
