package com.dopashift.domain.creation

// Feature: dashboard-quick-create, Property 9: Undo Emits a Change_Log Deletion Event

import com.dopashift.domain.creation.usecase.UndoCreationInteractor
import com.dopashift.domain.entity.ChangeLogEntry
import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.repository.ChangeLogRepository
import com.dopashift.domain.repository.DailyTodoRepository
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import io.kotest.common.ExperimentalKotest
import io.kotest.core.Tag
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Property 9: Undo Emits a Change_Log Deletion Event.
 *
 * Requirement 1.7: when the user activates "Undo", the Android_App deletes the just-created
 * entity AND emits the corresponding Change_Log **deletion** event through the standard
 * Sync_Engine pipeline (parent Req 4 AC4–AC5) — undo is explicitly **NOT** a local-only
 * rollback.
 *
 * The subject under test is [UndoCreationInteractor] (task 11.1), driven with in-memory fakes.
 * For any [UndoToken] — a single entity (goal, habit, or to-do) or an Inline_Goal_Capture
 * goal+habit token — invoking undo must, for every referenced entity that exists:
 *   1. remove it from its repository via the standard delete path, AND
 *   2. append exactly one Change_Log entry that is a *deletion* event — one with a `null`
 *      value (the deletion marker), scoped to the entity's owning `userId`, targeting the
 *      entity's id.
 *
 * A local-only rollback would leave the [ChangeLogRepository] untouched; asserting that a
 * deletion event is appended for each entity is precisely what distinguishes the required
 * sync-safe behavior from a rollback (DQC-1.7, DQC-7.4).
 *
 * Kotest property testing, 200 iterations (>= 100 per the design Testing Strategy).
 *
 * **Feature: dashboard-quick-create, Property 9**
 * **Validates: Requirements 1.7** (also satisfies DQC-7.4)
 */
