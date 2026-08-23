package com.dopashift.domain.entity

import java.time.DayOfWeek

/**
 * Defines how a reminder recurs over time.
 */
data class RecurrenceRule(
    val type: RecurrenceType,
    val weekdays: Set<DayOfWeek>? = null,
    val intervalDays: Int? = null
)
