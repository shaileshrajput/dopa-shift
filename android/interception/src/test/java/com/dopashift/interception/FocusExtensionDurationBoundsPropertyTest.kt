package com.dopashift.interception

// Feature: screen-time-interception-engine, Property 16

import com.dopashift.interception.InterceptionConfig.Companion.MAX_FOCUS_EXTENSION_MINUTES
import com.dopashift.interception.InterceptionConfig.Companion.MIN_FOCUS_EXTENSION_MINUTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Property 16: Focus_Extension duration bounds.
 *
 * The configurable Focus_Extension duration is constrained to the inclusive range
 * [MIN_FOCUS_EXTENSION_MINUTES, MAX_FOCUS_EXTENSION_MINUTES] (1..15 minutes).
 * Constructing an [InterceptionConfig] with `focusExtensionMinutes = m`:
 *  - SUCCEEDS for every `m` inside `1..15`, and the constructed config retains that value; and
 *  - THROWS [IllegalArgumentException] for every `m` outside `1..15`.
 *
 * **Validates: Requirements 4.10**
 *
 * Property-based test following existing module conventions (JUnit4 + randomized iterations
 * via `kotlin.random.Random` and `repeat(N)`, as in [SuppressionContextPropertyTest] /
 * [MidnightAllowanceResetPropertyTest]); Kotest is not on this module's test classpath, so the
 * established randomized-input style with >=100 iterations is used.
 */
class FocusExtensionDurationBoundsPropertyTest {

    /**
     * Property: every whole-minute duration inside the valid range constructs successfully and
     * the resulting config reports back exactly the requested duration. Exhaustively covers the
     * full 1..15 range each iteration, repeated well past the 100-iteration convention.
     */
    @Test
    fun `property - in-range durations construct successfully and are retained`() {
        repeat(160) {
            for (m in MIN_FOCUS_EXTENSION_MINUTES..MAX_FOCUS_EXTENSION_MINUTES) {
                val config = InterceptionConfig(focusExtensionMinutes = m)
                assertEquals(
                    "focusExtensionMinutes=$m in range should be retained",
                    m,
                    config.focusExtensionMinutes
                )
            }
        }
    }

    /**
     * Property: any duration below the minimum or above the maximum is rejected with an
     * [IllegalArgumentException]. Draws randomized out-of-range values on both sides of the
     * boundary across >=100 iterations, and also pins the exact boundary-adjacent values.
     */
    @Test
    fun `property - out-of-range durations throw IllegalArgumentException`() {
        val random = Random(seed = 416)

        // Boundary-adjacent values that must always be rejected.
        assertThrows(IllegalArgumentException::class.java) {
            InterceptionConfig(focusExtensionMinutes = MIN_FOCUS_EXTENSION_MINUTES - 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InterceptionConfig(focusExtensionMinutes = MAX_FOCUS_EXTENSION_MINUTES + 1)
        }

        repeat(200) {
            // Half the iterations probe below the minimum, half above the maximum.
            val m = if (random.nextBoolean()) {
                // Below MIN: (..., MIN-1], includes negatives and zero.
                MIN_FOCUS_EXTENSION_MINUTES - 1 - random.nextInt(0, 1_000)
            } else {
                // Above MAX: [MAX+1, ...).
                MAX_FOCUS_EXTENSION_MINUTES + 1 + random.nextInt(0, 1_000)
            }

            assertTrue(
                "test generated an in-range value ($m); expected out-of-range",
                m < MIN_FOCUS_EXTENSION_MINUTES || m > MAX_FOCUS_EXTENSION_MINUTES
            )
            assertThrows(
                "focusExtensionMinutes=$m out of range should throw IllegalArgumentException",
                IllegalArgumentException::class.java
            ) {
                InterceptionConfig(focusExtensionMinutes = m)
            }
        }
    }
}
