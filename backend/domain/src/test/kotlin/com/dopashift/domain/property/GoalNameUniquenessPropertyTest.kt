package com.dopashift.domain.property

import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.exception.GoalNameAlreadyExistsException
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.usecase.goal.CreateGoalCommand
import com.dopashift.domain.usecase.goal.CreateGoalUseCase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.util.UUID

/**
 * Property 1: Goal Name Uniqueness Enforcement
 *
 * For any user and any two goal creation attempts with the same name (case-insensitive),
 * the system SHALL accept exactly one and reject the second with a duplicate-name error,
 * regardless of timing or device of origin.
 *
 * **Validates: Requirements 1.7**
 *
 * Tag: Feature: dopa-shift, Property 1: Goal Name Uniqueness Enforcement
 */
class GoalNameUniquenessPropertyTest : FunSpec({

    tags(io.kotest.core.Tag("Feature: dopa-shift"), io.kotest.core.Tag("Property 1: Goal Name Uniqueness Enforcement"))

    test("Property 1: Goal Name Uniqueness Enforcement - second creation with same name (case-insensitive) is rejected") {
        checkAll(100, goalNameArb()) { goalName ->
            // Fresh in-memory repository for each iteration
            val repository = InMemoryGoalRepository()
            val useCase = CreateGoalUseCase(repository)
            val userId = UUID.randomUUID()

            // First creation should succeed
            val firstCommand = CreateGoalCommand(
                userId = userId,
                name = goalName,
                category = "TestCategory",
                keywords = listOf("keyword1")
            )
            val created = useCase.execute(firstCommand)
            created.name shouldBe goalName

            // Generate case-variant of the same name
            val caseVariant = randomCaseVariant(goalName)

            // Second creation with same name (case variation) should fail
            val secondCommand = CreateGoalCommand(
                userId = userId,
                name = caseVariant,
                category = "AnotherCategory",
                keywords = listOf("keyword2")
            )

            val exception = runCatching { useCase.execute(secondCommand) }.exceptionOrNull()
            exception.shouldBeInstanceOf<GoalNameAlreadyExistsException>()
        }
    }

    test("Property 1: Goal Name Uniqueness Enforcement - various case combinations all rejected") {
        checkAll(100, goalNameArb()) { goalName ->
            val repository = InMemoryGoalRepository()
            val useCase = CreateGoalUseCase(repository)
            val userId = UUID.randomUUID()

            // First creation with original name
            val firstCommand = CreateGoalCommand(
                userId = userId,
                name = goalName,
                category = "TestCategory",
                keywords = listOf("keyword1")
            )
            useCase.execute(firstCommand)

            // Test uppercase variant
            val upperCommand = firstCommand.copy(name = goalName.uppercase())
            val upperException = runCatching { useCase.execute(upperCommand) }.exceptionOrNull()
            upperException.shouldBeInstanceOf<GoalNameAlreadyExistsException>()

            // Test lowercase variant
            val lowerCommand = firstCommand.copy(name = goalName.lowercase())
            val lowerException = runCatching { useCase.execute(lowerCommand) }.exceptionOrNull()
            lowerException.shouldBeInstanceOf<GoalNameAlreadyExistsException>()

            // Test mixed-case variant
            val mixedName = goalName.mapIndexed { i, c ->
                if (i % 2 == 0) c.uppercaseChar() else c.lowercaseChar()
            }.joinToString("")
            val mixedCommand = firstCommand.copy(name = mixedName)
            val mixedException = runCatching { useCase.execute(mixedCommand) }.exceptionOrNull()
            mixedException.shouldBeInstanceOf<GoalNameAlreadyExistsException>()
        }
    }
})

/**
 * Generates random goal names (arbitrary strings 1-100 chars)
 * that are valid for GoalProfile (non-blank, 1-100 chars).
 * Names have no leading/trailing whitespace to avoid trim-related edge cases
 * and focus on the case-insensitivity property.
 */
private fun goalNameArb(): Arb<String> = arbitrary {
    val length = Arb.int(1..100).bind()
    val nonSpaceChars = ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('-', '_', '.')
    val innerChars = nonSpaceChars + listOf(' ')
    buildString {
        // First character: non-whitespace
        append(nonSpaceChars.random())
        if (length > 2) {
            // Middle characters: can include spaces
            repeat(length - 2) {
                append(innerChars.random())
            }
        }
        if (length > 1) {
            // Last character: non-whitespace
            append(nonSpaceChars.random())
        }
    }
}

/**
 * Creates a random case variant of the given string.
 * Each character is randomly uppercased or lowercased.
 */
private fun randomCaseVariant(original: String): String {
    return original.map { c ->
        if (Math.random() > 0.5) c.uppercaseChar() else c.lowercaseChar()
    }.joinToString("")
}

/**
 * In-memory implementation of GoalRepository for property testing.
 * Performs case-insensitive name matching to simulate the database's
 * UNIQUE(user_id, name) constraint with case-insensitive collation.
 */
private class InMemoryGoalRepository : GoalRepository {
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
