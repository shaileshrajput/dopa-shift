package com.dopashift.domain.creation

// Feature: dashboard-quick-create
// Tags: DQC-1.7

import com.dopashift.domain.creation.usecase.UndoCreationInteractor
import com.dopashift.domain.entity.ChangeLogEntry
import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.repository.ChangeLogRepository
import com.dopashift.domain.repository.DailyTodoRepository
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Unit tests for [UndoCreationInteractor] (tasks.md 11.1, DQC-1.7).
 *
 * Assert that Undo deletes each entity referenced by the [UndoToken] through the standard
 * delete path AND emits a Change_Log **deletion** event (null value) through the shared
 * emitter — never a local-only rollback. For an Inline_Goal_Capture token (goal + habit) the
 * whole transaction is reverted, with the dependent habit deleted before its goal.
 *
 * All fakes are in-memory and framework-free so the domain module stays pure Kotlin.
 */
class UndoCreationInteractorTest : StringSpec({

    val userId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val fixedInstant = Instant.parse("2025-01-01T00:00:00Z")
    val fixedToday = LocalDate.of(2025, 1, 1)
    val correlationId = CorrelationId.random()

    fun goal(id: UUID) = GoalProfile(
        id = id,
        userId = userId,
        name = "Run daily",
        category = "Fitness",
        keywords = listOf("running"),
        createdAt = fixedInstant,
        updatedAt = fixedInstant
    )

    fun habit(id: UUID, goalId: UUID) = HabitTrack(
        id = id,
        goalId = goalId,
        userId = userId,
        startDate = fixedToday,
        currentDay = 1
    )

    fun todo(id: UUID) = DailyTodoItem(
        id = id,
        userId = userId,
        text = "Buy groceries",
        dueDateTime = null,
        createdAt = fixedInstant,
        updatedAt = fixedInstant,
        dayDate = fixedToday
    )

    fun newInteractor(
        goalRepo: GoalRepository,
        habitRepo: HabitTrackRepository,
        todoRepo: DailyTodoRepository,
        changeLog: ChangeLogRepository
    ) = UndoCreationInteractor(
        goalRepository = goalRepo,
        habitTrackRepository = habitRepo,
        dailyTodoRepository = todoRepo,
        changeLogRepository = changeLog,
        deviceIdProvider = { "device-under-test" },
        clock = object : DomainClock {
            override fun now(): Instant = fixedInstant
            override fun today(): LocalDate = fixedToday
        }
    )

    "undo of a single to-do deletes it and emits a deletion Change_Log event, not a rollback" {
        runBlocking {
            val todoId = UUID.randomUUID()
            val todoRepo = FakeDailyTodoRepository().apply { store[todoId] = todo(todoId) }
            val goalRepo = FakeGoalRepository()
            val habitRepo = FakeHabitTrackRepository()
            val changeLog = RecordingChangeLogRepository()

            val interactor = newInteractor(goalRepo, habitRepo, todoRepo, changeLog)

            val result = interactor(
                UndoToken(
                    entityRefs = listOf(EntityRef(type = "todo", id = todoId)),
                    correlationId = correlationId
                )
            )

            result.shouldBeInstanceOf<CreationResult.Success<Unit>>()
            // The entity is gone via the standard delete path.
            todoRepo.store[todoId].shouldBeNull()
            // Exactly one deletion event was appended (null value denotes deletion) — DQC-1.7.
            changeLog.entries.size shouldBe 1
            val event = changeLog.entries.single()
            event.entityId shouldBe todoId
            event.entityType shouldBe "DailyTodoItem"
            event.value.shouldBeNull()
            event.userId shouldBe userId
        }
    }

    "undo of an Inline_Goal_Capture token reverts the whole transaction, habit before goal" {
        runBlocking {
            val goalId = UUID.randomUUID()
            val habitId = UUID.randomUUID()
            val goalRepo = FakeGoalRepository().apply { store[goalId] = goal(goalId) }
            val habitRepo = FakeHabitTrackRepository().apply { store[habitId] = habit(habitId, goalId) }
            val todoRepo = FakeDailyTodoRepository()
            val changeLog = RecordingChangeLogRepository()

            val interactor = newInteractor(goalRepo, habitRepo, todoRepo, changeLog)

            // Token lists the goal first, then the habit (creation order, DQC-6.2).
            val result = interactor(
                UndoToken(
                    entityRefs = listOf(
                        EntityRef(type = "goal", id = goalId),
                        EntityRef(type = "habit_track", id = habitId)
                    ),
                    correlationId = correlationId
                )
            )

            result.shouldBeInstanceOf<CreationResult.Success<Unit>>()
            // Both entities deleted — the whole unit reverted (OQ-3 default).
            goalRepo.store[goalId].shouldBeNull()
            habitRepo.store[habitId].shouldBeNull()

            // Two deletion events, and the dependent habit's deletion precedes the goal's so a
            // replay never references an already-deleted goal.
            changeLog.entries.map { it.entityType } shouldContainExactly
                listOf("HabitTrack", "GoalProfile")
            changeLog.entries.forEach { it.value.shouldBeNull() }
        }
    }

    "undo is idempotent when a referenced entity is already gone" {
        runBlocking {
            val missingId = UUID.randomUUID()
            val goalRepo = FakeGoalRepository()
            val habitRepo = FakeHabitTrackRepository()
            val todoRepo = FakeDailyTodoRepository()
            val changeLog = RecordingChangeLogRepository()

            val interactor = newInteractor(goalRepo, habitRepo, todoRepo, changeLog)

            val result = interactor(
                UndoToken(
                    entityRefs = listOf(EntityRef(type = "goal", id = missingId)),
                    correlationId = correlationId
                )
            )

            result.shouldBeInstanceOf<CreationResult.Success<Unit>>()
            // No entity to delete, so no deletion event is emitted.
            changeLog.entries.shouldBeEmpty()
        }
    }

    "a deletion failure surfaces as CreationResult.Failure" {
        runBlocking {
            val goalId = UUID.randomUUID()
            val goalRepo = object : FakeGoalRepository() {
                override suspend fun delete(id: UUID) {
                    throw IllegalStateException("delete blew up")
                }
            }.apply { store[goalId] = goal(goalId) }
            val habitRepo = FakeHabitTrackRepository()
            val todoRepo = FakeDailyTodoRepository()
            val changeLog = RecordingChangeLogRepository()

            val interactor = newInteractor(goalRepo, habitRepo, todoRepo, changeLog)

            val result = interactor(
                UndoToken(
                    entityRefs = listOf(EntityRef(type = "goal", id = goalId)),
                    correlationId = correlationId
                )
            )

            val failure = result.shouldBeInstanceOf<CreationResult.Failure>()
            failure.cause.shouldBeInstanceOf<IllegalStateException>()
        }
    }
})

