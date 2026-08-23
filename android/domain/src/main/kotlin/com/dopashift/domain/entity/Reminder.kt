package com.dopashift.domain.entity

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * A scheduled notification (one-off, repeating, or conditional)
 * attachable to any DailyTodoItem, GoalChecklistItem, or HabitTrack checkpoint.
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
)

enum class ReminderEntityType { DAILY_TODO, GOAL_CHECKLIST, HABIT_CHECKPOINT }

data class RecurrenceRule(
    val type: RecurrenceType,
    val weekdays: Set<java.time.DayOfWeek>? = null,
    val intervalDays: Int? = null
)

enum class RecurrenceType { DAILY, SPECIFIC_WEEKDAYS, WEEKLY, MONTHLY, CUSTOM_INTERVAL }

data class ReminderCondition(
    val type: ConditionType
)

enum class ConditionType { ENTITY_INCOMPLETE }
