package com.dopashift.domain.usecase.reminder

import com.dopashift.domain.entity.ConditionType
import com.dopashift.domain.entity.RecurrenceRule
import com.dopashift.domain.entity.RecurrenceType
import com.dopashift.domain.entity.Reminder
import com.dopashift.domain.entity.ReminderCondition
import com.dopashift.domain.entity.ReminderEntityType
import com.dopashift.domain.model.QuietHoursWindow
import com.dopashift.domain.model.ReminderEvaluationResult
import com.dopashift.domain.model.SuppressionReason
import com.dopashift.domain.port.EntityCompletionChecker
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReminderEvaluationServiceTest {

    private val userZone = ZoneId.of("UTC")

    private fun fixedClock(dateTime: ZonedDateTime): Clock =
        Clock.fixed(dateTime.toInstant(), dateTime.zone)

    private fun buildReminder(
        scheduledTime: LocalTime = LocalTime.of(9, 0),
        scheduledDate: LocalDate? = null,
        recurrence: RecurrenceRule? = null,
        condition: ReminderCondition? = null,
        escalationInterval: Duration = Duration.ofMinutes(15),
        maxEscalations: Int = 3,
        escalationCount: Int = 0,
        isActive: Boolean = true
    ): Reminder = Reminder(
        id = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        entityType = ReminderEntityType.DAILY_TODO,
        entityId = UUID.randomUUID(),
        scheduledTime = scheduledTime,
        scheduledDate = scheduledDate,
        recurrence = recurrence,
        condition = condition,
        escalationInterval = escalationInterval,
        maxEscalations = maxEscalations,
        escalationCount = escalationCount,
        isActive = isActive
    )

    private fun alwaysIncompleteChecker(): EntityCompletionChecker = object : EntityCompletionChecker {
        override suspend fun isComplete(entityType: ReminderEntityType, entityId: UUID): Boolean = false
    }

    private fun alwaysCompleteChecker(): EntityCompletionChecker = object : EntityCompletionChecker {
        override suspend fun isComplete(entityType: ReminderEntityType, entityId: UUID): Boolean = true
    }

    // ==================== Inactive Reminder ====================

    @Test
    fun `inactive reminder is suppressed`() = runTest {
        val clock = fixedClock(ZonedDateTime.of(2024, 6, 15, 9, 0, 0, 0, userZone))
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())

        val reminder = buildReminder(isActive = false)
        val result = service.evaluate(reminder, null, userZone)

        assertIs<ReminderEvaluationResult.Suppressed>(result)
        assertEquals(SuppressionReason.INACTIVE, result.reason)
    }

    // ==================== Scheduled Date ====================

    @Test
    fun `one-off reminder fires on scheduled date`() = runTest {
        val today = LocalDate.of(2024, 6, 15)
        val clock = fixedClock(ZonedDateTime.of(today, LocalTime.of(9, 0), userZone))
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())

        val reminder = buildReminder(scheduledDate = today)
        val result = service.evaluate(reminder, null, userZone)

        assertIs<ReminderEvaluationResult.Fire>(result)
    }

    @Test
    fun `one-off reminder suppressed on wrong date`() = runTest {
        val today = LocalDate.of(2024, 6, 15)
        val scheduledDate = LocalDate.of(2024, 6, 16)
        val clock = fixedClock(ZonedDateTime.of(today, LocalTime.of(9, 0), userZone))
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())

        val reminder = buildReminder(scheduledDate = scheduledDate)
        val result = service.evaluate(reminder, null, userZone)

        assertIs<ReminderEvaluationResult.Suppressed>(result)
        assertEquals(SuppressionReason.SCHEDULED_DATE_NOT_TODAY, result.reason)
    }

    // ==================== Recurrence: DAILY ====================

    @Test
    fun `daily recurrence fires every day`() = runTest {
        val clock = fixedClock(ZonedDateTime.of(2024, 6, 15, 9, 0, 0, 0, userZone))
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())

        val reminder = buildReminder(recurrence = RecurrenceRule(RecurrenceType.DAILY))
        val result = service.evaluate(reminder, null, userZone)

        assertIs<ReminderEvaluationResult.Fire>(result)
    }

    // ==================== Recurrence: SPECIFIC_WEEKDAYS ====================

    @Test
    fun `specific weekdays recurrence fires on matching day`() = runTest {
        // June 17, 2024 is a Monday
        val monday = LocalDate.of(2024, 6, 17)
        val clock = fixedClock(ZonedDateTime.of(monday, LocalTime.of(9, 0), userZone))
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())

        val reminder = buildReminder(
            recurrence = RecurrenceRule(
                RecurrenceType.SPECIFIC_WEEKDAYS,
                weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
            )
        )
        val result = service.evaluate(reminder, null, userZone)

        assertIs<ReminderEvaluationResult.Fire>(result)
    }

    @Test
    fun `specific weekdays recurrence suppressed on non-matching day`() = runTest {
        // June 18, 2024 is a Tuesday
        val tuesday = LocalDate.of(2024, 6, 18)
        val clock = fixedClock(ZonedDateTime.of(tuesday, LocalTime.of(9, 0), userZone))
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())

        val reminder = buildReminder(
            recurrence = RecurrenceRule(
                RecurrenceType.SPECIFIC_WEEKDAYS,
                weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
            )
        )
        val result = service.evaluate(reminder, null, userZone)

        assertIs<ReminderEvaluationResult.Suppressed>(result)
        assertEquals(SuppressionReason.RECURRENCE_NOT_MATCHED, result.reason)
    }

    @Test
    fun `specific weekdays recurrence suppressed when weekdays is null`() = runTest {
        val clock = fixedClock(ZonedDateTime.of(2024, 6, 17, 9, 0, 0, 0, userZone))
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())

        val reminder = buildReminder(
            recurrence = RecurrenceRule(RecurrenceType.SPECIFIC_WEEKDAYS, weekdays = null)
        )
        val result = service.evaluate(reminder, null, userZone)

        assertIs<ReminderEvaluationResult.Suppressed>(result)
    }

    // ==================== Recurrence: WEEKLY ====================

    @Test
    fun `weekly recurrence fires on same day of week as anchor`() = runTest {
        // Anchor: June 15, 2024 (Saturday). Today: June 22, 2024 (also Saturday)
        val anchor = LocalDate.of(2024, 6, 15)
        val today = LocalDate.of(2024, 6, 22)
        val clock = fixedClock(ZonedDateTime.of(today, LocalTime.of(9, 0), userZone))
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())

        val reminder = buildReminder(
            scheduledDate = anchor,
            recurrence = RecurrenceRule(RecurrenceType.WEEKLY)
        )
        val result = service.evaluate(reminder, null, userZone)

        assertIs<ReminderEvaluationResult.Fire>(result)
    }

    @Test
    fun `weekly recurrence suppressed on different day of week`() = runTest {
        // Anchor: June 15, 2024 (Saturday). Today: June 19, 2024 (Wednesday)
        val anchor = LocalDate.of(2024, 6, 15)
        val today = LocalDate.of(2024, 6, 19)
        val clock = fixedClock(ZonedDateTime.of(today, LocalTime.of(9, 0), userZone))
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())

        val reminder = buildReminder(
            scheduledDate = anchor,
            recurrence = RecurrenceRule(RecurrenceType.WEEKLY)
        )
        val result = service.evaluate(reminder, null, userZone)

        assertIs<ReminderEvaluationResult.Suppressed>(result)
    }

    // ==================== Recurrence: MONTHLY ====================

    @Test
    fun `monthly recurrence fires on same day of month`() = runTest {
        // Anchor: June 15. Today: July 15
        val anchor = LocalDate.of(2024, 6, 15)
        val today = LocalDate.of(2024, 7, 15)
        val clock = fixedClock(ZonedDateTime.of(today, LocalTime.of(9, 0), userZone))
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())

        val reminder = buildReminder(
            scheduledDate = anchor,
            recurrence = RecurrenceRule(RecurrenceType.MONTHLY)
        )
        val result = service.evaluate(reminder, null, userZone)

        assertIs<ReminderEvaluationResult.Fire>(result)
    }

    @Test
    fun `monthly recurrence suppressed on different day of month`() = runTest {
        val anchor = LocalDate.of(2024, 6, 15)
        val today = LocalDate.of(2024, 7, 16)
        val clock = fixedClock(ZonedDateTime.of(today, LocalTime.of(9, 0), userZone))
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())

        val reminder = buildReminder(
            scheduledDate = anchor,
            recurrence = RecurrenceRule(RecurrenceType.MONTHLY)
        )
        val result = service.evaluate(reminder, null, userZone)

        assertIs<ReminderEvaluationResult.Suppressed>(result)
    }

    // ==================== Recurrence: CUSTOM_INTERVAL ====================

    @Test
    fun `custom interval fires on exact interval day`() = runTest {
        // Anchor: June 10. Interval: 5 days. Today: June 15 (5 days later)
        val anchor = LocalDate.of(2024, 6, 10)
        val today = LocalDate.of(2024, 6, 15)
        val clock = fixedClock(ZonedDateTime.of(today, LocalTime.of(9, 0), userZone))
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())

        val reminder = buildReminder(
            scheduledDate = anchor,
            recurrence = RecurrenceRule(RecurrenceType.CUSTOM_INTERVAL, intervalDays = 5)
        )
        val result = service.evaluate(reminder, null, userZone)

        assertIs<ReminderEvaluationResult.Fire>(result)
    }

    @Test
    fun `custom interval fires on anchor day itself`() = runTest {
        val anchor = LocalDate.of(2024, 6, 10)
        val clock = fixedClock(ZonedDateTime.of(anchor, LocalTime.of(9, 0), userZone))
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())

        val reminder = buildReminder(
            scheduledDate = anchor,
            recurrence = RecurrenceRule(RecurrenceType.CUSTOM_INTERVAL, intervalDays = 7)
        )
        val result = service.evaluate(reminder, null, userZone)

        assertIs<ReminderEvaluationResult.Fire>(result)
    }

    @Test
    fun `custom interval suppressed on non-interval day`() = runTest {
        // Anchor: June 10. Interval: 5 days. Today: June 13 (3 days later, not divisible)
        val anchor = LocalDate.of(2024, 6, 10)
        val today = LocalDate.of(2024, 6, 13)
        val clock = fixedClock(ZonedDateTime.of(today, LocalTime.of(9, 0), userZone))
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())

        val reminder = buildReminder(
            scheduledDate = anchor,
            recurrence = RecurrenceRule(RecurrenceType.CUSTOM_INTERVAL, intervalDays = 5)
        )
        val result = service.evaluate(reminder, null, userZone)

        assertIs<ReminderEvaluationResult.Suppressed>(result)
    }

    @Test
    fun `custom interval suppressed before anchor date`() = runTest {
        val anchor = LocalDate.of(2024, 6, 15)
        val today = LocalDate.of(2024, 6, 10) // before anchor
        val clock = fixedClock(ZonedDateTime.of(today, LocalTime.of(9, 0), userZone))
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())

        val reminder = buildReminder(
            scheduledDate = anchor,
            recurrence = RecurrenceRule(RecurrenceType.CUSTOM_INTERVAL, intervalDays = 3)
        )
        val result = service.evaluate(reminder, null, userZone)

        assertIs<ReminderEvaluationResult.Suppressed>(result)
    }

    @Test
    fun `custom interval suppressed when intervalDays is null`() = runTest {
        val anchor = LocalDate.of(2024, 6, 10)
        val clock = fixedClock(ZonedDateTime.of(anchor, LocalTime.of(9, 0), userZone))
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())

        val reminder = buildReminder(
            scheduledDate = anchor,
            recurrence = RecurrenceRule(RecurrenceType.CUSTOM_INTERVAL, intervalDays = null)
        )
        val result = service.evaluate(reminder, null, userZone)

        assertIs<ReminderEvaluationResult.Suppressed>(result)
    }

    // ==================== Condition Evaluation ====================

    @Test
    fun `conditional reminder fires when entity is incomplete`() = runTest {
        val clock = fixedClock(ZonedDateTime.of(2024, 6, 15, 9, 0, 0, 0, userZone))
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())

        val reminder = buildReminder(
            condition = ReminderCondition(ConditionType.ENTITY_INCOMPLETE)
        )
        val result = service.evaluate(reminder, null, userZone)

        assertIs<ReminderEvaluationResult.Fire>(result)
    }

    @Test
    fun `conditional reminder suppressed when entity is complete`() = runTest {
        val clock = fixedClock(ZonedDateTime.of(2024, 6, 15, 9, 0, 0, 0, userZone))
        val service = ReminderEvaluationService(clock, alwaysCompleteChecker())

        val reminder = buildReminder(
            condition = ReminderCondition(ConditionType.ENTITY_INCOMPLETE)
        )
        val result = service.evaluate(reminder, null, userZone)

        assertIs<ReminderEvaluationResult.Suppressed>(result)
        assertEquals(SuppressionReason.ENTITY_COMPLETE, result.reason)
    }

    @Test
    fun `reminder without condition fires regardless of entity state`() = runTest {
        val clock = fixedClock(ZonedDateTime.of(2024, 6, 15, 9, 0, 0, 0, userZone))
        val service = ReminderEvaluationService(clock, alwaysCompleteChecker())

        val reminder = buildReminder(condition = null)
        val result = service.evaluate(reminder, null, userZone)

        assertIs<ReminderEvaluationResult.Fire>(result)
    }

    // ==================== Quiet Hours ====================

    @Test
    fun `reminder suppressed during quiet hours with delivery at window end`() = runTest {
        // Current time: 23:00, Quiet hours: 22:00 - 07:00
        val clock = fixedClock(ZonedDateTime.of(2024, 6, 15, 23, 0, 0, 0, userZone))
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())
        val quietHours = QuietHoursWindow(LocalTime.of(22, 0), LocalTime.of(7, 0))

        val reminder = buildReminder()
        val result = service.evaluate(reminder, quietHours, userZone)

        assertIs<ReminderEvaluationResult.DelayedToQuietHoursEnd>(result)
        assertEquals(LocalTime.of(7, 0), result.deliveryTime)
    }

    @Test
    fun `reminder fires outside quiet hours`() = runTest {
        // Current time: 09:00, Quiet hours: 22:00 - 07:00
        val clock = fixedClock(ZonedDateTime.of(2024, 6, 15, 9, 0, 0, 0, userZone))
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())
        val quietHours = QuietHoursWindow(LocalTime.of(22, 0), LocalTime.of(7, 0))

        val reminder = buildReminder()
        val result = service.evaluate(reminder, quietHours, userZone)

        assertIs<ReminderEvaluationResult.Fire>(result)
    }

    @Test
    fun `reminder suppressed during same-day quiet hours`() = runTest {
        // Current time: 14:00, Quiet hours: 13:00 - 15:00
        val clock = fixedClock(ZonedDateTime.of(2024, 6, 15, 14, 0, 0, 0, userZone))
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())
        val quietHours = QuietHoursWindow(LocalTime.of(13, 0), LocalTime.of(15, 0))

        val reminder = buildReminder()
        val result = service.evaluate(reminder, quietHours, userZone)

        assertIs<ReminderEvaluationResult.DelayedToQuietHoursEnd>(result)
        assertEquals(LocalTime.of(15, 0), result.deliveryTime)
    }

    @Test
    fun `reminder fires when no quiet hours configured`() = runTest {
        val clock = fixedClock(ZonedDateTime.of(2024, 6, 15, 23, 0, 0, 0, userZone))
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())

        val reminder = buildReminder()
        val result = service.evaluate(reminder, null, userZone)

        assertIs<ReminderEvaluationResult.Fire>(result)
    }

    // ==================== Escalation ====================

    @Test
    fun `escalation fires after interval elapsed`() = runTest {
        val lastFired = Instant.parse("2024-06-15T09:00:00Z")
        // Now is 16 minutes after last fired (interval is 15 min)
        val now = Instant.parse("2024-06-15T09:16:00Z")
        val clock = Clock.fixed(now, userZone)
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())

        val reminder = buildReminder(
            escalationInterval = Duration.ofMinutes(15),
            maxEscalations = 3,
            escalationCount = 0
        )
        val result = service.evaluateEscalation(reminder, lastFired, null, userZone)

        assertIs<ReminderEvaluationResult.Escalate>(result)
        assertEquals(1, result.newEscalationCount)
    }

    @Test
    fun `escalation suppressed before interval elapsed`() = runTest {
        val lastFired = Instant.parse("2024-06-15T09:00:00Z")
        // Now is only 10 minutes after last fired (interval is 15 min)
        val now = Instant.parse("2024-06-15T09:10:00Z")
        val clock = Clock.fixed(now, userZone)
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())

        val reminder = buildReminder(
            escalationInterval = Duration.ofMinutes(15),
            maxEscalations = 3,
            escalationCount = 0
        )
        val result = service.evaluateEscalation(reminder, lastFired, null, userZone)

        assertIs<ReminderEvaluationResult.Suppressed>(result)
    }

    @Test
    fun `escalation suppressed when max escalations reached`() = runTest {
        val lastFired = Instant.parse("2024-06-15T09:00:00Z")
        val now = Instant.parse("2024-06-15T10:00:00Z")
        val clock = Clock.fixed(now, userZone)
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())

        val reminder = buildReminder(
            escalationInterval = Duration.ofMinutes(15),
            maxEscalations = 3,
            escalationCount = 3
        )
        val result = service.evaluateEscalation(reminder, lastFired, null, userZone)

        assertIs<ReminderEvaluationResult.Suppressed>(result)
        assertEquals(SuppressionReason.MAX_ESCALATIONS_REACHED, result.reason)
    }

    @Test
    fun `escalation suppressed when entity becomes complete`() = runTest {
        val lastFired = Instant.parse("2024-06-15T09:00:00Z")
        val now = Instant.parse("2024-06-15T09:20:00Z")
        val clock = Clock.fixed(now, userZone)
        val service = ReminderEvaluationService(clock, alwaysCompleteChecker())

        val reminder = buildReminder(
            condition = ReminderCondition(ConditionType.ENTITY_INCOMPLETE),
            escalationInterval = Duration.ofMinutes(15),
            maxEscalations = 3,
            escalationCount = 0
        )
        val result = service.evaluateEscalation(reminder, lastFired, null, userZone)

        assertIs<ReminderEvaluationResult.Suppressed>(result)
        assertEquals(SuppressionReason.ENTITY_COMPLETE, result.reason)
    }

    @Test
    fun `escalation delayed during quiet hours`() = runTest {
        val lastFired = Instant.parse("2024-06-15T22:00:00Z")
        val now = Instant.parse("2024-06-15T22:30:00Z") // 30 min later, within quiet hours
        val clock = Clock.fixed(now, userZone)
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())
        val quietHours = QuietHoursWindow(LocalTime.of(22, 0), LocalTime.of(7, 0))

        val reminder = buildReminder(
            escalationInterval = Duration.ofMinutes(15),
            maxEscalations = 3,
            escalationCount = 0
        )
        val result = service.evaluateEscalation(reminder, lastFired, quietHours, userZone)

        assertIs<ReminderEvaluationResult.DelayedToQuietHoursEnd>(result)
        assertEquals(LocalTime.of(7, 0), result.deliveryTime)
    }

    @Test
    fun `successive escalations increment count`() = runTest {
        val lastFired = Instant.parse("2024-06-15T09:00:00Z")
        val now = Instant.parse("2024-06-15T09:20:00Z")
        val clock = Clock.fixed(now, userZone)
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())

        val reminder = buildReminder(
            escalationInterval = Duration.ofMinutes(15),
            maxEscalations = 5,
            escalationCount = 2
        )
        val result = service.evaluateEscalation(reminder, lastFired, null, userZone)

        assertIs<ReminderEvaluationResult.Escalate>(result)
        assertEquals(3, result.newEscalationCount)
    }

    // ==================== matchesRecurrence direct tests ====================

    @Test
    fun `matchesRecurrence WEEKLY returns false when scheduledDate is null`() {
        val clock = fixedClock(ZonedDateTime.of(2024, 6, 15, 9, 0, 0, 0, userZone))
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())

        val result = service.matchesRecurrence(
            RecurrenceRule(RecurrenceType.WEEKLY),
            LocalDate.of(2024, 6, 15),
            null
        )
        assertFalse(result)
    }

    @Test
    fun `matchesRecurrence MONTHLY returns false when scheduledDate is null`() {
        val clock = fixedClock(ZonedDateTime.of(2024, 6, 15, 9, 0, 0, 0, userZone))
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())

        val result = service.matchesRecurrence(
            RecurrenceRule(RecurrenceType.MONTHLY),
            LocalDate.of(2024, 6, 15),
            null
        )
        assertFalse(result)
    }

    @Test
    fun `matchesRecurrence CUSTOM_INTERVAL returns false when scheduledDate is null`() {
        val clock = fixedClock(ZonedDateTime.of(2024, 6, 15, 9, 0, 0, 0, userZone))
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())

        val result = service.matchesRecurrence(
            RecurrenceRule(RecurrenceType.CUSTOM_INTERVAL, intervalDays = 5),
            LocalDate.of(2024, 6, 15),
            null
        )
        assertFalse(result)
    }

    // ==================== Combined scenarios ====================

    @Test
    fun `recurrence + condition combined - fires when both match`() = runTest {
        // Monday + entity incomplete
        val monday = LocalDate.of(2024, 6, 17)
        val clock = fixedClock(ZonedDateTime.of(monday, LocalTime.of(9, 0), userZone))
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())

        val reminder = buildReminder(
            recurrence = RecurrenceRule(
                RecurrenceType.SPECIFIC_WEEKDAYS,
                weekdays = setOf(DayOfWeek.MONDAY)
            ),
            condition = ReminderCondition(ConditionType.ENTITY_INCOMPLETE)
        )
        val result = service.evaluate(reminder, null, userZone)

        assertIs<ReminderEvaluationResult.Fire>(result)
    }

    @Test
    fun `recurrence matches but condition fails - suppressed`() = runTest {
        val monday = LocalDate.of(2024, 6, 17)
        val clock = fixedClock(ZonedDateTime.of(monday, LocalTime.of(9, 0), userZone))
        val service = ReminderEvaluationService(clock, alwaysCompleteChecker())

        val reminder = buildReminder(
            recurrence = RecurrenceRule(
                RecurrenceType.SPECIFIC_WEEKDAYS,
                weekdays = setOf(DayOfWeek.MONDAY)
            ),
            condition = ReminderCondition(ConditionType.ENTITY_INCOMPLETE)
        )
        val result = service.evaluate(reminder, null, userZone)

        assertIs<ReminderEvaluationResult.Suppressed>(result)
        assertEquals(SuppressionReason.ENTITY_COMPLETE, result.reason)
    }

    @Test
    fun `all conditions met but in quiet hours - delayed`() = runTest {
        val monday = LocalDate.of(2024, 6, 17)
        val clock = fixedClock(ZonedDateTime.of(monday, LocalTime.of(23, 30), userZone))
        val service = ReminderEvaluationService(clock, alwaysIncompleteChecker())
        val quietHours = QuietHoursWindow(LocalTime.of(22, 0), LocalTime.of(7, 0))

        val reminder = buildReminder(
            recurrence = RecurrenceRule(
                RecurrenceType.SPECIFIC_WEEKDAYS,
                weekdays = setOf(DayOfWeek.MONDAY)
            ),
            condition = ReminderCondition(ConditionType.ENTITY_INCOMPLETE)
        )
        val result = service.evaluate(reminder, quietHours, userZone)

        assertIs<ReminderEvaluationResult.DelayedToQuietHoursEnd>(result)
        assertEquals(LocalTime.of(7, 0), result.deliveryTime)
    }

    // ==================== QuietHoursWindow ====================

    @Test
    fun `QuietHoursWindow overnight - contains time after start`() {
        val window = QuietHoursWindow(LocalTime.of(22, 0), LocalTime.of(7, 0))
        assertTrue(window.contains(LocalTime.of(23, 0)))
        assertTrue(window.contains(LocalTime.of(22, 0)))
        assertTrue(window.contains(LocalTime.of(0, 0)))
        assertTrue(window.contains(LocalTime.of(6, 59)))
    }

    @Test
    fun `QuietHoursWindow overnight - does not contain time outside window`() {
        val window = QuietHoursWindow(LocalTime.of(22, 0), LocalTime.of(7, 0))
        assertFalse(window.contains(LocalTime.of(7, 0)))
        assertFalse(window.contains(LocalTime.of(12, 0)))
        assertFalse(window.contains(LocalTime.of(21, 59)))
    }

    @Test
    fun `QuietHoursWindow same-day - contains time within range`() {
        val window = QuietHoursWindow(LocalTime.of(13, 0), LocalTime.of(15, 0))
        assertTrue(window.contains(LocalTime.of(13, 0)))
        assertTrue(window.contains(LocalTime.of(14, 0)))
        assertTrue(window.contains(LocalTime.of(14, 59)))
    }

    @Test
    fun `QuietHoursWindow same-day - does not contain time outside range`() {
        val window = QuietHoursWindow(LocalTime.of(13, 0), LocalTime.of(15, 0))
        assertFalse(window.contains(LocalTime.of(12, 59)))
        assertFalse(window.contains(LocalTime.of(15, 0)))
        assertFalse(window.contains(LocalTime.of(16, 0)))
    }
}
