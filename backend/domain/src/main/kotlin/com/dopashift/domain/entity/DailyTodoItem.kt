package com.dopashift.domain.entity

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * A standalone daily to-do list entry, independent of any goal.
 * Supports full CRUD and cross-device sync.
 *
 * Validation constraints:
 * - text: 1–500 characters
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
        require(text.isNotBlank() && text.length in 1..500) {
            "Todo text must be between 1 and 500 characters"
        }
    }
}
