package com.dopashift.domain.entity

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * A standalone daily to-do list entry, independent of any goal.
 */
data class DailyTodoItem(
    val id: UUID,
    val userId: UUID,
    val text: String,
    val dueDateTime: Instant?,
    val isCompleted: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
    val dayDate: LocalDate
) {
    init {
        require(text.length in 1..500) { "Todo text must be 1-500 characters" }
    }
}
