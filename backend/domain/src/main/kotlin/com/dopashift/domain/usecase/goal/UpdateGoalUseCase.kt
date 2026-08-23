package com.dopashift.domain.usecase.goal

import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.exception.GoalNameAlreadyExistsException
import com.dopashift.domain.exception.GoalNotFoundException
import com.dopashift.domain.exception.KeywordValidationException
import com.dopashift.domain.repository.GoalRepository
import java.time.Instant
import java.util.UUID

/**
 * Updates an existing goal profile.
 *
 * Validates:
 * - Goal exists
 * - Name uniqueness per user (case-insensitive) if name changed
 * - Keyword count (max 20) and length (each 1–50 characters)
 *
 * Requirements: 1.1, 1.2, 1.7
 */
class UpdateGoalUseCase(
    private val goalRepository: GoalRepository
) {

    suspend fun execute(command: UpdateGoalCommand): GoalProfile {
        val existing = goalRepository.findById(command.goalId)
            ?: throw GoalNotFoundException(command.goalId.toString())

        validateKeywords(command.keywords)

        // Check name uniqueness only if name actually changed (case-insensitive)
        if (!existing.name.equals(command.name, ignoreCase = true)) {
            validateNameUniqueness(existing.userId, command.name)
        }

        val updated = existing.copy(
            name = command.name,
            category = command.category,
            keywords = command.keywords,
            isActive = command.isActive,
            updatedAt = Instant.now()
        )

        return goalRepository.save(updated)
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

data class UpdateGoalCommand(
    val goalId: UUID,
    val name: String,
    val category: String,
    val keywords: List<String>,
    val isActive: Boolean = true
)
