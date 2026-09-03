package com.dopashift.domain.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

/**
 * Unit tests for [InterceptionRule] invariants.
 *
 * Covers:
 * - dailyLimitMinutes bounds: reject < 1 and > 480, accept boundary values 1 and 480 (Req 2.6).
 * - isPausedOn: true when pausedForDate equals today, false otherwise (including null) (Req 3.5).
 * - limitType/repetitiveIntervalMinutes coupling: `Once` must leave the interval null,
 *   `Repetitive` requires an interval in 1..120 inclusive (Req 1.5).
 *
 * **Validates: Requirements 2.6, 3.5, 1.5**
 */
class InterceptionRuleTest {

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val ruleId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val createdAt = Instant.parse("2024-06-15T14:00:00Z")

    private fun ruleWith(
        dailyLimitMinutes: Int,
        pausedForDate: LocalDate? = null,
        limitType: LimitType = LimitType.Once,
        repetitiveIntervalMinutes: Int? = null
    ): InterceptionRule = InterceptionRule(
        id = ruleId,
        userId = userId,
        appPackageName = "com.example.app",
        dailyLimitMinutes = dailyLimitMinutes,
        limitType = limitType,
        repetitiveIntervalMinutes = repetitiveIntervalMinutes,
        enabled = true,
        pausedForDate = pausedForDate,
        createdAt = createdAt
    )

    // --- dailyLimitMinutes bounds (Req 2.6) ---

