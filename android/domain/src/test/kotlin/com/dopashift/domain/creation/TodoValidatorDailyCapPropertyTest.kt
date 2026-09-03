package com.dopashift.domain.creation

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.property.Arb
import io.kotest.property.PropertyTesting
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property 4: Daily To-Do Cap Is Never Exceeded.
 *
 * Feature: dashboard-quick-create, Property 4
 * Validates: Requirements 4.6 (also satisfies DQC-7.6 boundary values).
 *
 * For any user on any day, when [TodoValidator.DAILY_CAP] (100) items already exist for
 * that day, a further creation attempt is rejected — so the per-user per-day count can
 * never be pushed beyond 100. Conversely, for a day that still has capacity
 * (todayCount in 0..99) with otherwise valid text, no daily-cap error is raised, so a
 * legitimate creation is allowed.
 *
 * The subject under test is the pure-Kotlin [TodoValidator]. A creation is "allowed to
 * proceed" (and would increment the count) exactly when the validator returns no
 * [TodoValidator.FIELD_DAILY_CAP] error. Since the validator rejects at todayCount == 100
 * and above, the count can never grow past 100.
 */
class TodoValidatorDailyCapPropertyTest : StringSpec({

    // 100+ iterations per property (DQC-7 PBT config).
    PropertyTesting.defaultIterationCount = 200

    /**
     * A non-blank, in-bounds to-do text so text validation never masks the cap behavior.
     * A visible prefix guarantees the text is never blank regardless of the generated body.
     */
    val validText: Arb<String> =
        Arb.string(minSize = 0, maxSize = TodoValidator.TEXT_MAX - 1).map { "x$it" }

    "at or above the daily cap, creation is always rejected with a dailyCap error" {
        // todayCount >= 100 must always produce a dailyCap error, for any valid text.
        checkAll(validText, Arb.int(TodoValidator.DAILY_CAP, TodoValidator.DAILY_CAP + 10_000)) { text, todayCount ->
            val errors = TodoValidator.validate(text = text, todayCount = todayCount)
            errors shouldContainKey TodoValidator.FIELD_DAILY_CAP
        }
    }

    "below the daily cap, valid text has no dailyCap error so creation may proceed" {
        // todayCount in 0..99 must never produce a dailyCap error for valid text.
        checkAll(validText, Arb.int(0, TodoValidator.DAILY_CAP - 1)) { text, todayCount ->
            val errors = TodoValidator.validate(text = text, todayCount = todayCount)
            errors shouldNotContainKey TodoValidator.FIELD_DAILY_CAP
        }
    }

    "the count is never pushed beyond the cap across a simulated day of creations" {
        // Model a day: start at some count, attempt creations; only proceed when the
        // validator allows it. The resulting count must never exceed DAILY_CAP.
        checkAll(Arb.int(0, TodoValidator.DAILY_CAP + 50), Arb.int(0, 500)) { startCount, attempts ->
            var count = startCount.coerceAtMost(TodoValidator.DAILY_CAP)
            repeat(attempts) {
                val errors = TodoValidator.validate(text = "valid task", todayCount = count)
                if (!errors.containsKey(TodoValidator.FIELD_DAILY_CAP)) {
                    count += 1
                }
            }
            (count <= TodoValidator.DAILY_CAP).shouldBeTrue()
        }
    }

    // --- Boundary values (DQC-7.6): 99 / 100 / 101 ---

    "boundary: at 99 existing items, a valid creation is allowed (no dailyCap error)" {
        val errors = TodoValidator.validate(text = "boundary task", todayCount = 99)
        errors shouldNotContainKey TodoValidator.FIELD_DAILY_CAP
    }

    "boundary: at exactly the cap (100) existing items, creation is rejected" {
        val errors = TodoValidator.validate(text = "boundary task", todayCount = 100)
        errors shouldContainKey TodoValidator.FIELD_DAILY_CAP
    }

    "boundary: at 101 existing items (above the cap), creation is rejected" {
        val errors = TodoValidator.validate(text = "boundary task", todayCount = 101)
        errors shouldContainKey TodoValidator.FIELD_DAILY_CAP
    }
})
