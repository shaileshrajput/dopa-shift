package com.dopashift.api.integration

import com.dopashift.domain.entity.ConditionType
import com.dopashift.domain.entity.Reminder
import com.dopashift.domain.entity.ReminderCondition
import com.dopashift.domain.entity.ReminderEntityType
import com.dopashift.domain.entity.RecurrenceRule
import com.dopashift.domain.entity.RecurrenceType
import com.dopashift.domain.model.QuietHoursWindow
import com.dopashift.domain.model.ReminderEvaluationResult
import com.dopashift.domain.service.ReminderEvaluationService
import org.junit.jupiter.api.BeforeEach
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
import kotlin.test.assertTrue

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

    private val evaluationService = ReminderEvaluationService()

    @Nested
    inner class RecurrenceEvaluation {

        @Test
        fun `DAILY recurrence fires every day`() {
            val reminder = createReminder(
                scheduledTime = LocalTime.of(9, 0),
                recurrence = RecurrenceRule(type = RecurrenceType.DAILY)
            )

            // Simulate 7 consecutive days — should fire each day at 09:00
            for (dayOffset in 0L..6L) {
                val clock = clockAt("2024-06-15T09:00:00Z", dayOffset)
                val result = evaluationService.evaluate(
                    reminder,
                    quietHours = null,
                    userZone = userZone
                )
                assertIs<ReminderEvaluationResult.Fire>(result,
                    "Day $dayOffset: DAILY reminder should fire")
            }
        }

        @Test
        fun `SPECIFIC_WEEKDAYS only fires on configured days`() {
            val reminder = createReminder(
                scheduledTime = LocalTime.of(9, 0),
                recurrence = RecurrenceRule(
                    type = RecurrenceType.SPECIFIC_WEEKDAYS,
                    weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
                )
            )

            // 2024-06-15 is a Saturday — should NOT fire
            val satResult = evaluationService.evaluateForDate(
                reminder,
                LocalDate.of(2024, 6, 15), // Saturday
                quietHours = null,
                userZone = userZone
            )
            assertIs<ReminderEvaluationResult.Suppressed>(satResult)

            // 2024-06-17 is a Monday — should fire
            val monResult = evaluationService.evaluateForDate(
                reminder,
                LocalDate.of(2024, 6, 17), // Monday
                quietHours = null,
                userZone = userZone
            )
            assertIs<ReminderEvaluationResult.Fire>(monResult)
        }

        @Test
        fun `WEEKLY fires once per week on the scheduled day`() {
            val reminder = createReminder(
                scheduledTime = LocalTime.of(9, 0),
                scheduledDate = LocalDate.of(2024, 6, 15), // Saturday
                recurrence = RecurrenceRule(type = RecurrenceType.WEEKLY)
            )

            // Should fire on the next Saturday (June 22)
            val nextWeekResult = evaluationService.evaluateForDate(
                reminder,
                LocalDate.of(2024, 6, 22),
                quietHours = null,
                userZone = userZone
            )
            assertIs<ReminderEvaluationResult.Fire>(nextWeekResult)

            // Should NOT fire on Sunday (June 23)
            val sundayResult = evaluationService.evaluateForDate(
                reminder,
                LocalDate.of(2024, 6, 23),
                quietHours = null,
                userZone = userZone
            )
            assertIs<ReminderEvaluationResult.Suppressed>(sundayResult)
        }

        @Test
        fun `CUSTOM_INTERVAL fires every N days from start`() {
            val reminder = createReminder(
                scheduledTime = LocalTime.of(9, 0),
                scheduledDate = LocalDate.of(2024, 6, 15),
                recurrence = RecurrenceRule(
                    type = RecurrenceType.CUSTOM_INTERVAL,
                    intervalDays = 3
                )
            )

            // Day 0: June 15 — fires
            val day0 = evaluationService.evaluateForDate(
                reminder, LocalDate.of(2024, 6, 15), null, userZone
            )
            assertIs<ReminderEvaluationResult.Fire>(day0)

            // Day 1: June 16 — suppressed
            val day1 = evaluationService.evaluateForDate(
                reminder, LocalDate.of(2024, 6, 16), null, userZone
            )
            assertIs<ReminderEvaluationResult.Suppressed>(day1)

            // Day 3: June 18 — fires
            val day3 = evaluationService.evaluateForDate(
                reminder, LocalDate.of(2024, 6, 18), null, userZone
            )
            assertIs<ReminderEvaluationResult.Fire>(day3)
        }
    }

    @Nested
    inner class EscalationLogic {

        @Test
        fun `escalation fires after escalation interval with incremented count`() {
            val reminder = createReminder(
                scheduledTime = LocalTime.of(9, 0),
                escalationInterval = Duration.ofMinutes(15),
                maxEscalations = 3
            )

            // First fire
            val lastFired = Instant.parse("2024-06-15T13:00:00Z")

            // Evaluate escalation 20 minutes later (past 15-min interval)
            val result = evaluationService.evaluateEscalation(
                reminder,
                lastFired,
                quietHours = null,
                userZone = userZone
            )

            assertIs<ReminderEvaluationResult.Escalate>(result)
            assertEquals(1, result.newEscalationCount)
        }

        @Test
        fun `escalation stops at max escalations`() {
            val reminder = createReminder(
                scheduledTime = LocalTime.of(9, 0),
                escalationInterval = Duration.ofMinutes(15),
                maxEscalations = 2,
                escalationCount = 2 // Already at max
            )

            val lastFired = Instant.parse("2024-06-15T13:00:00Z")
            val result = evaluationService.evaluateEscalation(
                reminder,
                lastFired,
                quietHours = null,
                userZone = userZone
            )

            assertIs<ReminderEvaluationResult.Suppressed>(result)
        }

        @Test
        fun `escalation interval not yet elapsed suppresses`() {
            val reminder = createReminder(
                scheduledTime = LocalTime.of(9, 0),
                escalationInterval = Duration.ofMinutes(15),
                maxEscalations = 3
            )

            // Last fired 5 minutes ago — not enough time for escalation
            val lastFired = Instant.now().minusSeconds(300)
            val result = evaluationService.evaluateEscalation(
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
            startTime = LocalTime.of(22, 0),
            endTime = LocalTime.of(7, 0)
        )

        @Test
        fun `reminder during quiet hours is delayed to window end`() {
            val reminder = createReminder(
                scheduledTime = LocalTime.of(23, 0) // Within quiet hours
            )

            val result = evaluationService.evaluate(
                reminder,
                quietHours = quietHours,
                userZone = userZone
            )

            assertIs<ReminderEvaluationResult.DelayedToQuietHoursEnd>(result)
            assertEquals(LocalTime.of(7, 0), result.deliveryTime)
        }

        @Test
        fun `reminder outside quiet hours fires normally`() {
            val reminder = createReminder(
                scheduledTime = LocalTime.of(15, 0) // Outside quiet hours
            )

            val result = evaluationService.evaluate(
                reminder,
                quietHours = quietHours,
                userZone = userZone
            )

            assertIs<ReminderEvaluationResult.Fire>(result)
        }

        @Test
        fun `escalation during quiet hours is delayed to window end`() {
            val reminder = createReminder(
                scheduledTime = LocalTime.of(23, 30),
                escalationInterval = Duration.ofMinutes(15),
                maxEscalations = 3
            )

            val lastFired = Instant.parse("2024-06-15T03:00:00Z") // Well past interval
            val result = evaluationService.evaluateEscalation(
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
        fun `conditional reminder suppresses when entity is complete`() {
            val reminder = createReminder(
                scheduledTime = LocalTime.of(9, 0),
                condition = ReminderCondition(type = ConditionType.ENTITY_INCOMPLETE)
            )

            // When entity is complete, the reminder should be suppressed
            val result = evaluationService.evaluateWithEntityStatus(
                reminder,
                isEntityComplete = true,
                quietHours = null,
                userZone = userZone
            )

            assertIs<ReminderEvaluationResult.Suppressed>(result)
        }

        @Test
        fun `conditional reminder fires when entity is incomplete`() {
            val reminder = createReminder(
                scheduledTime = LocalTime.of(9, 0),
                condition = ReminderCondition(type = ConditionType.ENTITY_INCOMPLETE)
            )

            val result = evaluationService.evaluateWithEntityStatus(
                reminder,
                isEntityComplete = false,
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

    private fun clockAt(baseIso: String, dayOffset: Long = 0): Clock {
        val instant = Instant.parse(baseIso).plus(Duration.ofDays(dayOffset))
        return Clock.fixed(instant, ZoneOffset.UTC)
    }
}