@OptIn(ExperimentalKotest::class)
class UndoDeletionEventPropertyTest : StringSpec({

    tags(
        Tag("Feature: dashboard-quick-create"),
        Tag("Property 9")
    )

    val config = PropTestConfig(iterations = 200)

    val fixedInstant = Instant.parse("2025-01-01T00:00:00Z")
    val fixedToday = LocalDate.of(2025, 1, 1)
    val clock = object : DomainClock {
        override fun now(): Instant = fixedInstant
        override fun today(): LocalDate = fixedToday
    }

    // --- Fakes ------------------------------------------------------------------------

    /** In-memory [GoalRepository]; only the methods the interactor uses are meaningful. */
    class FakeGoalRepository : GoalRepository {
        val store = LinkedHashMap<UUID, GoalProfile>()
        override suspend fun findById(id: UUID): GoalProfile? = store[id]
        override suspend fun findByUserId(userId: UUID): List<GoalProfile> =
            store.values.filter { it.userId == userId }
        override suspend fun findByUserIdAndName(userId: UUID, name: String): GoalProfile? =
            store.values.firstOrNull { it.userId == userId && it.name == name }
        override suspend fun save(goal: GoalProfile): GoalProfile { store[goal.id] = goal; return goal }
        override suspend fun delete(id: UUID) { store.remove(id) }
        override suspend fun countActiveByUserId(userId: UUID): Int =
            store.values.count { it.userId == userId && it.isActive }
        override fun observeByUserId(userId: UUID): Flow<List<GoalProfile>> = emptyFlow()
    }

    /** In-memory [HabitTrackRepository]. */
    class FakeHabitTrackRepository : HabitTrackRepository {
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
    class FakeDailyTodoRepository : DailyTodoRepository {
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
    class RecordingChangeLogRepository : ChangeLogRepository {
        val entries = ArrayList<ChangeLogEntry>()
        override suspend fun append(entry: ChangeLogEntry) { entries.add(entry) }
        override suspend fun findSince(userId: UUID, since: Instant): List<ChangeLogEntry> =
            entries.filter { it.userId == userId && it.timestamp >= since }
        override suspend fun countByUserId(userId: UUID): Long =
            entries.count { it.userId == userId }.toLong()
        override suspend fun archiveOlderThan(cutoff: Instant) {}
        override fun observeRecent(userId: UUID, limit: Int, offset: Int): Flow<List<ChangeLogEntry>> =
            emptyFlow()
    }

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
        clock = clock
    )

    fun goal(id: UUID, userId: UUID) = GoalProfile(
        id = id,
        userId = userId,
        name = "Run daily",
        category = "Fitness",
        keywords = listOf("running"),
        createdAt = fixedInstant,
        updatedAt = fixedInstant
    )

    fun habit(id: UUID, goalId: UUID, userId: UUID) = HabitTrack(
        id = id,
        goalId = goalId,
        userId = userId,
        startDate = fixedToday,
        currentDay = 1
    )

    fun todo(id: UUID, userId: UUID) = DailyTodoItem(
        id = id,
        userId = userId,
        text = "Buy groceries",
        dueDateTime = null,
        createdAt = fixedInstant,
        updatedAt = fixedInstant,
        dayDate = fixedToday
    )

    // The Change_Log entity types the interactor emits, keyed by EntityRef.type.
    val entityTypeForRef = mapOf(
        "goal" to "GoalProfile",
        "habit_track" to "HabitTrack",
        "todo" to "DailyTodoItem"
    )

    // --- Generators -------------------------------------------------------------------

    // Non-blank correlation tokens (CorrelationId requires non-blank input).
    val correlationIds: Arb<CorrelationId> =
        Arb.string(1..40).filter { it.isNotBlank() }.map { CorrelationId(it) }

    // --- Properties -------------------------------------------------------------------

    "Property 9 (DQC-1.7): undo of a single created entity emits exactly one deletion event, not a rollback" {
        // For each single-entity token type (goal / habit_track / todo), the entity is removed
        // via the standard delete path AND one deletion Change_Log event (null value) is appended
        // and scoped to the owner. A local-only rollback would leave the Change_Log empty.
        checkAll(
            config,
            Arb.uuid(),   // userId
            Arb.uuid(),   // entity id
            Arb.uuid(),   // goalId (only used for the habit case)
            Arb.int(0, 2), // which entity type: 0=goal, 1=habit_track, 2=todo
            correlationIds
        ) { userId, entityId, goalId, typeChoice, correlationId ->
            val goalRepo = FakeGoalRepository()
            val habitRepo = FakeHabitTrackRepository()
            val todoRepo = FakeDailyTodoRepository()
            val changeLog = RecordingChangeLogRepository()

            val refType = when (typeChoice) {
                0 -> "goal".also { goalRepo.store[entityId] = goal(entityId, userId) }
                1 -> "habit_track".also { habitRepo.store[entityId] = habit(entityId, goalId, userId) }
                else -> "todo".also { todoRepo.store[entityId] = todo(entityId, userId) }
            }

            val interactor = newInteractor(goalRepo, habitRepo, todoRepo, changeLog)

            val result = interactor(
                UndoToken(
                    entityRefs = listOf(EntityRef(type = refType, id = entityId)),
                    correlationId = correlationId
                )
            )

            result.shouldBeInstanceOf<CreationResult.Success<Unit>>()

            // 1. The entity is gone via the standard delete path.
            goalRepo.store[entityId].shouldBeNull()
            habitRepo.store[entityId].shouldBeNull()
            todoRepo.store[entityId].shouldBeNull()

            // 2. Exactly one Change_Log event was emitted — and it is a DELETION event
            //    (null value marker), scoped to the owner and targeting the entity. This is the
            //    core of DQC-1.7: a real deletion event, never a silent local rollback.
            changeLog.entries.size shouldBe 1
            val event = changeLog.entries.single()
            event.value.shouldBeNull()               // null value denotes deletion
            event.entityId shouldBe entityId
            event.entityType shouldBe entityTypeForRef.getValue(refType)
            event.userId shouldBe userId
        }
    }

    "Property 9 (DQC-1.7): undo of an Inline_Goal_Capture token emits a deletion event per referenced entity" {
        // A goal + habit token reverts the whole transaction: BOTH entities are deleted and a
        // deletion Change_Log event is emitted for each (never a local-only rollback of either).
        checkAll(
            config,
            Arb.uuid(),   // userId
            Arb.uuid(),   // goalId
            Arb.uuid(),   // habitId
            correlationIds
        ) { userId, goalId, habitId, correlationId ->
            val goalRepo = FakeGoalRepository().apply { store[goalId] = goal(goalId, userId) }
            val habitRepo = FakeHabitTrackRepository().apply {
                store[habitId] = habit(habitId, goalId, userId)
            }
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

            // Both entities deleted through the standard path.
            goalRepo.store[goalId].shouldBeNull()
            habitRepo.store[habitId].shouldBeNull()

            // Exactly two Change_Log events, one per referenced entity, and BOTH are deletion
            // events (null value) — a per-entity deletion, not a rollback of the transaction.
            changeLog.entries.size shouldBe 2
            changeLog.entries.forEach { entry ->
                entry.value.shouldBeNull()
                entry.userId shouldBe userId
            }
            // A deletion event exists for each referenced entity id.
            changeLog.entries.map { it.entityId }.toSet() shouldBe setOf(goalId, habitId)
            // Dependent habit deleted before its goal (inverse of DQC-6.2 create ordering).
            changeLog.entries.map { it.entityType } shouldContainExactly
                listOf("HabitTrack", "GoalProfile")
        }
    }
})
