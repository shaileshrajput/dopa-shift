package com.dopashift.domain.usecase.goal

import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.exception.GoalNameAlreadyExistsException
import com.dopashift.domain.exception.KeywordValidationException
import com.dopashift.domain.repository.GoalRepository
import java.time.Instant
import java.util.UUID

/**
 * Creates a new goal profile for a user.
 *
 * Validates:
 * - Name uniqueness per user (case-insensitive)
 * - Keyword count (max 20) and length (each 1–50 characters)
 * - Name length (1–100 characters) and category (1–50 characters) enforced by entity
 *
 * Requirements: 1.1, 1.2, 1.7
 */
class CreateGoalUseCase(
    private val goalRepository: GoalRepository
) {

    suspend fun execute(command: CreateGoalCommand): GoalProfile {
        validateKeywords(command.keywords)
        validateNameUniqueness(command.userId, command.name)

        val now = Instant.now()
        val goal = GoalProfile(
            id = UUID.randomUUID(),
            userId = command.userId,
            name = command.name,
            category = command.category,
            keywords = command.keywords,
            createdAt = now,
            updatedAt = now,
            isActive = true
        )

        return goalRepository.save(goal)
    }

    private suspend fun validateNameUniqueness(userId: UUID, name: String) {
        val existing = goalRepository.findByUserIdAndName(userId, name.trim())
        if (existing != null) {
            throw GoalNameAlreadyExistsException(name)
        }
    }

    private fun validateKeywords(keywords: List<String>) {
        if (keywords.size > 20) {
            throw KeywordValidationException("A goal can have at most 20 keywords")
        }
        if (keywords.isEmpty()) {
            throw KeywordValidationException("At least one keyword is required")
        }
        keywords.forEachIndexed { index, keyword ->
            if (keyword.isBlank() || keyword.length !in 1..50) {
                throw KeywordValidationException(
                    "Keyword at index $index must be between 1 and 50 characters"
                )
            }
        }
    }
}

data class CreateGoalCommand(
    val userId: UUID,
    val name: String,
    val category: String,
    val keywords: List<String>
)