    @Test
    fun `constructing with daily limit below 1 throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            ruleWith(dailyLimitMinutes = 0)
        }
    }

    @Test
    fun `constructing with negative daily limit throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            ruleWith(dailyLimitMinutes = -1)
        }
    }

    @Test
    fun `constructing with daily limit above 480 throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            ruleWith(dailyLimitMinutes = 481)
        }
    }

    @Test
    fun `constructing with lower boundary limit of 1 succeeds`() {
        val rule = ruleWith(dailyLimitMinutes = 1)
        assertEquals(1, rule.dailyLimitMinutes)
    }

    @Test
    fun `constructing with upper boundary limit of 480 succeeds`() {
        val rule = ruleWith(dailyLimitMinutes = 480)
        assertEquals(480, rule.dailyLimitMinutes)
    }

    @Test
    fun `constructing with a mid-range limit succeeds`() {
        val rule = ruleWith(dailyLimitMinutes = 30)
        assertEquals(30, rule.dailyLimitMinutes)
    }

    /**
     * Property: every whole-minute value in 1..480 is accepted, and every value
     * outside that range is rejected. Randomized over many iterations.
     */
    @Test
    fun `property - daily limit accepted iff within 1 to 480 inclusive`() {
        val random = Random(seed = 2611)

        repeat(500) {
            val candidate = random.nextInt(-500, 1000)
            if (candidate in 1..480) {
                val rule = ruleWith(dailyLimitMinutes = candidate)
                assertEquals(
                    "Value $candidate is in range and should be retained",
                    candidate,
                    rule.dailyLimitMinutes
                )
            } else {
                assertThrows(
                    IllegalArgumentException::class.java
                ) {
                    ruleWith(dailyLimitMinutes = candidate)
                }
            }
        }
    }

    // --- limitType / repetitiveIntervalMinutes coupling (Req 1.5) ---

    @Test
    fun `Once rule with a non-null interval throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            ruleWith(
                dailyLimitMinutes = 30,
                limitType = LimitType.Once,
                repetitiveIntervalMinutes = 5
            )
        }
    }

    @Test
    fun `Once rule with a null interval succeeds`() {
        val rule = ruleWith(
            dailyLimitMinutes = 30,
            limitType = LimitType.Once,
            repetitiveIntervalMinutes = null
        )
        assertEquals(LimitType.Once, rule.limitType)
        assertNull(rule.repetitiveIntervalMinutes)
    }

    @Test
    fun `Repetitive rule with a null interval throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            ruleWith(
                dailyLimitMinutes = 30,
                limitType = LimitType.Repetitive,
                repetitiveIntervalMinutes = null
            )
        }
    }

    @Test
    fun `Repetitive rule with interval below 1 throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            ruleWith(
                dailyLimitMinutes = 30,
                limitType = LimitType.Repetitive,
                repetitiveIntervalMinutes = 0
            )
        }
    }

    @Test
    fun `Repetitive rule with negative interval throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            ruleWith(
                dailyLimitMinutes = 30,
                limitType = LimitType.Repetitive,
                repetitiveIntervalMinutes = -1
            )
        }
    }

    @Test
    fun `Repetitive rule with interval above 120 throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            ruleWith(
                dailyLimitMinutes = 30,
                limitType = LimitType.Repetitive,
                repetitiveIntervalMinutes = 121
            )
        }
    }

    @Test
    fun `Repetitive rule with lower boundary interval of 1 succeeds`() {
        val rule = ruleWith(
            dailyLimitMinutes = 30,
            limitType = LimitType.Repetitive,
            repetitiveIntervalMinutes = 1
        )
        assertEquals(LimitType.Repetitive, rule.limitType)
        assertEquals(1, rule.repetitiveIntervalMinutes)
    }

    @Test
    fun `Repetitive rule with upper boundary interval of 120 succeeds`() {
        val rule = ruleWith(
            dailyLimitMinutes = 30,
            limitType = LimitType.Repetitive,
            repetitiveIntervalMinutes = 120
        )
        assertEquals(LimitType.Repetitive, rule.limitType)
        assertEquals(120, rule.repetitiveIntervalMinutes)
    }

    @Test
    fun `Repetitive rule with a mid-range interval succeeds`() {
        val rule = ruleWith(
            dailyLimitMinutes = 30,
            limitType = LimitType.Repetitive,
            repetitiveIntervalMinutes = 30
        )
        assertEquals(30, rule.repetitiveIntervalMinutes)
    }

    /**
     * Property: for a `Repetitive` Rule, an interval is accepted iff it is a
     * whole-minute value in 1..120 inclusive; any value outside that range (and
     * null) is rejected. Randomized over many iterations.
     */
    @Test
    fun `property - repetitive interval accepted iff within 1 to 120 inclusive`() {
        val random = Random(seed = 4120)

        repeat(500) {
            val candidate = random.nextInt(-200, 400)
            if (candidate in 1..120) {
                val rule = ruleWith(
                    dailyLimitMinutes = 30,
                    limitType = LimitType.Repetitive,
                    repetitiveIntervalMinutes = candidate
                )
                assertEquals(
                    "Interval $candidate is in range and should be retained",
                    candidate,
                    rule.repetitiveIntervalMinutes
                )
            } else {
                assertThrows(
                    IllegalArgumentException::class.java
                ) {
                    ruleWith(
                        dailyLimitMinutes = 30,
                        limitType = LimitType.Repetitive,
                        repetitiveIntervalMinutes = candidate
                    )
                }
            }
        }
    }

    // --- isPausedOn (Req 3.5) ---

    @Test
    fun `isPausedOn returns true when pausedForDate equals today`() {
        val today = LocalDate.of(2024, 6, 15)
        val rule = ruleWith(dailyLimitMinutes = 30, pausedForDate = today)
        assertTrue(rule.isPausedOn(today))
    }

    @Test
    fun `isPausedOn returns false when pausedForDate is a different day`() {
        val pausedDay = LocalDate.of(2024, 6, 15)
        val today = LocalDate.of(2024, 6, 16)
        val rule = ruleWith(dailyLimitMinutes = 30, pausedForDate = pausedDay)
        assertFalse(rule.isPausedOn(today))
    }

    @Test
    fun `isPausedOn returns false when pausedForDate is null`() {
        val today = LocalDate.of(2024, 6, 15)
        val rule = ruleWith(dailyLimitMinutes = 30, pausedForDate = null)
        assertFalse(rule.isPausedOn(today))
    }

    /**
     * Property: for any pause date P and any query date Q, isPausedOn(Q) is true
     * iff P is non-null and P == Q.
     */
    @Test
    fun `property - isPausedOn true iff pausedForDate equals query date`() {
        val random = Random(seed = 305)
        val epochBase = LocalDate.of(2024, 1, 1).toEpochDay()

        repeat(500) {
            val hasPause = random.nextBoolean()
            val pausedForDate = if (hasPause) {
                LocalDate.ofEpochDay(epochBase + random.nextLong(0, 730))
            } else {
                null
            }
            val queryDate = LocalDate.ofEpochDay(epochBase + random.nextLong(0, 730))

            val rule = ruleWith(dailyLimitMinutes = 30, pausedForDate = pausedForDate)

            val expected = pausedForDate != null && pausedForDate == queryDate
            assertEquals(
                "pausedForDate=$pausedForDate queryDate=$queryDate",
                expected,
                rule.isPausedOn(queryDate)
            )
        }
    }
}
