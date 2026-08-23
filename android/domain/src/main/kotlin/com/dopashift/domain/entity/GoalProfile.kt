package com.dopashift.domain.entity

import java.time.Instant
import java.util.UUID

/**
 * A user-defined life-goal entity with a name, category, keywords,
 * associated tasks, and habit programs.
 */
data class GoalProfile(
    val id: UUID,
    val userId: UUID,
    val name: String,
    val category: String,
    val keywords: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val isActive: Boolean = true
) {
    init {
        require(name.length in 1..100) { "Goal name must be 1-100 characters" }
        require(category.length in 1..50) { "Category must be 1-50 characters" }
        require(keywords.size <= 20) { "Maximum 20 keywords per goal" }
        require(keywords.all { it.length in 1..50 }) { "Each keyword must be 1-50 characters" }
    }
}
