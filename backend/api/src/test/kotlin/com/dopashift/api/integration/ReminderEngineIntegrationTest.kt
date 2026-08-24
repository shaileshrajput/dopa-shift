package com.dopashift.api.integration

import com.dopashift.domain.entity.ConditionType
import com.dopashift.domain.entity.Reminder
import com.dopashift.domain.entity.ReminderCondition
import com.dopashift.domain.entity.ReminderEntityType
import com.dopashift.domain.entity.RecurrenceRule
import com.dopashift.domain.entity.RecurrenceType
import com.dopashift.domain.model.QuietHoursWindow
import com.dopashift.domain.model.ReminderEvaluationResult
import com.dopashift.domain.port.EntityCompletionChecker
import com.dopashift.domain.usecase.reminder.ReminderEvaluationService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Integration test suite: Reminder Engine with Injectable/Fake Clock
 *
 * Requirement 21.7: Fast-forward simulated time, validate all recurrence types,
 * test escalation logic, test quiet-hours suppression and delivery at window end.
 *
 * Uses a fake/fixed clock to simulate multi-day scenarios without real-time waits.
 */
class ReminderEngineIntegrationTest {

    private val userId = UUID.randomUUID()
    private val entityId = UUID.randomUUID()
    private val userZone = ZoneId.of("America/New_York")

    private fun serviceAt(instant: Instant, isEntityComplete: Boolean = false): ReminderEvaluationService {
        val clock = Clock.fixed(instant, userZone)
        val checker = FakeEntityCompletionChecker(isEntityComplete)
        return ReminderEvaluationService(clock, checker)
    }

    private fun serviceAtUtc(instant: Instant, isEntityComplete: Boolean = false): ReminderEvaluationService {
        val clock = Clock.fixed(instant, ZoneOffset.UTC)
        val checker = FakeEntityCompletionChecker(isEntityComplete)
        return ReminderEvaluationService(clock, checker)
    }

    @Nested
    inner class RecurrenceEvaluation {

        @Test
        fun `DAILY recurrence fires every day`() = runBlocking {
            val reminder = createReminder(
                scheduledTime = LocalTime.of(9, 0),
                recurrence = RecurrenceRule(type = RecurrenceType.DAILY)
            )

            // Simulate 7 consecutive days — should fire each day at 09:00 ET
            for (dayOffset in 0L..6L) {
                val instant = clockInstantAt("2024-06-15T13:00:00Z", dayOffset) // 13:00 UTC = 09:00 ET
                val service = serviceAt(instant)
                val result = service.evaluate(reminder, quietHours = null, userZone = userZone)
                assertIs<ReminderEvaluationResult.Fire>(result,
                    "Day $dayOffset: DAILY reminder should fire")
            }
        }

        @Test
        fun `SPECIFIC_WEEKDAYS only fires on configured days`() = runBlocking {
            val reminder = createReminder(
                scheduledTime = LocalTime.of(9, 0),
                recurrence = RecurrenceRule(
                    type = RecurrenceType.SPECIFIC_WEEKDAYS,
                    weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
                )
            )

            // 2024-06-15 is a Saturday — should NOT fire
            // Saturday 09:00 ET = 13:00 UTC
            val satService = serviceAt(Instant.parse("2024-06-15T13:00:00Z"))
            val satResult = satService.evaluate(reminder, quietHours = null, userZone = userZone)
            assertIs<ReminderEvaluationResult.Suppressed>(satResult)

            // 2024-06-17 is a Monday — should fire
            val monService = serviceAt(Instant.parse("2024-06-17T13:00:00Z"))
            val monResult = monService.evaluate(reminder, quietHours = null, userZone = userZone)
            assertIs<ReminderEvaluationResult.Fire>(monResult)
        }

        @Test
        fun `WEEKLY fires once per week on the scheduled day`() = runBlocking {
            val reminder = createReminder(
                scheduledTime = LocalTime.of(9, 0),
                scheduledDate = LocalDate.of(2024, 6, 15), // Saturday
                recurrence = RecurrenceRule(type = RecurrenceType.WEEKLY)
            )

            // Should fire on the next Saturday (June 22) at 09:00 ET = 13:00 UTC
            val nextWeekService = serviceAt(Instant.parse("2024-06-22T13:00:00Z"))
            val nextWeekResult = nextWeekService.evaluate(reminder, quietHours = null, userZone = userZone)
            assertIs<ReminderEvaluationResult.Fire>(nextWeekResult)

            // Should NOT fire on Sunday (June 23)
            val sundayService = serviceAt(Instant.parse("2024-06-23T13:00:00Z"))
            val sundayResult = sundayService.evaluate(reminder, quietHours = null, userZone = userZone)
            assertIs<ReminderEvaluationResult.Suppressed>(sundayResult)
        }

        @Test
        fun `CUSTOM_INTERVAL fires every N days from start`() = runBlocking {
            val reminder = createReminder(
                scheduledTime = LocalTime.of(9, 0),
                scheduledDate = LocalDate.of(2024, 6, 15),
                recurrence = RecurrenceRule(
                    type = RecurrenceType.CUSTOM_INTERVAL,
                    intervalDays = 3
                )
            )

            // Day 0: June 15 — fires (09:00 ET = 13:00 UTC)
            val day0Service = serviceAt(Instant.parse("2024-06-15T13:00:00Z"))
            val day0 = day0Service.evaluate(reminder, null, userZone)
            assertIs<ReminderEvaluationResult.Fire>(day0)

            // Day 1: June 16 — suppressed
            val day1Service = serviceAt(Instant.parse("2024-06-16T13:00:00Z"))
            val day1 = day1Service.evaluate(reminder, null, userZone)
            assertIs<ReminderEvaluationResult.Suppressed>(day1)

            // Day 3: June 18 — fires
            val day3Service = serviceAt(Instant.parse("2024-06-18T13:00:00Z"))
            val day3 = day3Service.evaluate(reminder, null, userZone)
            assertIs<ReminderEvaluationResult.Fire>(day3)
        }
    }

