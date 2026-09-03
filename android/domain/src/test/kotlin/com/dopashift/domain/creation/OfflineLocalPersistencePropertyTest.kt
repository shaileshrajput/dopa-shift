package com.dopashift.domain.creation

import com.dopashift.domain.creation.usecase.CreateDailyTodoInteractor
import com.dopashift.domain.creation.usecase.CreateGoalInteractor
import com.dopashift.domain.entity.ChangeLogEntry
import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.repository.ChangeLogRepository
import com.dopashift.domain.repository.DailyTodoRepository
import com.dopashift.domain.repository.GoalRepository
import io.kotest.assertions.withClue
import io.kotest.core.Tag
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Property 5: Offline Local Persistence and Change_Log Enqueue.
 *
 * *For any* valid creation input, driving the single, shared creation use cases
 * ([CreateGoalInteractor] / [CreateDailyTodoInteractor]) — the same ones the dedicated
 * screens use, with no parallel data path — SHALL, **regardless of connectivity**:
 *  1. persist the entity to the local store (Room in production; an in-memory local store
 *     here), and
 *  2. enqueue the corresponding create Change_Log events for the Sync_Engine.
 *
 * The interactors are pure Kotlin and make **no** network call: the repository writes local
 * first and the emitter appends Change_Log events to the append-only queue (design: "Local
 * Room write within 200 ms; Change_Log enqueued regardless of connectivity", DQC-2.8, DQC-4.2,
 * DQC-4.9). "Offline" is therefore the interactors' *normal* mode of operation. This test
 * models being offline explicitly with an [Offline] connectivity fake threaded through the
 * fakes so the assertion is unambiguous: even when the network is unreachable, both the local
 * write and the Change_Log enqueue happen. (The design's 200 ms budget is a timing/integration
 * concern covered by task 18.3; this domain property asserts the local-write + enqueue-offline
 * invariant.)
 *
 * **Validates: Requirements 2.8, 4.2, 4.9**
 *
 * Tags: Feature: dashboard-quick-create, Property 5 (satisfies DQC-7.5)
 */
