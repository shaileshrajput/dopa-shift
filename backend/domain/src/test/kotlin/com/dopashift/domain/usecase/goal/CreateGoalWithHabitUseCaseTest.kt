package com.dopashift.domain.usecase.goal

import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.exception.GoalNameAlreadyExistsException
import com.dopashift.domain.port.TransactionRunner
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.util.UUID

/**
 * Unit tests for [CreateGoalWithHabitUseCase] (Inline_Goal_Capture server-side atomicity).
 *
 * Covers:
 * - Additive plain-goal creation when no nested habit track is present.
 * - Nested goal + habit creation committed together.
 * - Atomic rollback: a failing habit save leaves zero goals persisted.
 *
 * Validates: Requirements 3.3, 5.5 (DQC-3.3, DQC-5.5)
 */
class CreateGoalWithHabitUseCaseTest : FunSpec({

    fun descriptions30(): List<String> = (1..30).map { "Day $it micro-habit" }

    test("creates a plain goal and no habit when habitTrack is absent (additive)") {
        val goals = InMemoryGoalRepo()
        val habits = InMemoryHabitRepo()
        val useCase = CreateGoalWithHabitUseCase(goals, habits, DirectTransactionRunner)
        val userId = UUID.randomUUID()

        val result = useCase.execute(
            CreateGoalWithHabitCommand(
                userId = userId,
                name = "Learn Kotlin",
                category = "Learning",
                keywords = listOf("kotlin"),
                habitTrack = null
            )
        )

        result.habitTrack shouldBe null
        goals.count() shouldBe 1
        habits.count() shouldBe 0
    }

    test("creates goal and habit together when nested habitTrack is present") {
        val goals = InMemoryGoalRepo()
        val habits = InMemoryHabitRepo()
        val useCase = CreateGoalWithHabitUseCase(goals, habits, DirectTransactionRunner)
        val userId = UUID.randomUUID()

        val result = useCase.execute(
            CreateGoalWithHabitCommand(
                userId = userId,
                name = "Get Fit",
                category = "Fitness",
                keywords = listOf("gym", "run"),
                habitTrack = NestedHabitTrack(descriptions = descriptions30())
            )
        )

        result.habitTrack.shouldNotBeNull()
        // Habit references the goal created in the same transaction (no orphan).
        result.habitTrack!!.goalId shouldBe result.goal.id
        result.habitTrack!!.checkpoints.size shouldBe 30
        result.habitTrack!!.currentDay shouldBe 1
        goals.count() shouldBe 1
        habits.count() shouldBe 1
    }

    test("rolls back the goal when habit persistence fails (atomicity DQC-3.3)") {
        val goals = InMemoryGoalRepo()
        val habits = FailingHabitRepo()
        // Transaction runner that discards writes made in a failed block.
        val runner = RollbackTransactionRunner(goals)
        val useCase = CreateGoalWithHabitUseCase(goals, habits, runner)
        val userId = UUID.randomUUID()

        shouldThrow<IllegalStateException> {
            useCase.execute(
                CreateGoalWithHabitCommand(
                    userId = userId,
                    name = "Build Business",
                    category = "Build Business",
                    keywords = listOf("startup"),
                    habitTrack = NestedHabitTrack(descriptions = descriptions30())
                )
            )
        }

        goals.count() shouldBe 0
        habits.count() shouldBe 0
    }

    test("rejects duplicate goal name before any persistence") {
        val goals = InMemoryGoalRepo()
        val habits = InMemoryHabitRepo()
        val useCase = CreateGoalWithHabitUseCase(goals, habits, DirectTransactionRunner)
        val userId = UUID.randomUUID()

        useCase.execute(
            CreateGoalWithHabitCommand(userId, "Focus", "Wellbeing", listOf("calm"))
        )

        shouldThrow<GoalNameAlreadyExistsException> {
            useCase.execute(
                CreateGoalWithHabitCommand(userId, "focus", "Wellbeing", listOf("calm"))
            )
        }
        goals.count() shouldBe 1
    }
})

/** Runs the block directly (no real transaction) — writes persist. */
private object DirectTransactionRunner : TransactionRunner {
    override suspend fun <T> execute(block: suspend () -> T): T = block()
}

/** Simulates rollback: on exception, restores the goal store to its pre-block snapshot. */
private class RollbackTransactionRunner(private val goals: InMemoryGoalRepo) : TransactionRunner {
    override suspend fun <T> execute(block: suspend () -> T): T {
        val snapshot = goals.snapshot()
        return try {
            block()
        } catch (e: Throwable) {
            goals.restore(snapshot)
            throw e
        }
    }
}

private class InMemoryGoalRepo : GoalRepository {
    private val store = mutableListOf<GoalProfile>()
    fun count() = store.size
    fun snapshot(): List<GoalProfile> = store.toList()
    fun restore(items: List<GoalProfile>) {
        store.clear(); store.addAll(items)
    }

    override suspend fun findById(id: UUID) = store.find { it.id == id }
    override suspend fun findByUserId(userId: UUID) = store.filter { it.userId == userId }
    override suspend fun findByUserIdAndName(userId: UUID, name: String) =
        store.find { it.userId == userId && it.name.equals(name, ignoreCase = true) }
    override suspend fun save(goal: GoalProfile): GoalProfile {
        store.removeAll { it.id == goal.id }; store.add(goal); return goal
    }
    override suspend fun delete(id: UUID) { store.removeAll { it.id == id } }
    override suspend fun countActiveByUserId(userId: UUID) =
        store.count { it.userId == userId && it.isActive }
}

private open class InMemoryHabitRepo : HabitTrackRepository {
    protected val store = mutableListOf<HabitTrack>()
    fun count() = store.size
    override suspend fun findById(id: UUID) = store.find { it.id == id }
    override suspend fun findActiveByGoalId(goalId: UUID) = store.filter { it.goalId == goalId }
    override suspend fun findActiveByUserId(userId: UUID) = store.filter { it.userId == userId }
    override suspend fun save(track: HabitTrack): HabitTrack {
        store.removeAll { it.id == track.id }; store.add(track); return track
    }
    override suspend fun deleteByGoalId(goalId: UUID) { store.removeAll { it.goalId == goalId } }
    override suspend fun reassignToGoal(fromGoalId: UUID, toGoalId: UUID) {}
    override suspend fun countActiveByUserId(userId: UUID) =
        store.count { it.userId == userId && !it.isFinished }
}

private class FailingHabitRepo : InMemoryHabitRepo() {
    override suspend fun save(track: HabitTrack): HabitTrack {
        throw IllegalStateException("Simulated habit persistence failure")
    }
}
