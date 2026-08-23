package com.dopashift.domain.model

import java.time.LocalTime

/**
 * The result of evaluating whether a reminder should fire at a given time.
 */
sealed class ReminderEvaluationResult {

    /** The reminder should fire now. */
    data object Fire : ReminderEvaluationResult()

    /** The reminder should not fire — reason provided for diagnostics. */
    data class Suppressed(val reason: SuppressionReason) : ReminderEvaluationResult()

    /** The reminder is suppressed by quiet hours but should be delivered at the window end. */
    data class DelayedToQuietHoursEnd(val deliveryTime: LocalTime) : ReminderEvaluationResult()

    /**
     * An escalation should fire — the reminder was not acknowledged within the escalation interval.
     * Contains the updated escalation count.
     */
    data class Escalate(val newEscalationCount: Int) : ReminderEvaluationResult()
}

/**
 * Reasons a reminder may be suppressed (not fired).
 */
enum class SuppressionReason {
    /** The reminder's recurrence rule does not match the current date/time. */
    RECURRENCE_NOT_MATCHED,

    /** The entity linked to the reminder is already complete. */
    ENTITY_COMPLETE,

    /** The reminder is inactive. */
    INACTIVE,

    /** The reminder has a scheduled date that does not match today. */
    SCHEDULED_DATE_NOT_TODAY,

    /** Max escalations reached — no more escalations will fire. */
    MAX_ESCALATIONS_REACHED
}