/** In-memory [GoalRepository]; only the methods the interactor uses are meaningful. */
private open class FakeGoalRepository : GoalRepository {
    val store = LinkedHashMap<UUID, GoalProfile>()
    override suspend fun findById(id: UUID): GoalProfile? = store[id]
    override suspend fun findByUserId(userId: UUID): List<GoalProfile> =
        store.values.filter { it.userId == userId }
    override suspend fun findByUserIdAndName(userId: UUID, name: String): GoalProfile? =
        store.values.firstOrNull { it.userId == userId && it.name == name }
    override suspend fun save(goal: GoalProfile): GoalProfile {
        store[goal.id] = goal; return goal
    }
    override suspend fun delete(id: UUID) { store.remove(id) }
    override suspend fun countActiveByUserId(userId: UUID): Int =
        store.values.count { it.userId == userId && it.isActive }
    override fun observeByUserId(userId: UUID): Flow<List<GoalProfile>> = emptyFlow()
}

/** In-memory [HabitTrackRepository]. */
private class FakeHabitTrackRepository : HabitTrackRepository {
    val store = LinkedHashMap<UUID, HabitTrack>()
    override suspend fun findById(id: UUID): HabitTrack? = store[id]
    override suspend fun findActiveByGoalId(goalId: UUID): List<HabitTrack> =
        store.values.filter { it.goalId == goalId && !it.isFinished }
    override suspend fun findActiveByUserId(userId: UUID): List<HabitTrack> =
        store.values.filter { it.userId == userId && !it.isFinished }
    override suspend fun save(track: HabitTrack): HabitTrack { store[track.id] = track; return track }
    override suspend fun delete(id: UUID) { store.remove(id) }
    override fun observeActiveByUserId(userId: UUID): Flow<List<HabitTrack>> = emptyFlow()
}

/** In-memory [DailyTodoRepository]. */
private class FakeDailyTodoRepository : DailyTodoRepository {
    val store = LinkedHashMap<UUID, DailyTodoItem>()
    override suspend fun findById(id: UUID): DailyTodoItem? = store[id]
    override suspend fun findByUserIdAndDate(userId: UUID, date: LocalDate): List<DailyTodoItem> =
        store.values.filter { it.userId == userId && it.dayDate == date }
    override suspend fun countByUserIdAndDate(userId: UUID, date: LocalDate): Int =
        store.values.count { it.userId == userId && it.dayDate == date }
    override suspend fun save(item: DailyTodoItem): DailyTodoItem { store[item.id] = item; return item }
    override suspend fun delete(id: UUID) { store.remove(id) }
    override fun observeByUserIdAndDate(userId: UUID, date: LocalDate): Flow<List<DailyTodoItem>> =
        emptyFlow()
}

/** Recording [ChangeLogRepository] preserving append order for the assertions. */
private class RecordingChangeLogRepository : ChangeLogRepository {
    val entries = ArrayList<ChangeLogEntry>()
    override suspend fun append(entry: ChangeLogEntry) { entries.add(entry) }
    override suspend fun findSince(userId: UUID, since: Instant): List<ChangeLogEntry> =
        entries.filter { it.userId == userId && it.timestamp >= since }
    override suspend fun countByUserId(userId: UUID): Long = entries.count { it.userId == userId }.toLong()
    override suspend fun archiveOlderThan(cutoff: Instant) {}
    override fun observeRecent(userId: UUID, limit: Int, offset: Int): Flow<List<ChangeLogEntry>> =
        emptyFlow()
}