    @Nested
    inner class EscalationLogic {

        @Test
        fun `escalation fires after escalation interval with incremented count`() = runBlocking {
            val reminder = createReminder(
                scheduledTime = LocalTime.of(9, 0),
                escalationInterval = Duration.ofMinutes(15),
                maxEscalations = 3
            )

            // First fire at 13:00 UTC
            val lastFired = Instant.parse("2024-06-15T13:00:00Z")

            // Evaluate escalation 20 minutes later (past 15-min interval)
            val service = serviceAtUtc(Instant.parse("2024-06-15T13:20:00Z"))
            val result = service.evaluateEscalation(
                reminder,
                lastFired,
                quietHours = null,
                userZone = userZone
            )

            assertIs<ReminderEvaluationResult.Escalate>(result)
            assertEquals(1, result.newEscalationCount)
        }

        @Test
        fun `escalation stops at max escalations`() = runBlocking {
            val reminder = createReminder(
                scheduledTime = LocalTime.of(9, 0),
                escalationInterval = Duration.ofMinutes(15),
                maxEscalations = 2,
                escalationCount = 2 // Already at max
            )

            val lastFired = Instant.parse("2024-06-15T13:00:00Z")
            val service = serviceAtUtc(Instant.parse("2024-06-15T13:20:00Z"))
            val result = service.evaluateEscalation(
                reminder,
                lastFired,
                quietHours = null,
                userZone = userZone
            )

            assertIs<ReminderEvaluationResult.Suppressed>(result)
        }

        @Test
        fun `escalation interval not yet elapsed suppresses`() = runBlocking {
            val reminder = createReminder(
                scheduledTime = LocalTime.of(9, 0),
                escalationInterval = Duration.ofMinutes(15),
                maxEscalations = 3
            )

            // Last fired 5 minutes ago — not enough time for escalation
            val now = Instant.parse("2024-06-15T13:05:00Z")
            val lastFired = Instant.parse("2024-06-15T13:00:00Z")
            val service = serviceAtUtc(now)
            val result = service.evaluateEscalation(
                reminder,
                lastFired,
                quietHours = null,
                userZone = userZone
            )

            assertIs<ReminderEvaluationResult.Suppressed>(result)
        }
    }

