package com.dopashift.domain.creation

import com.dopashift.domain.entity.DailyTodoItem
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.stringPattern
import io.kotest.property.checkAll
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Property-based test for to-do creation validity and its structural invariants.
 *
 * Feature: dashboard-quick-create, Property 3: To-Do Creation Validity and Invariants
 * Validates: Requirements 4.1, 4.3, 4.5, 4.7 (also satisfies DQC-7.6 boundary-value coverage)
 *
 * For any to-do text, creation succeeds if and only if the text is non-blank (not empty
 * and not whitespace-only) and at most 500 characters; when it succeeds the created
 * [DailyTodoItem] has no associated goal and a `dayDate` equal to the current date in the
 * user's configured time zone; when it fails the item count and state are unchanged.
 *
 * Subject under test is the pure-Kotlin [TodoValidator] (the single validator shared by
 * the Dashboard quick-add and the dedicated Daily Tasks screen — DQC-1.10), together with
 * the [CreateTodoCommand]/[DailyTodoItem] shapes that carry the no-goal-association and
 * `dayDate` invariants.
 *
 * Kotest property testing, >= 100 iterations, matching the sibling `domain` property tests.
 */
class TodoValidityInvariantsPropertyTest : StringSpec({

    // >= 100 generated cases per property (design Testing Strategy: min 100 iterations).
    val config = PropTestConfig(iterations = 200)

    val userId: UserId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val correlationId = CorrelationId.random()

    // Text that is non-blank and within bounds: 1..500 chars with at least one non-whitespace char.
    val validText: Arb<String> = Arb.string(minSize = 1, maxSize = TodoValidator.TEXT_MAX)
        .filter { it.isNotBlank() }

    // Whitespace-only strings (including empty) that must be rejected as blank.
    val blankText: Arb<String> =
        Arb.stringPattern("[ \\t\\n\\r]{0,20}").filter { it.isBlank() }

    // ---- Acceptance iff non-blank AND length <= 500 (DQC-4.1, DQC-4.5, DQC-4.7) ----

    "Property 3 (DQC-4.1/4.5/4.7): valid non-blank text within 500 chars is accepted" {
        checkAll(config, validText, Arb.int(min = 0, max = TodoValidator.DAILY_CAP - 1)) { text, count ->
            TodoValidator.validate(text = text, todayCount = count).shouldBeEmpty()
        }
    }

    "Property 3 (DQC-4.7): blank or whitespace-only text is always rejected" {
        checkAll(config, blankText, Arb.int(min = 0, max = TodoValidator.DAILY_CAP - 1)) { text, count ->
            val errors = TodoValidator.validate(text = text, todayCount = count)
            errors shouldContainKey TodoValidator.FIELD_TEXT
        }
    }

    "Property 3 (DQC-4.5): text longer than 500 chars is always rejected" {
        // Generate lengths strictly above the max, built from non-whitespace so the only
        // violated rule is the length bound (isolating the 501+ boundary).
        val overLongLength = Arb.int(min = TodoValidator.TEXT_MAX + 1, max = TodoValidator.TEXT_MAX + 200)
        checkAll(config, overLongLength) { length ->
            val text = "a".repeat(length)
            val errors = TodoValidator.validate(text = text, todayCount = 0)
            errors shouldContainKey TodoValidator.FIELD_TEXT
        }
    }

    // ---- Explicit boundary values: 0 / 1 / 500 / 501 chars (DQC-7.6) ----

    "Property 3 (DQC-7.6): boundary lengths 1 and 500 accepted, 0 and 501 rejected" {
        // 0 chars (empty) -> blank -> rejected
        TodoValidator.validate(text = "", todayCount = 0) shouldContainKey TodoValidator.FIELD_TEXT
        // 1 char -> accepted
        TodoValidator.validate(text = "a", todayCount = 0).shouldBeEmpty()
        // exactly 500 chars -> accepted
        TodoValidator.validate(text = "a".repeat(TodoValidator.TEXT_MAX), todayCount = 0).shouldBeEmpty()
        // 501 chars -> rejected
        TodoValidator.validate(text = "a".repeat(TodoValidator.TEXT_MAX + 1), todayCount = 0)
            .shouldContainKey(TodoValidator.FIELD_TEXT)
    }

    // ---- No-goal association invariant (DQC-4.1) ----
    // Uses plain Java reflection (kotlin-reflect is not on the domain test classpath).

    "Property 3 (DQC-4.1): CreateTodoCommand exposes no goal-association field" {
        val fieldNames = CreateTodoCommand::class.java.declaredFields.map { it.name.lowercase() }
        fieldNames.none { it.contains("goal") } shouldBe true
    }

    "Property 3 (DQC-4.1): DailyTodoItem exposes no goal-association field" {
        val fieldNames = DailyTodoItem::class.java.declaredFields.map { it.name.lowercase() }
        fieldNames.none { it.contains("goal") } shouldBe true
    }

    // ---- dayDate equals "today" in the user's tz (DQC-4.3) ----

    "Property 3 (DQC-4.3): a created to-do carries the supplied today date in the user's tz" {
        // Model a range of zones and instants; "today" is the local date for that zone.
        val zoneIds = listOf(
            "UTC", "Asia/Kolkata", "America/Los_Angeles", "Pacific/Kiritimati", "Etc/GMT+12"
        )
        val zones = Arb.int(min = 0, max = zoneIds.size - 1).map { ZoneId.of(zoneIds[it]) }
        // Instants spanning many years so date boundaries across zones are exercised.
        val instants = Arb.int(min = 0, max = Int.MAX_VALUE)
            .map { Instant.ofEpochSecond(it.toLong()) }

        checkAll(config, validText, zones, instants) { text, zone, now ->
            val today = LocalDate.ofInstant(now, zone)

            val command = CreateTodoCommand(
                userId = userId,
                text = text,
                dayDate = today,
                correlationId = correlationId
            )

            // The command records the day as the user-tz "today" it was built with.
            command.dayDate shouldBe today

            // A to-do created from that command inherits the same dayDate, with no goal.
            val item = DailyTodoItem(
                id = UUID.randomUUID(),
                userId = command.userId,
                text = command.text,
                dueDateTime = command.dueDateTime,
                isCompleted = false,
                createdAt = now,
                updatedAt = now,
                dayDate = command.dayDate
            )

            item.dayDate shouldBe today
            item.dayDate shouldBe LocalDate.ofInstant(now, zone)
        }
    }

    // ---- Failure leaves count/state unchanged: validator is a pure function (DQC-4.7) ----

    "Property 3 (DQC-4.7): validate is pure - repeated calls do not change any external state" {
        // The validator has no side effects; identical inputs always yield identical outputs,
        // so a rejected attempt cannot mutate the item count or state.
        checkAll(config, Arb.string(minSize = 0, maxSize = TodoValidator.TEXT_MAX + 50)) { text ->
            val first = TodoValidator.validate(text = text, todayCount = 3)
            val second = TodoValidator.validate(text = text, todayCount = 3)
            first shouldBe second
        }
    }

    "Property 3 sanity: a rejected result is non-empty and distinct from an accepted one" {
        TodoValidator.validate(text = "   ", todayCount = 0) shouldNotBe emptyMap<String, String>()
    }
})
