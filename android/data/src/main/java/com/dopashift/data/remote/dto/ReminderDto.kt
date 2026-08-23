package com.dopashift.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreateReminderRequest(
    val entityType: String,
    val entityId: String,
    val scheduledTime: String,
    val scheduledDate: String? = null,
    val recurrenceType: String? = null,
    val recurrenceWeekdays: List<Int>? = null,
    val recurrenceIntervalDays: Int? = null,
    val conditionType: String? = null,
    val escalationIntervalMinutes: Int = 15,
    val maxEscalations: Int = 3
)

@JsonClass(generateAdapter = true)
data class UpdateReminderRequest(
    val scheduledTime: String? = null,
    val scheduledDate: String? = null,
    val recurrenceType: String? = null,
    val recurrenceWeekdays: List<Int>? = null,
    val recurrenceIntervalDays: Int? = null,
    val conditionType: String? = null,
    val escalationIntervalMinutes: Int? = null,
    val maxEscalations: Int? = null,
    val isActive: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class ReminderResponse(
    val id: String,
    val entityType: String,
    val entityId: String,
    val scheduledTime: String,
    val scheduledDate: String?,
    val recurrenceType: String?,
    val recurrenceWeekdays: List<Int>?,
    val recurrenceIntervalDays: Int?,
    val conditionType: String?,
    val escalationIntervalMinutes: Int,
    val maxEscalations: Int,
    val currentEscalationCount: Int,
    val isActive: Boolean,
    val createdAt: String,
    val updatedAt: String
)
