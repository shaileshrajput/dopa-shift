package com.dopashift.api.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.DayOfWeek
import java.util.UUID

/**
 * DTOs for the Reminders REST API.
 * Supports all recurrence types and condition types defined in the domain.
 *
 * Requirements: 5.1, 5.2, 5.3, 5.4
 */

// --- Request DTOs ---

data class ReminderCreateDto(
    @field:NotBlank(message = "Entity type is required")
    val entityType: String,

    @field:NotNull(message = "Entity ID is required")
    val entityId: UUID,

    @field:NotBlank(message = "Scheduled time is required")
    val scheduledTime: String,

    val scheduledDate: String? = null,
    val recurrence: RecurrenceRuleDto? = null,
    val condition: ReminderConditionDto? = null,

    @field:Min(1, message = "Escalation interval must be at least 1 minute")
    @field:Max(1440, message = "Escalation interval must be at most 1440 minutes")
    val escalationIntervalMinutes: Long,

    @field:Min(0, message = "Max escalations must be at least 0")
    @field:Max(10, message = "Max escalations must be at most 10")
    val maxEscalations: Int
)

data class ReminderUpdateDto(
    val scheduledTime: String? = null,
    val scheduledDate: String? = null,
    val recurrence: RecurrenceRuleDto? = null,
    val condition: ReminderConditionDto? = null,

    @field:Min(1, message = "Escalation interval must be at least 1 minute")
    @field:Max(1440, message = "Escalation interval must be at most 1440 minutes")
    val escalationIntervalMinutes: Long? = null,

    @field:Min(0, message = "Max escalations must be at least 0")
    @field:Max(10, message = "Max escalations must be at most 10")
    val maxEscalations: Int? = null,
    val isActive: Boolean? = null
)

// --- Response DTO ---

data class ReminderResponseDto(
    val id: UUID,
    val userId: UUID,
    val entityType: String,
    val entityId: UUID,
    val scheduledTime: String,
    val scheduledDate: String?,
    val recurrence: RecurrenceRuleDto?,
    val condition: ReminderConditionDto?,
    val escalationIntervalMinutes: Long,
    val maxEscalations: Int,
    val escalationCount: Int,
    val isActive: Boolean
)

// --- Shared sub-DTOs ---

data class RecurrenceRuleDto(
    val type: String,
    val weekdays: Set<DayOfWeek>? = null,
    val intervalDays: Int? = null
)

data class ReminderConditionDto(
    val type: String
)
