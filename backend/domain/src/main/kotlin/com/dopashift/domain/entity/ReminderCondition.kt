package com.dopashift.domain.entity

/**
 * A condition that must be true for a reminder to fire.
 */
data class ReminderCondition(
    val type: ConditionType
)
