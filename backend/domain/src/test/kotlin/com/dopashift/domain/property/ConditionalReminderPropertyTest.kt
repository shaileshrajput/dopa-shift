package com.dopashift.domain.property

import com.dopashift.domain.entity.ConditionType
import com.dopashift.domain.entity.RecurrenceRule
import com.dopashift.domain.entity.RecurrenceType
import com.dopashift.domain.entity.Reminder
import com.dopashift.domain.entity.ReminderCondition
import com.dopashift.domain.entity.ReminderEntityType
import com.dopashift.domain.model.ReminderEvaluationResult
import com.dopashift.domain.model.SuppressionReason
import com.dopashift.domain.port.EntityCompletionChecker
import com.dopashift.domain.usecase.reminder.ReminderEvaluationService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Property 12: Conditional Reminder Fires Only When Condition Holds
 *
 * For any conditional reminder, if the linked entity is completed before the scheduled
 * fire time, the reminder SHALL NOT fire — it SHALL be cancelled (for one-off) or skip
 * the current occurrence (for repeating).
 *
 * **Validates: Requirements 5.4, 5.8**
 *
 * Tag: Feature: dopa-shift, Property 12: Conditional Reminder Fires Only When Condition Holds
 */
class ConditionalReminderPropertyTest : FunSpec({

    tags(
        io.kotest.core.Tag("Feature: dopa-shift"),
        io.kotest.core.Tag("Property 12: Conditional Reminder Fires Only When Condition Holds")
    )

    // Fixed clock at 10:00 UTC — well outside any quiet-hours window.
    val fixedDateTime = ZonedDateTime.of(2024, 6, 15, 10, 0, 0, 0, ZoneId.of("UTC"))
    val fixedClock = Clock.fixed(fixedDateTime.toInstant(), fixedDateTime.zone)
    val userZone = ZoneId.of("UTC")

    // Arbitrary for escalation interval (1 to 60 minutes)
    val arbEscalationMinutes = Arb.int(1..60)

    // Arbitrary for max escalations (1 to 10)
    val arbMaxEscalations = Arb.int(1..10)

    // Arbitrary for entity type
    val arbEntityType = Arb.enum<ReminderEntityType>()

    /**
     * Generates a Reminder with condition = ReminderCondition(ConditionType.ENTITY_INCOMPLETE).
     */
    fun buildConditionalReminder(
        entityType: ReminderEntityType,
        escalationMinutes: Int,
        maxEscalations: Int
    ): Reminder = Reminder(
        id = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        entityType = entityType,
        entityId = UUID.randomUUID(),
        scheduledTime = LocalTime.of(10, 0),
        scheduledDate = LocalDate.of(2024, 6, 15), // today for one-off
        recurrence = null,
        condition = ReminderCondition(ConditionType.ENTITY_INCOMPLETE),
        escalationInterval = Duration.ofMinutes(escalationMinutes.toLong()),
        maxEscalations = maxEscalations,
        escalationCount = 0,
        isActive = true
    )

    /**
     * Generates a Reminder with no condition (condition = null).
     */
    fun buildUnconditionalReminder(
        entityType: ReminderEntityType,
        escalationMinutes: Int,
        maxEscalations: Int
    ): Reminder = Reminder(
        id = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        entityType = entityType,
        entityId = UUID.randomUUID(),
        scheduledTime = LocalTime.of(10, 0),
        scheduledDate = LocalDate.of(2024, 6, 15), // today
        recurrence = null,
        condition = null,
        escalationInterval = Duration.ofMinutes(escalationMinutes.toLong()),
        maxEscalations = maxEscalations,
        escalationCount = 0,
        isActive = true
    )

    fun entityCompleteChecker(): EntityCompletionChecker = object : EntityCompletionChecker {
        override suspend fun isComplete(entityType: ReminderEntityType, entityId: UUID): Boolean = true
    }

    fun entityIncompleteChecker(): EntityCompletionChecker = object : EntityCompletionChecker {
        override suspend fun isComplete(entityType: ReminderEntityType, entityId: UUID): Boolean = false
    }

    test("Property 12: Conditional reminder is suppressed when entity is complete (ENTITY_COMPLETE)") {
        val service = ReminderEvaluationService(fixedClock, entityCompleteChecker())

        checkAll(100, arbEntityType, arbEscalationMinutes, arbMaxEscalations) { entityType, escMin, maxEsc ->
            val reminder = buildConditionalReminder(entityType, escMin, maxEsc)
            val result = service.evaluate(reminder, null, userZone)

            result.shouldBeInstanceOf<ReminderEvaluationResult.Suppressed>()
            result.reason shouldBe SuppressionReason.ENTITY_COMPLETE
        }
    }

    test("Property 12: Conditional reminder fires when entity is incomplete") {
        val service = ReminderEvaluationService(fixedClock, entityIncompleteChecker())

        checkAll(100, arbEntityType, arbEscalationMinutes, arbMaxEscalations) { entityType, escMin, maxEsc ->
            val reminder = buildConditionalReminder(entityType, escMin, maxEsc)
            val result = service.evaluate(reminder, null, userZone)

            result.shouldBeInstanceOf<ReminderEvaluationResult.Fire>()
        }
    }

    test("Property 12: Reminder without condition fires regardless of entity completion state") {
        // Even when the entity IS complete, a reminder without a condition should still fire
        val service = ReminderEvaluationService(fixedClock, entityCompleteChecker())

        checkAll(100, arbEntityType, arbEscalationMinutes, arbMaxEscalations) { entityType, escMin, maxEsc ->
            val reminder = buildUnconditionalReminder(entityType, escMin, maxEsc)
            val result = service.evaluate(reminder, null, userZone)

            result.shouldBeInstanceOf<ReminderEvaluationResult.Fire>()
        }
    }
})