    @Nested
    inner class QuietHoursIntegration {

        private val quietHours = QuietHoursWindow(
            start = LocalTime.of(22, 0),
            end = LocalTime.of(7, 0)
        )

        @Test
        fun `reminder during quiet hours is delayed to window end`() = runBlocking {
            val reminder = createReminder(
                scheduledTime = LocalTime.of(23, 0) // Within quiet hours
            )

            // 23:00 ET = 03:00 UTC next day
            val service = serviceAt(Instant.parse("2024-06-16T03:00:00Z"))
            val result = service.evaluate(
                reminder,
                quietHours = quietHours,
                userZone = userZone
            )

            assertIs<ReminderEvaluationResult.DelayedToQuietHoursEnd>(result)
            assertEquals(LocalTime.of(7, 0), result.deliveryTime)
        }

        @Test
        fun `reminder outside quiet hours fires normally`() = runBlocking {
            val reminder = createReminder(
                scheduledTime = LocalTime.of(15, 0) // Outside quiet hours
            )

            // 15:00 ET = 19:00 UTC
            val service = serviceAt(Instant.parse("2024-06-15T19:00:00Z"))
            val result = service.evaluate(
                reminder,
                quietHours = quietHours,
                userZone = userZone
            )

            assertIs<ReminderEvaluationResult.Fire>(result)
        }

        @Test
        fun `escalation during quiet hours is delayed to window end`() = runBlocking {
            val reminder = createReminder(
                scheduledTime = LocalTime.of(23, 30),
                escalationInterval = Duration.ofMinutes(15),
                maxEscalations = 3
            )

            // Evaluate at 23:30 ET (well past interval from lastFired)
            // 23:30 ET = 03:30 UTC next day
            val lastFired = Instant.parse("2024-06-15T02:00:00Z") // Well past interval
            val service = serviceAt(Instant.parse("2024-06-16T03:30:00Z"))
            val result = service.evaluateEscalation(
                reminder,
                lastFired,
                quietHours = quietHours,
                userZone = userZone
            )

            assertIs<ReminderEvaluationResult.DelayedToQuietHoursEnd>(result)
            assertEquals(LocalTime.of(7, 0), result.deliveryTime)
        }
    }

    @Nested
    inner class ConditionalReminders {

        @Test
        fun `conditional reminder suppresses when entity is complete`() = runBlocking {
            val reminder = createReminder(
                scheduledTime = LocalTime.of(9, 0),
                condition = ReminderCondition(type = ConditionType.ENTITY_INCOMPLETE)
            )

            // When entity is complete, the reminder should be suppressed
            val service = serviceAt(Instant.parse("2024-06-15T13:00:00Z"), isEntityComplete = true)
            val result = service.evaluate(
                reminder,
                quietHours = null,
                userZone = userZone
            )

            assertIs<ReminderEvaluationResult.Suppressed>(result)
        }

        @Test
        fun `conditional reminder fires when entity is incomplete`() = runBlocking {
            val reminder = createReminder(
                scheduledTime = LocalTime.of(9, 0),
                condition = ReminderCondition(type = ConditionType.ENTITY_INCOMPLETE)
            )

            val service = serviceAt(Instant.parse("2024-06-15T13:00:00Z"), isEntityComplete = false)
            val result = service.evaluate(
                reminder,
                quietHours = null,
                userZone = userZone
            )

            assertIs<ReminderEvaluationResult.Fire>(result)
        }
    }

    // === Helpers ===

    private fun createReminder(
        scheduledTime: LocalTime = LocalTime.of(9, 0),
        scheduledDate: LocalDate? = null,
        recurrence: RecurrenceRule? = null,
        condition: ReminderCondition? = null,
        escalationInterval: Duration = Duration.ofMinutes(30),
        maxEscalations: Int = 3,
        escalationCount: Int = 0
    ): Reminder {
        return Reminder(
            id = UUID.randomUUID(),
            userId = userId,
            entityType = ReminderEntityType.DAILY_TODO,
            entityId = entityId,
            scheduledTime = scheduledTime,
            scheduledDate = scheduledDate,
            recurrence = recurrence,
            condition = condition,
            escalationInterval = escalationInterval,
            maxEscalations = maxEscalations,
            escalationCount = escalationCount,
            isActive = true
        )
    }

    private fun clockInstantAt(baseIso: String, dayOffset: Long = 0): Instant {
        return Instant.parse(baseIso).plus(Duration.ofDays(dayOffset))
    }
}

// === Test Doubles ===

/**
 * Fake EntityCompletionChecker for testing conditional reminder evaluation.
 */
class FakeEntityCompletionChecker(
    private val isComplete: Boolean = false
) : EntityCompletionChecker {
    override suspend fun isComplete(entityType: ReminderEntityType, entityId: UUID): Boolean {
        return isComplete
    }
}
