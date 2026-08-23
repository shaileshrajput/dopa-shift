package com.dopashift.domain.entity

import java.time.LocalDate
import java.util.UUID

/**
 * A 30-day sequence of micro-habit checkpoints tied to a GoalProfile.
 */
data class HabitTrack(
    val id: UUID,
    val goalId: UUID,
    val userId: UUID,
    val startDate: LocalDate,
    val currentDay: Int,
    val isFinished: Boolean = false,
    val checkpoints: List<HabitCheckpoint> = emptyList()
) {
    init {
        require(currentDay in 1..30) { "Current day must be between 1 and 30" }
    }
}