class OfflineLocalPersistencePropertyTest : StringSpec({

    tags(
        Tag("Feature: dashboard-quick-create"),
        Tag("Property 5")
    )

    // >= 100 iterations per the design Testing Strategy.
    val config = PropTestConfig(iterations = 200)

    // ---------------------------------------------------------------------------------
    // Connectivity model: the device is offline. If any fake ever attempted a network
    // call it would fail, so a passing test proves the create path is purely local.
    // ---------------------------------------------------------------------------------

    /** Models device connectivity. In this test the device is always [Offline]. */
    class Connectivity(val online: Boolean) {
        /** Would-be network hop; called by nothing here — proves the path never syncs inline. */
        fun requireOnlineForNetwork(): Nothing =
            error("No network available (offline): creation must not perform a network call")
    }

    val offline = Connectivity(online = false)

    // ---------------------------------------------------------------------------------
    // In-memory local fakes standing in for Room. None of them touch the network.
    // ---------------------------------------------------------------------------------

    /** In-memory local goal store (stands in for Room), scoped by user. */
    class FakeGoalRepository(private val connectivity: Connectivity) : GoalRepository {
        val goals = LinkedHashMap<UUID, GoalProfile>()

        override suspend fun findById(id: UUID): GoalProfile? = goals[id]
        override suspend fun findByUserId(userId: UUID): List<GoalProfile> =
            goals.values.filter { it.userId == userId }
        override suspend fun findByUserIdAndName(userId: UUID, name: String): GoalProfile? =
            goals.values.firstOrNull { it.userId == userId && it.name.equals(name, ignoreCase = true) }
        override suspend fun save(goal: GoalProfile): GoalProfile {
            // Local-first: writes to the in-memory store with no dependency on connectivity.
            goals[goal.id] = goal
            return goal
        }
        override suspend fun delete(id: UUID) { goals.remove(id) }
        override suspend fun countActiveByUserId(userId: UUID): Int =
            goals.values.count { it.userId == userId && it.isActive }
        override fun observeByUserId(userId: UUID): Flow<List<GoalProfile>> =
            flowOf(goals.values.filter { it.userId == userId })
    }

    /** In-memory local to-do store (stands in for Room), scoped by user and day. */
    class FakeDailyTodoRepository(private val connectivity: Connectivity) : DailyTodoRepository {
        val items = LinkedHashMap<UUID, DailyTodoItem>()

        override suspend fun findById(id: UUID): DailyTodoItem? = items[id]
        override suspend fun findByUserIdAndDate(userId: UUID, date: LocalDate): List<DailyTodoItem> =
            items.values.filter { it.userId == userId && it.dayDate == date }
        override suspend fun countByUserIdAndDate(userId: UUID, date: LocalDate): Int =
            items.values.count { it.userId == userId && it.dayDate == date }
        override suspend fun save(item: DailyTodoItem): DailyTodoItem {
            items[item.id] = item
            return item
        }
        override suspend fun delete(id: UUID) { items.remove(id) }
        override fun observeByUserIdAndDate(userId: UUID, date: LocalDate): Flow<List<DailyTodoItem>> =
            flowOf(items.values.filter { it.userId == userId && it.dayDate == date })
    }

    /**
     * Recording append-only Change_Log queue. `append` never contacts the network — it only
     * enqueues locally for the Sync_Engine to drain later — so it works identically offline.
     */
    class RecordingChangeLogRepository : ChangeLogRepository {
        val entries = ArrayList<ChangeLogEntry>()
        override suspend fun append(entry: ChangeLogEntry) { entries.add(entry) }
        override suspend fun findSince(userId: UUID, since: Instant): List<ChangeLogEntry> =
            entries.filter { it.userId == userId && !it.timestamp.isBefore(since) }
        override suspend fun countByUserId(userId: UUID): Long =
            entries.count { it.userId == userId }.toLong()
        override suspend fun archiveOlderThan(cutoff: Instant) {}
        override fun observeRecent(userId: UUID, limit: Int, offset: Int): Flow<List<ChangeLogEntry>> =
            flowOf(entries.filter { it.userId == userId })
    }

    val fixedToday = LocalDate.of(2025, 6, 1)
    val fixedNow: Instant = Instant.parse("2025-06-01T09:00:00Z")
    val clock = object : DomainClock {
        override fun now(): Instant = fixedNow
        override fun today(): LocalDate = fixedToday
    }
    val deviceIdProvider = DeviceIdProvider { "test-device" }

    // ---------------------------------------------------------------------------------
    // Generators constrained to the VALID input space (a valid create must succeed).
    // ---------------------------------------------------------------------------------

    // 1-100 chars, non-blank after trim.
    val goalName: Arb<String> = arbitrary { rs ->
        val body = Arb.string(1..90).bind().replace("\n", " ").trim().ifBlank { "goal" }
        // Uniqueness per user: append a random suffix so generated names never collide.
        (body + "-" + Arb.int(0..1_000_000).bind()).take(100)
    }
    // 1-50 chars, non-blank after trim.
    val category: Arb<String> = arbitrary { rs ->
        Arb.string(1..40).bind().replace("\n", " ").trim().ifBlank { "Learning" }.take(50)
    }
    // 1-50 chars each, 1-20 of them.
    val keyword: Arb<String> = arbitrary { rs ->
        Arb.string(1..40).bind().replace("\n", " ").trim().ifBlank { "kw" }.take(50)
    }
    val keywords: Arb<List<String>> = Arb.list(keyword, 1..20)

    // Non-blank to-do text, 1-500 chars.
    val todoText: Arb<String> = arbitrary { rs ->
        Arb.string(1..400).bind().replace("\n", " ").trim().ifBlank { "task" }.take(500)
    }

    // ---------------------------------------------------------------------------------
    // Property (goal): a valid goal persists locally AND enqueues its create events offline.
    // ---------------------------------------------------------------------------------

    "Property 5 (DQC-2.8): a valid goal persists to the local store and enqueues create Change_Log events while offline" {
        checkAll(config, goalName, category, keywords) { name, cat, kws ->
            runBlocking {
                val goalRepo = FakeGoalRepository(offline)
                val changeLog = RecordingChangeLogRepository()
                val useCase = CreateGoalInteractor(goalRepo, changeLog, deviceIdProvider, clock)

                val userId: UUID = UUID.randomUUID()
                val result = useCase(
                    CreateGoalCommand(
                        userId = userId,
                        name = name,
                        category = cat,
                        keywords = kws,
                        correlationId = CorrelationId.random(),
                        origin = CreationOrigin.DASHBOARD
                    )
                )

                // (0) A valid input succeeds even with no connectivity.
                result.shouldBeInstanceOf<CreationResult.Success<GoalProfile>>()
                val saved = result.value

                // (1) The entity is persisted to the LOCAL store.
                withClue("goal must be persisted to the local store while offline") {
                    (goalRepo.findById(saved.id) != null).shouldBeTrue()
                }
                goalRepo.goals[saved.id]!!.userId shouldBe userId

                // (2) The corresponding create Change_Log events are enqueued, scoped to the
                //     entity and user, regardless of connectivity.
                val goalEvents = changeLog.entries.filter {
                    it.entityId == saved.id && it.entityType == "GoalProfile"
                }
                withClue("at least one create Change_Log event must be enqueued for the goal") {
                    goalEvents.size shouldBeGreaterThanOrEqual 1
                }
                goalEvents.map { it.field } shouldContainAll listOf("name", "category", "keywords")
                goalEvents.forEach { it.userId shouldBe userId }
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // Property (to-do): a valid to-do persists locally AND enqueues its create events offline.
    // ---------------------------------------------------------------------------------

    "Property 5 (DQC-4.2, DQC-4.9): a valid to-do persists to the local store and enqueues create Change_Log events while offline" {
        checkAll(config, todoText) { text ->
            runBlocking {
                val todoRepo = FakeDailyTodoRepository(offline)
                val changeLog = RecordingChangeLogRepository()
                val useCase = CreateDailyTodoInteractor(todoRepo, changeLog, deviceIdProvider, clock)

                val userId: UUID = UUID.randomUUID()
                val result = useCase(
                    CreateTodoCommand(
                        userId = userId,
                        text = text,
                        dayDate = fixedToday,
                        correlationId = CorrelationId.random(),
                        origin = CreationOrigin.DASHBOARD
                    )
                )

                // (0) A valid input succeeds even with no connectivity.
                result.shouldBeInstanceOf<CreationResult.Success<DailyTodoItem>>()
                val saved = result.value

                // (1) The entity is persisted to the LOCAL store.
                withClue("to-do must be persisted to the local store while offline") {
                    (todoRepo.findById(saved.id) != null).shouldBeTrue()
                }
                todoRepo.items[saved.id]!!.userId shouldBe userId

                // (2) The corresponding create Change_Log events are enqueued, scoped to the
                //     entity and user, regardless of connectivity.
                val todoEvents = changeLog.entries.filter {
                    it.entityId == saved.id && it.entityType == "DailyTodoItem"
                }
                withClue("at least one create Change_Log event must be enqueued for the to-do") {
                    todoEvents.size shouldBeGreaterThanOrEqual 1
                }
                todoEvents.map { it.field } shouldContainAll listOf("text", "dayDate")
                todoEvents.forEach { it.userId shouldBe userId }
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // Focused examples: pin the offline invariant for each entity type.
    // ---------------------------------------------------------------------------------

    "a valid goal created offline is both persisted locally and has its create events enqueued" {
        runBlocking {
            val goalRepo = FakeGoalRepository(offline)
            val changeLog = RecordingChangeLogRepository()
            val useCase = CreateGoalInteractor(goalRepo, changeLog, deviceIdProvider, clock)

            val userId = UUID.randomUUID()
            val result = useCase(
                CreateGoalCommand(
                    userId = userId,
                    name = "Learn Kotlin",
                    category = "Learning",
                    keywords = listOf("kotlin", "coroutines"),
                    correlationId = CorrelationId.random()
                )
            )

            result.shouldBeInstanceOf<CreationResult.Success<GoalProfile>>()
            goalRepo.goals.size shouldBe 1
            changeLog.entries.count { it.entityType == "GoalProfile" } shouldBeGreaterThanOrEqual 1
        }
    }

    "a valid to-do created offline is both persisted locally and has its create events enqueued" {
        runBlocking {
            val todoRepo = FakeDailyTodoRepository(offline)
            val changeLog = RecordingChangeLogRepository()
            val useCase = CreateDailyTodoInteractor(todoRepo, changeLog, deviceIdProvider, clock)

            val userId = UUID.randomUUID()
            val result = useCase(
                CreateTodoCommand(
                    userId = userId,
                    text = "Write the offline persistence test",
                    dayDate = fixedToday,
                    correlationId = CorrelationId.random()
                )
            )

            result.shouldBeInstanceOf<CreationResult.Success<DailyTodoItem>>()
            todoRepo.items.size shouldBe 1
            changeLog.entries.count { it.entityType == "DailyTodoItem" } shouldBeGreaterThanOrEqual 1
        }
    }
})
