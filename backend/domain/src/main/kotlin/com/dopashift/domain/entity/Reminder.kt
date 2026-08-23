package com.dopashift.domain.entity

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * A scheduled notification (one-off, repeating, or conditional) attachable to
 * any DailyTodoItem, GoalChecklistItem, or HabitTrack checkpoint.
 *
 * Validation constraints:
 * - maxEscalations: 1–10
 * - escalationCount: 0–maxEscalations
 * - escalationInterval: positive duration
 */
data class Reminder(
    val id: UUID,
    val userId: UUID,
    val entityType: ReminderEntityType,
    val entityId: UUID,
    val scheduledTime: LocalTime,
    val scheduledDate: LocalDate?,
    val recurrence: RecurrenceRule?,
    val condition: ReminderCondition?,
    val escalationInterval: Duration,
    val maxEscalations: Int,
    val escalationCount: Int = 0,
    val isActive: Boolean = true
) {
    init {
        require(maxEscalations in 1..10) {
            "Max escalations must be between 1 and 10"
        }
        require(escalationCount in 0..maxEscalations) {
            "Escalation count must be between 0 and maxEscalations ($maxEscalations)"
        }
        require(!escalationInterval.isNegative && !escalationInterval.isZero) {
            "Escalation interval must be a positive duration"
        }
    }
}
