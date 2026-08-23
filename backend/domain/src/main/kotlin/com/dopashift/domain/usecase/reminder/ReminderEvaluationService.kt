package com.dopashift.domain.usecase.reminder

import com.dopashift.domain.entity.ConditionType
import com.dopashift.domain.entity.RecurrenceRule
import com.dopashift.domain.entity.RecurrenceType
import com.dopashift.domain.entity.Reminder
import com.dopashift.domain.model.QuietHoursWindow
import com.dopashift.domain.model.ReminderEvaluationResult
import com.dopashift.domain.model.SuppressionReason
import com.dopashift.domain.port.EntityCompletionChecker
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Pure domain service that evaluates whether a reminder should fire at a given point in time.
 *
 * Responsibilities:
 * 1. Schedule evaluation — determine if the current time matches the reminder's scheduled time
 * 2. Recurrence evaluation — apply RecurrenceRule to determine if today matches
 * 3. Condition evaluation — check if the entity is still incomplete (via EntityCompletionChecker)
 * 4. Escalation logic — if reminder was not acknowledged within escalationInterval, escalate
 * 5. Quiet-hours suppression — suppress during quiet hours and deliver at window end
 *
 * Uses an injectable [Clock] for testability.
 */
class ReminderEvaluationService(
    private val clock: Clock,
    private val entityCompletionChecker: EntityCompletionChecker
) {

    /**
     * Evaluates whether the given [reminder] should fire right now.
     *
     * @param reminder The reminder to evaluate.
     * @param quietHours The user's quiet-hours window, or null if not configured.
     * @param userZone The user's timezone for date/time calculations.
     * @return A [ReminderEvaluationResult] indicating whether to fire, suppress, delay, or escalate.
     */
    suspend fun evaluate(
        reminder: Reminder,
        quietHours: QuietHoursWindow?,
        userZone: ZoneId
    ): ReminderEvaluationResult {
        // 1. Check if reminder is active
        if (!reminder.isActive) {
            return ReminderEvaluationResult.Suppressed(SuppressionReason.INACTIVE)
        }

        val now = Instant.now(clock)
        val today = now.atZone(userZone).toLocalDate()
        val currentTime = now.atZone(userZone).toLocalTime()

        // 2. Evaluate scheduling / recurrence
        if (reminder.recurrence != null) {
            // Repeating reminder — scheduledDate is the anchor, not a one-off target date.
            // Use recurrence rule to determine if today is a match.
            if (!matchesRecurrence(reminder.recurrence, today, reminder.scheduledDate)) {
                return ReminderEvaluationResult.Suppressed(SuppressionReason.RECURRENCE_NOT_MATCHED)
            }
        } else if (reminder.scheduledDate != null && reminder.scheduledDate != today) {
            // One-off reminder — only fires on the exact scheduled date
            return ReminderEvaluationResult.Suppressed(SuppressionReason.SCHEDULED_DATE_NOT_TODAY)
        }

        // 3. Evaluate condition (entity incomplete check)
        if (reminder.condition != null) {
            if (!evaluateCondition(reminder)) {
                return ReminderEvaluationResult.Suppressed(SuppressionReason.ENTITY_COMPLETE)
            }
        }

        // 4. Apply quiet-hours suppression
        if (quietHours != null && quietHours.contains(currentTime)) {
            return ReminderEvaluationResult.DelayedToQuietHoursEnd(quietHours.end)
        }

        return ReminderEvaluationResult.Fire
    }

    /**
     * Evaluates whether an escalation should fire for a reminder that was not acknowledged.
     *
     * @param reminder The reminder to check escalation for.
     * @param lastFiredAt The timestamp when the reminder last fired (or the original fire time).
     * @param quietHours The user's quiet-hours window, or null if not configured.
     * @param userZone The user's timezone.
     * @return A [ReminderEvaluationResult] indicating escalation, suppression, or delay.
     */
    suspend fun evaluateEscalation(
        reminder: Reminder,
        lastFiredAt: Instant,
        quietHours: QuietHoursWindow?,
        userZone: ZoneId
    ): ReminderEvaluationResult {
        // Check if max escalations reached
        if (reminder.escalationCount >= reminder.maxEscalations) {
            return ReminderEvaluationResult.Suppressed(SuppressionReason.MAX_ESCALATIONS_REACHED)
        }

        val now = Instant.now(clock)
        val elapsed = Duration.between(lastFiredAt, now)

        // Not enough time has passed for an escalation
        if (elapsed < reminder.escalationInterval) {
            return ReminderEvaluationResult.Suppressed(SuppressionReason.RECURRENCE_NOT_MATCHED)
        }

        // Re-check condition — entity might have been completed since last fire
        if (reminder.condition != null) {
            if (!evaluateCondition(reminder)) {
                return ReminderEvaluationResult.Suppressed(SuppressionReason.ENTITY_COMPLETE)
            }
        }

        val currentTime = now.atZone(userZone).toLocalTime()

        // Apply quiet-hours suppression to escalations too
        if (quietHours != null && quietHours.contains(currentTime)) {
            return ReminderEvaluationResult.DelayedToQuietHoursEnd(quietHours.end)
        }

        return ReminderEvaluationResult.Escalate(newEscalationCount = reminder.escalationCount + 1)
    }

    /**
     * Determines if today matches the given recurrence rule.
     *
     * @param rule The recurrence rule to evaluate.
     * @param today The current date.
     * @param scheduledDate The original scheduled date of the reminder (anchor for weekly/monthly/custom).
     */
    fun matchesRecurrence(rule: RecurrenceRule, today: LocalDate, scheduledDate: LocalDate?): Boolean {
        return when (rule.type) {
            RecurrenceType.DAILY -> true

            RecurrenceType.SPECIFIC_WEEKDAYS -> {
                val weekdays = rule.weekdays ?: return false
                today.dayOfWeek in weekdays
            }

            RecurrenceType.WEEKLY -> {
                // Fires on the same day-of-week as the scheduled date
                val anchor = scheduledDate ?: return false
                today.dayOfWeek == anchor.dayOfWeek
            }

            RecurrenceType.MONTHLY -> {
                // Fires on the same day-of-month as the scheduled date
                val anchor = scheduledDate ?: return false
                today.dayOfMonth == anchor.dayOfMonth
            }

            RecurrenceType.CUSTOM_INTERVAL -> {
                // Fires every N days from the scheduled date
                val anchor = scheduledDate ?: return false
                val intervalDays = rule.intervalDays ?: return false
                if (intervalDays <= 0) return false
                val daysBetween = ChronoUnit.DAYS.between(anchor, today)
                daysBetween >= 0 && daysBetween % intervalDays == 0L
            }
        }
    }

    /**
     * Evaluates the reminder's condition.
     * Returns true if the reminder should fire (condition is met / entity is incomplete).
     */
    private suspend fun evaluateCondition(reminder: Reminder): Boolean {
        val condition = reminder.condition ?: return true
        return when (condition.type) {
            ConditionType.ENTITY_INCOMPLETE -> {
                !entityCompletionChecker.isComplete(reminder.entityType, reminder.entityId)
            }
        }
    }
}
