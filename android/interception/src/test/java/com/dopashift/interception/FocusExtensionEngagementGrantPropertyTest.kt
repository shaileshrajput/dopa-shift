package com.dopashift.interception

// Feature: screen-time-interception-engine, Property 15

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

/**
 * Property 15: Focus_Extension granted only on recorded engagement.
 *
 * For any depletion event and any engagement flag, a Focus_Extension is granted **if and only if**
 * at least one action was recorded (and the daily cap is not yet reached), with expiry equal to the
 * depletion (grant) time plus the configured duration. Specifically, on a fresh
 * [FocusExtensionManager] for any package:
 *  - `grantIfEngaged(pkg, engaged = false, config)` ALWAYS returns [GrantResult.NoActionRecorded]
 *    and does NOT increment `extensionsUsedToday` (it stays 0); and
 *  - `grantIfEngaged(pkg, engaged = true, config)` returns [GrantResult.Granted] with
 *    `expiresAt == clock.instant() + focusExtensionMinutes` and increments the count to 1.
 *
 * **Validates: Requirements 4.9**
 *
 * Property-based test following existing module conventions (JUnit4 + randomized iterations via
 * `kotlin.random.Random` and `repeat(N >= 100)`, as in [SamplingIntervalIntegrityPropertyTest] /
 * [FocusExtensionDurationBoundsPropertyTest]); Kotest is not on this module's test classpath, so
 * the established randomized-input style with >=100 iterations is used. A fixed [Clock] and
 * [ZoneId] make the expiry computation deterministic.
 */
class FocusExtensionEngagementGrantPropertyTest {

    /**
     * Property: on a fresh manager, `engaged = false` never grants an extension. It always returns
     * [GrantResult.NoActionRecorded] and leaves `extensionsUsedToday` at 0 (no increment), across
     * randomized packages and configured durations.
     */
    @Test
    fun `property - no engagement never grants and never increments the count`() {
        val random = Random(seed = 1509)

        repeat(200) {
            val fixedInstant = randomInstant(random)
            val zone = randomZone(random)
            val manager = FocusExtensionManager(Clock.fixed(fixedInstant, zone), zone)

            val pkg = randomPackage(random)
            val config = InterceptionConfig(focusExtensionMinutes = random.nextInt(1, 16))

            val result = manager.grantIfEngaged(pkg, engaged = false, config = config)

            assertEquals(
                "engaged=false must yield NoActionRecorded for $pkg",
                GrantResult.NoActionRecorded,
                result
            )
            assertEquals(
                "engaged=false must not increment extensionsUsedToday for $pkg",
                0,
                manager.extensionsUsedToday(pkg)
            )
            assertTrue(
                "engaged=false must leave no active extension for $pkg",
                !manager.isExtensionActive(pkg, fixedInstant)
            )
        }
    }

    /**
     * Property: on a fresh manager, `engaged = true` grants exactly one extension whose expiry is
     * `clock.instant() + focusExtensionMinutes`, increments the count to 1, and leaves the full
     * remaining budget (cap - 1). Randomized over packages and durations.
     */
    @Test
    fun `property - engagement on a fresh app grants with correct expiry and increments the count`() {
        val random = Random(seed = 2509)

        repeat(200) {
            val fixedInstant = randomInstant(random)
            val zone = randomZone(random)
            val manager = FocusExtensionManager(Clock.fixed(fixedInstant, zone), zone)

            val pkg = randomPackage(random)
            val minutes = random.nextInt(1, 16)
            val config = InterceptionConfig(focusExtensionMinutes = minutes)

            // Fresh app: count starts at 0.
            assertEquals(
                "fresh app must start with 0 extensions used for $pkg",
                0,
                manager.extensionsUsedToday(pkg)
            )

            val result = manager.grantIfEngaged(pkg, engaged = true, config = config)

            assertTrue(
                "engaged=true on a fresh app must yield Granted for $pkg (got $result)",
                result is GrantResult.Granted
            )
            val granted = result as GrantResult.Granted

            val expectedExpiry = fixedInstant.plus(Duration.ofMinutes(minutes.toLong()))
            assertEquals(
                "expiry must equal clock.instant() + focusExtensionMinutes ($minutes min) for $pkg",
                expectedExpiry,
                granted.expiresAt
            )
            assertEquals(
                "engaged=true must increment extensionsUsedToday to 1 for $pkg",
                1,
                manager.extensionsUsedToday(pkg)
            )
            assertEquals(
                "remaining must be cap - 1 after the first grant for $pkg",
                FocusExtensionManager.MAX_EXTENSIONS_PER_DAY - 1,
                granted.remaining
            )
            // The just-granted extension is active at the grant instant but not at its expiry
            // (half-open interval).
            assertTrue(
                "granted extension must be active at the grant instant for $pkg",
                manager.isExtensionActive(pkg, fixedInstant)
            )
            assertTrue(
                "granted extension must not be active at its expiry instant for $pkg",
                !manager.isExtensionActive(pkg, expectedExpiry)
            )
        }
    }

    /**
     * Property (biconditional): for the same fresh-app starting state and the same package, a grant
     * happens exactly when the engagement flag is true. Draws the flag randomly and asserts the
     * result type matches the flag, and the count reflects it.
     */
    @Test
    fun `property - grant happens if and only if engaged is true`() {
        val random = Random(seed = 3509)

        repeat(200) {
            val fixedInstant = randomInstant(random)
            val zone = randomZone(random)
            val manager = FocusExtensionManager(Clock.fixed(fixedInstant, zone), zone)

            val pkg = randomPackage(random)
            val config = InterceptionConfig(focusExtensionMinutes = random.nextInt(1, 16))
            val engaged = random.nextBoolean()

            val result = manager.grantIfEngaged(pkg, engaged = engaged, config = config)

            if (engaged) {
                assertTrue(
                    "engaged=true must grant for $pkg (got $result)",
                    result is GrantResult.Granted
                )
                assertEquals(1, manager.extensionsUsedToday(pkg))
            } else {
                assertEquals(GrantResult.NoActionRecorded, result)
                assertEquals(0, manager.extensionsUsedToday(pkg))
            }
        }
    }

    // --- Generators -----------------------------------------------------------------------------

    /** A randomized instant within a broad, realistic range for deterministic expiry checks. */
    private fun randomInstant(random: Random): Instant {
        // Roughly 1970..2100 in epoch seconds, avoiding overflow when adding minutes.
        val epochSecond = random.nextLong(0L, 4_100_000_000L)
        return Instant.ofEpochSecond(epochSecond)
    }

    /** A randomized zone offset so the midnight boundary / today resolution varies across runs. */
    private fun randomZone(random: Random): ZoneId {
        val totalMinutes = random.nextInt(-12 * 60, 14 * 60 + 1)
        return ZoneOffset.ofTotalSeconds(totalMinutes * 60)
    }

    /** A randomized package name resembling an Android application id. */
    private fun randomPackage(random: Random): String {
        val segments = random.nextInt(2, 5)
        return (0 until segments).joinToString(".") {
            val len = random.nextInt(3, 8)
            buildString {
                repeat(len) { append(('a'..'z').random(random)) }
            }
        }
    }
}
