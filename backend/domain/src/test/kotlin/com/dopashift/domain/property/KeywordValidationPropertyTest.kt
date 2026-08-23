package com.dopashift.domain.property

import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.exception.KeywordValidationException
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.usecase.goal.CreateGoalCommand
import com.dopashift.domain.usecase.goal.CreateGoalUseCase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.util.UUID

/**
 * Property 18: Keyword Count and Length Validation
 *
 * For any goal, adding keywords SHALL be rejected if it would result in more than
 * 20 keywords per goal, or if any keyword exceeds 50 characters or is empty.
 *
 * **Validates: Requirements 1.2**
 *
 * Tag: Feature: dopa-shift, Property 18: Keyword Count and Length Validation
 */
class KeywordValidationPropertyTest : FunSpec({

    tags(
        io.kotest.core.Tag("Feature: dopa-shift"),
        io.kotest.core.Tag("Property 18: Keyword Count and Length Validation")
    )

    test("Property 18: Keywords list with more than 20 entries is rejected") {
        checkAll(100, tooManyKeywordsArb()) { keywords ->
            val repository = InMemoryGoalRepositoryForKeywords()
            val useCase = CreateGoalUseCase(repository)
            val userId = UUID.randomUUID()

            val command = CreateGoalCommand(
                userId = userId,
                name = "Goal-${UUID.randomUUID()}",
                category = "TestCategory",
                keywords = keywords
            )

            val exception = runCatching { useCase.execute(command) }.exceptionOrNull()
            exception.shouldBeInstanceOf<KeywordValidationException>()
        }
    }

    test("Property 18: Keywords list with any keyword exceeding 50 characters is rejected") {
        checkAll(100, keywordsWithOneTooLongArb()) { keywords ->
            val repository = InMemoryGoalRepositoryForKeywords()
            val useCase = CreateGoalUseCase(repository)
            val userId = UUID.randomUUID()

            val command = CreateGoalCommand(
                userId = userId,
                name = "Goal-${UUID.randomUUID()}",
                category = "TestCategory",
                keywords = keywords
            )

            val exception = runCatching { useCase.execute(command) }.exceptionOrNull()
            exception.shouldBeInstanceOf<KeywordValidationException>()
        }
    }

    test("Property 18: Keywords list with empty or blank keywords is rejected") {
        checkAll(100, keywordsWithEmptyOrBlankArb()) { keywords ->
            val repository = InMemoryGoalRepositoryForKeywords()
            val useCase = CreateGoalUseCase(repository)
            val userId = UUID.randomUUID()

            val command = CreateGoalCommand(
                userId = userId,
                name = "Goal-${UUID.randomUUID()}",
                category = "TestCategory",
                keywords = keywords
            )

            val exception = runCatching { useCase.execute(command) }.exceptionOrNull()
            exception.shouldBeInstanceOf<KeywordValidationException>()
        }
    }

    test("Property 18: Valid keywords list (1-20 keywords, each 1-50 chars) is accepted") {
        checkAll(100, validKeywordsArb()) { keywords ->
            val repository = InMemoryGoalRepositoryForKeywords()
            val useCase = CreateGoalUseCase(repository)
            val userId = UUID.randomUUID()

            val command = CreateGoalCommand(
                userId = userId,
                name = "Goal-${UUID.randomUUID()}",
                category = "TestCategory",
                keywords = keywords
            )

            val result = useCase.execute(command)
            result.keywords shouldBe keywords
        }
    }
})

// --- Generators ---

/**
 * Generates keyword lists with more than 20 entries (21-40 keywords).
 * Each keyword is valid (1-50 chars, non-blank).
 */
private fun tooManyKeywordsArb(): Arb<List<String>> = arbitrary {
    val count = Arb.int(21..40).bind()
    (1..count).map { validKeyword(it) }
}

/**
 * Generates keyword lists (1-20 entries) where at least one keyword exceeds 50 characters.
 */
private fun keywordsWithOneTooLongArb(): Arb<List<String>> = arbitrary {
    val count = Arb.int(1..20).bind()
    val tooLongIndex = Arb.int(0 until count).bind()
    val tooLongLength = Arb.int(51..200).bind()
    (0 until count).map { index ->
        if (index == tooLongIndex) {
            buildValidString(tooLongLength)
        } else {
            validKeyword(index)
        }
    }
}

/**
 * Generates keyword lists (1-20 entries) where at least one keyword is empty or blank.
 */
private fun keywordsWithEmptyOrBlankArb(): Arb<List<String>> = arbitrary {
    val count = Arb.int(1..20).bind()
    val blankIndex = Arb.int(0 until count).bind()
    val blankType = Arb.int(0..2).bind() // 0=empty, 1=single space, 2=multiple spaces
    (0 until count).map { index ->
        if (index == blankIndex) {
            when (blankType) {
                0 -> ""
                1 -> " "
                else -> "   "
            }
        } else {
            validKeyword(index)
        }
    }
}

/**
 * Generates valid keyword lists: 1-20 keywords, each 1-50 characters, non-blank.
 */
private fun validKeywordsArb(): Arb<List<String>> = arbitrary {
    val count = Arb.int(1..20).bind()
    (1..count).map {
        val length = Arb.int(1..50).bind()
        buildValidString(length)
    }
}

/**
 * Builds a non-blank string of the given length using alphanumeric characters.
 */
private fun buildValidString(length: Int): String {
    val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    return (1..length).map { chars.random() }.joinToString("")
}

/**
 * Creates a valid keyword for testing (non-blank, 1-50 chars).
 */
private fun validKeyword(seed: Int): String {
    return "keyword-$seed"
}

/**
 * In-memory implementation of GoalRepository for keyword validation property testing.
 */
private class InMemoryGoalRepositoryForKeywords : GoalRepository {
    private val store = mutableListOf<GoalProfile>()

    override suspend fun findById(id: UUID): GoalProfile? =
        store.find { it.id == id }

    override suspend fun findByUserId(userId: UUID): List<GoalProfile> =
        store.filter { it.userId == userId }

    override suspend fun findByUserIdAndName(userId: UUID, name: String): GoalProfile? =
        store.find { it.userId == userId && it.name.equals(name, ignoreCase = true) }

    override suspend fun save(goal: GoalProfile): GoalProfile {
        store.removeAll { it.id == goal.id }
        store.add(goal)
        return goal
    }

    override suspend fun delete(id: UUID) {
        store.removeAll { it.id == id }
    }

    override suspend fun countActiveByUserId(userId: UUID): Int =
        store.count { it.userId == userId && it.isActive }
}
