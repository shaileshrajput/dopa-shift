package com.dopashift.domain.property

import com.dopashift.domain.entity.GoalChecklistItem
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.entity.HabitCheckpoint
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.entity.CheckpointStatus
import com.dopashift.domain.entity.InterceptionRule
import com.dopashift.domain.repository.GoalChecklistItemRepository
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import com.dopashift.domain.repository.InterceptionRuleRepository
import com.dopashift.domain.usecase.goal.DeleteGoalAction
import com.dopashift.domain.usecase.goal.DeleteGoalCommand
import com.dopashift.domain.usecase.goal.DeleteGoalUseCase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.uuid
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.localDate
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Property 2: Goal Deletion Leaves No Orphans
 *
 * For any goal deletion (whether with DELETE_ALL or REASSIGN action), after the operation
 * completes, there SHALL be zero Goal_Checklist_Items, Habit_Tracks, or Interception_Rules
 * referencing the deleted goal's ID in the database.
 *
 * **Validates: Requirements 1.5, 1.6**
 *
 * Tags: Feature: dopa-shift, Property 2: Goal Deletion Leaves No Orphans
 */
class GoalDeletionLeavesNoOrphansPropertyTest : FunSpec({

    tags(io.kotest.core.Tag("Feature: dopa-shift"), io.kotest.core.Tag("Property 2: Goal Deletion Leaves No Orphans"))

    // --- In-Memory Repository Implementations ---

    class InMemoryGoalRepository : GoalRepository {
        val store = mutableMapOf<UUID, GoalProfile>()

        override suspend fun findById(id: UUID): GoalProfile? = store[id]
        override suspend fun findByUserId(userId: UUID): List<GoalProfile> =
            store.values.filter { it.userId == userId }

        override suspend fun findByUserIdAndName(userId: UUID, name: String): GoalProfile? =
            store.values.find { it.userId == userId && it.name == name }

        override suspend fun save(goal: GoalProfile): GoalProfile {
            store[goal.id] = goal
            return goal
        }

        override suspend fun delete(id: UUID) {
            store.remove(id)
        }

        override suspend fun countActiveByUserId(userId: UUID): Int =
            store.values.count { it.userId == userId && it.isActive }
    }

    class InMemoryGoalChecklistItemRepository : GoalChecklistItemRepository {
        val store = mutableMapOf<UUID, GoalChecklistItem>()

        override suspend fun findById(id: UUID): GoalChecklistItem? = store[id]

        override suspend fun findByGoalId(goalId: UUID): List<GoalChecklistItem> =
            store.values.filter { it.goalId == goalId }

        override suspend fun findByUserId(userId: UUID): List<GoalChecklistItem> =
            store.values.filter { it.userId == userId }

        override suspend fun save(item: GoalChecklistItem): GoalChecklistItem {
            store[item.id] = item
            return item
        }

        override suspend fun delete(id: UUID) {
            store.remove(id)
        }

        override suspend fun reassignToGoal(fromGoalId: UUID, toGoalId: UUID) {
            store.replaceAll { _, item ->
                if (item.goalId == fromGoalId) item.copy(goalId = toGoalId) else item
            }
        }

        override suspend fun deleteByGoalId(goalId: UUID) {
            store.entries.removeIf { it.value.goalId == goalId }
        }
    }

    class InMemoryHabitTrackRepository : HabitTrackRepository {
        val store = mutableMapOf<UUID, HabitTrack>()

        override suspend fun findById(id: UUID): HabitTrack? = store[id]
        override suspend fun findActiveByGoalId(goalId: UUID): List<HabitTrack> =
            store.values.filter { it.goalId == goalId && !it.isFinished }

        override suspend fun findActiveByUserId(userId: UUID): List<HabitTrack> =
            store.values.filter { it.userId == userId && !it.isFinished }

        override suspend fun save(track: HabitTrack): HabitTrack {
            store[track.id] = track
            return track
        }

        override suspend fun deleteByGoalId(goalId: UUID) {
            store.entries.removeIf { it.value.goalId == goalId }
        }

        override suspend fun reassignToGoal(fromGoalId: UUID, toGoalId: UUID) {
            store.replaceAll { _, track ->
                if (track.goalId == fromGoalId) track.copy(goalId = toGoalId) else track
            }
        }

        override suspend fun countActiveByUserId(userId: UUID): Int =
            store.values.count { it.userId == userId && !it.isFinished }
    }

    class InMemoryInterceptionRuleRepository : InterceptionRuleRepository {
        val store = mutableMapOf<UUID, InterceptionRule>()

        override suspend fun findById(id: UUID): InterceptionRule? = store[id]

        override suspend fun findByGoalId(goalId: UUID): List<InterceptionRule> =
            store.values.filter { it.goalId == goalId }

        override suspend fun findByUserId(userId: UUID): List<InterceptionRule> =
            store.values.filter { it.userId == userId }

        override suspend fun reassignToGoal(fromGoalId: UUID, toGoalId: UUID) {
            store.replaceAll { _, rule ->
                if (rule.goalId == fromGoalId) rule.copy(goalId = toGoalId) else rule
            }
        }

        override suspend fun deleteByGoalId(goalId: UUID) {
            store.entries.removeIf { it.value.goalId == goalId }
        }

        override suspend fun delete(id: UUID) {
            store.remove(id)
        }

        override suspend fun save(rule: InterceptionRule): InterceptionRule {
            store[rule.id] = rule
            return rule
        }
    }

    // --- Arbitrary Generators ---

    val arbGoalProfile: Arb<GoalProfile> = Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        Arb.string(minSize = 1, maxSize = 50),
        Arb.string(minSize = 1, maxSize = 30)
    ) { id, userId, name, category ->
        GoalProfile(
            id = id,
            userId = userId,
            name = name.take(100).ifBlank { "Goal" },
            category = category.take(50).ifBlank { "General" },
            keywords = emptyList(),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            isActive = true
        )
    }

    fun arbChecklistItems(goalId: UUID, userId: UUID, count: Int): List<GoalChecklistItem> =
        (1..count).map {
            GoalChecklistItem(
                id = UUID.randomUUID(),
                goalId = goalId,
                userId = userId,
                text = "Checklist item $it",
                isCompleted = false,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        }

    fun arbHabitTracks(goalId: UUID, userId: UUID, count: Int): List<HabitTrack> =
        (1..count).map {
            HabitTrack(
                id = UUID.randomUUID(),
                goalId = goalId,
                userId = userId,
                startDate = LocalDate.now(),
                currentDay = 1,
                isFinished = false,
                checkpoints = emptyList()
            )
        }

    fun arbInterceptionRules(goalId: UUID, userId: UUID, count: Int): List<InterceptionRule> =
        (1..count).map {
            InterceptionRule(
                id = UUID.randomUUID(),
                userId = userId,
                goalId = goalId,
                appPackageName = "com.example.app$it",
                siteDomain = null,
                dailyAllowanceMinutes = (it % 480) + 1,
                isActive = true,
                createdAt = Instant.now()
            )
        }

    // --- Property Tests ---

    test("DELETE_ALL: after deletion, no items reference the deleted goal's ID") {
        checkAll(100, arbGoalProfile, Arb.int(0..5), Arb.int(0..5), Arb.int(0..5)) {
            goal, checklistCount, habitCount, ruleCount ->

            // Fresh repos per iteration
            val goalRepo = InMemoryGoalRepository()
            val checklistRepo = InMemoryGoalChecklistItemRepository()
            val habitRepo = InMemoryHabitTrackRepository()
            val ruleRepo = InMemoryInterceptionRuleRepository()

            // Seed the goal
            goalRepo.save(goal)

            // Seed dependent items
            arbChecklistItems(goal.id, goal.userId, checklistCount).forEach {
                checklistRepo.store[it.id] = it
            }
            arbHabitTracks(goal.id, goal.userId, habitCount).forEach {
                habitRepo.store[it.id] = it
            }
            arbInterceptionRules(goal.id, goal.userId, ruleCount).forEach {
                ruleRepo.store[it.id] = it
            }

            val useCase = DeleteGoalUseCase(goalRepo, checklistRepo, habitRepo, ruleRepo)

            // Execute DELETE_ALL
            useCase.execute(DeleteGoalCommand(goal.id, DeleteGoalAction.DeleteAll))

            // Assert: no orphans referencing deleted goal
            checklistRepo.findByGoalId(goal.id).shouldBeEmpty()
            habitRepo.store.values.filter { it.goalId == goal.id }.shouldBeEmpty()
            ruleRepo.findByGoalId(goal.id).shouldBeEmpty()

            // Assert: goal itself is deleted
            goalRepo.findById(goal.id) shouldBe null
        }
    }

    test("REASSIGN: after deletion, no items reference the deleted goal's ID and all reference target") {
        checkAll(100, arbGoalProfile, arbGoalProfile, Arb.int(0..5), Arb.int(0..5), Arb.int(0..5)) {
            sourceGoal, targetGoalBase, checklistCount, habitCount, ruleCount ->

            // Ensure target goal has a different ID
            val targetGoal = targetGoalBase.copy(
                id = if (targetGoalBase.id == sourceGoal.id) UUID.randomUUID() else targetGoalBase.id,
                userId = sourceGoal.userId
            )

            // Fresh repos per iteration
            val goalRepo = InMemoryGoalRepository()
            val checklistRepo = InMemoryGoalChecklistItemRepository()
            val habitRepo = InMemoryHabitTrackRepository()
            val ruleRepo = InMemoryInterceptionRuleRepository()

            // Seed both goals
            goalRepo.save(sourceGoal)
            goalRepo.save(targetGoal)

            // Seed dependent items for source goal
            val checklistItems = arbChecklistItems(sourceGoal.id, sourceGoal.userId, checklistCount)
            checklistItems.forEach { checklistRepo.store[it.id] = it }

            val habitTracks = arbHabitTracks(sourceGoal.id, sourceGoal.userId, habitCount)
            habitTracks.forEach { habitRepo.store[it.id] = it }

            val rules = arbInterceptionRules(sourceGoal.id, sourceGoal.userId, ruleCount)
            rules.forEach { ruleRepo.store[it.id] = it }

            val useCase = DeleteGoalUseCase(goalRepo, checklistRepo, habitRepo, ruleRepo)

            // Execute REASSIGN
            useCase.execute(DeleteGoalCommand(sourceGoal.id, DeleteGoalAction.Reassign(targetGoal.id)))

            // Assert: no orphans referencing deleted goal
            checklistRepo.findByGoalId(sourceGoal.id).shouldBeEmpty()
            habitRepo.store.values.filter { it.goalId == sourceGoal.id }.shouldBeEmpty()
            ruleRepo.findByGoalId(sourceGoal.id).shouldBeEmpty()

            // Assert: source goal itself is deleted
            goalRepo.findById(sourceGoal.id) shouldBe null

            // Assert: all items now reference the target goal
            val reassignedChecklist = checklistRepo.store.values
                .filter { it.id in checklistItems.map { item -> item.id }.toSet() }
            reassignedChecklist.forEach { it.goalId shouldBe targetGoal.id }

            val reassignedHabits = habitRepo.store.values
                .filter { it.id in habitTracks.map { t -> t.id }.toSet() }
            reassignedHabits.forEach { it.goalId shouldBe targetGoal.id }

            val reassignedRules = ruleRepo.store.values
                .filter { it.id in rules.map { r -> r.id }.toSet() }
            reassignedRules.forEach { it.goalId shouldBe targetGoal.id }

            // Assert: counts match (no items lost)
            reassignedChecklist.size shouldBe checklistCount
            reassignedHabits.size shouldBe habitCount
            reassignedRules.size shouldBe ruleCount
        }
    }
})
