package com.dopashift.domain.entity

import java.time.Instant
import java.util.UUID

/**
 * A user-defined life-goal entity with a name, category, keywords,
 * associated tasks, and habit programs.
 *
 * Validation constraints:
 * - name: 1–100 characters, unique per user
 * - category: 1–50 characters
 * - keywords: max 20, each 1–50 characters
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
        require(name.isNotBlank() && name.length in 1..100) {
            "Goal name must be between 1 and 100 characters"
        }
        require(category.isNotBlank() && category.length in 1..50) {
            "Category must be between 1 and 50 characters"
        }
        require(keywords.size <= 20) {
            "A goal can have at most 20 keywords"
        }
        keywords.forEachIndexed { index, keyword ->
            require(keyword.isNotBlank() && keyword.length in 1..50) {
                "Keyword at index $index must be between 1 and 50 characters"
            }
        }
    }
}
