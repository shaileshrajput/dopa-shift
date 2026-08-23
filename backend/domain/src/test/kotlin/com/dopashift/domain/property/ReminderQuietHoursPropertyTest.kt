package com.dopashift.domain.property

import com.dopashift.domain.entity.RecurrenceRule
import com.dopashift.domain.entity.RecurrenceType
import com.dopashift.domain.entity.Reminder
import com.dopashift.domain.entity.ReminderEntityType
import com.dopashift.domain.model.QuietHoursWindow
import com.dopashift.domain.model.ReminderEvaluationResult
import com.dopashift.domain.port.EntityCompletionChecker
import com.dopashift.domain.usecase.reminder.ReminderEvaluationService
import io.kotest.core.Tag
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Property 11: Reminder Quiet-Hours Suppression and Delivery
 *
 * For any reminder scheduled to fire during user-configured quiet hours,
 * the notification SHALL NOT be delivered during the quiet window, and
 * SHALL be delivered at the end of the quiet-hours window.
 *
 * **Validates: Requirements 5.13**
 *
 * Tag: Feature: dopa-shift, Property 11: Reminder Quiet-Hours Suppression and Delivery
 */
class ReminderQuietHoursPropertyTest : FunSpec({

    tags(
        Tag("Feature: dopa-shift"),
        Tag("Property 11: Reminder Quiet-Hours Suppression and Delivery")
    )

    val userZone = ZoneId.of("UTC")

    // Always-incomplete checker ensures condition evaluation passes (reminder should fire)
    val alwaysIncompleteChecker = object : EntityCompletionChecker {
        override suspend fun isComplete(entityType: ReminderEntityType, entityId: UUID): Boolean = false
    }

    /**
     * Generates a random QuietHoursWindow where start != end (valid window).
     * Both same-day (start < end) and overnight (start > end) windows are included.
     */
    fun arbQuietHoursWindow(): Arb<QuietHoursWindow> =
        io.kotest.property.arbitrary.arbitrary {
            val startMinute = Arb.int(0..(24 * 60 - 1)).bind()
            var endMinute = Arb.int(0..(24 * 60 - 1)).bind()
            // Ensure start != end so the window is non-empty
            while (endMinute == startMinute) {
                endMinute = Arb.int(0..(24 * 60 - 1)).bind()
            }
            QuietHoursWindow(
                start = LocalTime.of(startMinute / 60, startMinute % 60),
                end = LocalTime.of(endMinute / 60, endMinute % 60)
            )
        }

    /**
     * Generates a random time that falls WITHIN the given quiet-hours window.
     */
    fun arbTimeWithinWindow(window: QuietHoursWindow): Arb<LocalTime> = io.kotest.property.arbitrary.arbitrary {
        val startMinute = window.start.hour * 60 + window.start.minute
        val endMinute = window.end.hour * 60 + window.end.minute

        if (startMinute < endMinute) {
            // Same-day window: time in [start, end)
            val minuteOfDay = Arb.int(startMinute until endMinute).bind()
            LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        } else {
            // Overnight window: time in [start, 24:00) or [00:00, end)
            val rangeSize = (24 * 60 - startMinute) + endMinute
            val offset = Arb.int(0 until rangeSize).bind()
            val minuteOfDay = (startMinute + offset) % (24 * 60)
            LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        }
    }

    /**
     * Generates a random time that falls OUTSIDE the given quiet-hours window.
     */
    fun arbTimeOutsideWindow(window: QuietHoursWindow): Arb<LocalTime> = io.kotest.property.arbitrary.arbitrary {
        val startMinute = window.start.hour * 60 + window.start.minute
        val endMinute = window.end.hour * 60 + window.end.minute

        if (startMinute < endMinute) {
            // Same-day window: outside is [end, 24:00) or [00:00, start)
            val rangeSize = (24 * 60 - endMinute) + startMinute
            val offset = Arb.int(0 until rangeSize).bind()
            val minuteOfDay = (endMinute + offset) % (24 * 60)
            LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        } else {
            // Overnight window: outside is [end, start)
            val minuteOfDay = Arb.int(endMinute until startMinute).bind()
            LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        }
    }

    fun buildReminder(): Reminder = Reminder(
        id = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        entityType = ReminderEntityType.DAILY_TODO,
        entityId = UUID.randomUUID(),
        scheduledTime = LocalTime.of(9, 0),
        scheduledDate = null,
        recurrence = RecurrenceRule(RecurrenceType.DAILY),
        condition = null,
        escalationInterval = Duration.ofMinutes(15),
        maxEscalations = 3,
        escalationCount = 0,
        isActive = true
    )

    test("Property 11: Reminder during quiet hours is delayed to window end") {
        checkAll(100, arbQuietHoursWindow()) { window ->
            // Generate a time within the quiet-hours window
            val timeInWindow = arbTimeWithinWindow(window).bind()
            val today = LocalDate.of(2024, 6, 15)
            val dateTime = ZonedDateTime.of(today, timeInWindow, userZone)
            val clock = Clock.fixed(dateTime.toInstant(), userZone)

            val service = ReminderEvaluationService(clock, alwaysIncompleteChecker)
            val reminder = buildReminder()

            val result = service.evaluate(reminder, window, userZone)

            // During quiet hours, result must be DelayedToQuietHoursEnd
            result.shouldBeInstanceOf<ReminderEvaluationResult.DelayedToQuietHoursEnd>()
            // Delivery time must equal the quiet-hours end time
            result.deliveryTime shouldBe window.end
        }
    }

    test("Property 11: Reminder outside quiet hours fires normally") {
        checkAll(100, arbQuietHoursWindow()) { window ->
            // Generate a time outside the quiet-hours window
            val timeOutside = arbTimeOutsideWindow(window).bind()
            val today = LocalDate.of(2024, 6, 15)
            val dateTime = ZonedDateTime.of(today, timeOutside, userZone)
            val clock = Clock.fixed(dateTime.toInstant(), userZone)

            val service = ReminderEvaluationService(clock, alwaysIncompleteChecker)
            val reminder = buildReminder()

            val result = service.evaluate(reminder, window, userZone)

            // Outside quiet hours, the reminder should fire
            result.shouldBeInstanceOf<ReminderEvaluationResult.Fire>()
        }
    }

    test("Property 11: Delivery time always equals the quiet-hours end time (never fires mid-window)") {
        checkAll(100, arbQuietHoursWindow()) { window ->
            val timeInWindow = arbTimeWithinWindow(window).bind()
            val today = LocalDate.of(2024, 6, 15)
            val dateTime = ZonedDateTime.of(today, timeInWindow, userZone)
            val clock = Clock.fixed(dateTime.toInstant(), userZone)

            val service = ReminderEvaluationService(clock, alwaysIncompleteChecker)
            val reminder = buildReminder()

            val result = service.evaluate(reminder, window, userZone)

            result.shouldBeInstanceOf<ReminderEvaluationResult.DelayedToQuietHoursEnd>()
            // The delivery time is precisely the end of the quiet window
            result.deliveryTime shouldBe window.end
            // Ensure the delivery time is NOT within the quiet window
            window.contains(result.deliveryTime) shouldBe false
        }
    }
})
