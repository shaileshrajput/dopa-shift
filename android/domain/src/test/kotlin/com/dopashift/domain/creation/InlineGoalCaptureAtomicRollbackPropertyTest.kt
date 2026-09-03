package com.dopashift.domain.creation

import com.dopashift.domain.creation.usecase.CreateGoalWithHabitInteractor
import com.dopashift.domain.entity.ChangeLogEntry
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.repository.ChangeLogRepository
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import io.kotest.core.Tag
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Property 6: Inline_Goal_Capture Atomic Rollback.
 *
 * Feature: dashboard-quick-create, Property 6
 * Validates: Requirements 3.3 (satisfies DQC-7.2).
 *
 * Requirement 3.3 requires Inline_Goal_Capture to create the [GoalProfile] and the dependent
 * [HabitTrack] as a single atomic local transaction: IF habit-track creation fails for any
 * reason THEN the goal creation is rolled back so no partially created state is persisted.
 *
 * The subject under test is [CreateGoalWithHabitInteractor] (task 10.1). For any valid inline
 * command where dependent habit persistence fails, after the operation the persisted state must
 * contain:
 * - zero newly created [GoalProfile]s,
 * - zero [HabitTrack]s, and
 * - zero committed [ChangeLogEntry]s from the aborted unit,
 * and the interactor must report [CreationResult.Failure].
 *
 * To exercise real rollback semantics in pure Kotlin (no Room), this test uses:
 * - an [InMemoryStores] holder for goals / habits / change-log entries,
 * - a [RollbackTransactionRunner] fake that snapshots those stores before running the block and
 *   restores the snapshots when the block throws (true rollback), and
 * - a [FaultInjectingHabitTrackRepository] whose `save` always throws, simulating a habit failure
 *   at the point where the goal has already been written inside the transaction.
 *
 * A control run with a non-faulting habit repository confirms the same command persists exactly
 * one goal and one habit and commits its change-log events — so the rollback assertions are
 * proving a real difference, not a store that was never written to.
 *
 * Kotest property testing, 200 iterations (>= 100 per the design Testing Strategy).
 */
class InlineGoalCaptureAtomicRollbackPropertyTest : StringSpec({

    tags(
        Tag("Feature: dashboard-quick-create"),
        Tag("Property 6")
    )

    // >= 100 generated cases per property (design Testing Strategy: min 100 iterations).
    val config = PropTestConfig(iterations = 200)

    val fixedNow: Instant = Instant.parse("2025-05-01T10:15:30Z")
    val fixedToday: LocalDate = LocalDate.of(2025, 5, 1)

    val clock = object : DomainClock {
        override fun now(): Instant = fixedNow
        override fun today(): LocalDate = fixedToday
    }

    val deviceIdProvider = DeviceIdProvider { "test-device" }

    // A template provider that always yields a valid 30-checkpoint template for any category,
    // so Template-mode authoring resolves and the ONLY failure comes from the habit repository.
    val templateProvider = object : HabitTemplateProvider {
        private fun templateFor(category: String, id: String): HabitTemplate =
            HabitTemplate(
                id = id,
                category = category.ifBlank { "general" },
                title = "Template for ${category.ifBlank { "general" }}",
                checkpointDescriptions = (1..HabitTemplate.CHECKPOINT_COUNT).map { "Day $it micro-habit" }
            )

        override fun templatesForCategory(category: String): List<HabitTemplate> =
            listOf(templateFor(category, "tmpl-${category.ifBlank { "general" }}"))

        override fun defaultTemplate(category: String): HabitTemplate =
            templateFor(category, "default-${category.ifBlank { "general" }}")
    }

    val authoringResolver = HabitAuthoringResolver(templateProvider)

    // --- Generators over the valid inline-goal input space ---------------------------------

    val names: Arb<String> = Arb.string(minSize = 1, maxSize = GoalValidator.NAME_MAX)
        .map { it.trim().ifBlank { "Goal name" } }
    val categories: Arb<String> = Arb.string(minSize = 1, maxSize = GoalValidator.CATEGORY_MAX)
        .map { it.trim().ifBlank { "Fitness" } }
    val keyword: Arb<String> = Arb.string(minSize = 1, maxSize = GoalValidator.KEYWORD_MAX_LEN)
        .map { it.trim().ifBlank { "kw" } }
    val keywordLists: Arb<List<String>> = Arb.list(keyword, 1..GoalValidator.KEYWORDS_MAX)

    // Manual authoring carrying 1..40 usable descriptions plus Template mode, so the command is
    // always resolvable (the failure is injected at the habit repository, never the resolver).
    val descriptions: Arb<String> = Arb.string(minSize = 1, maxSize = 120)
        .map { it.trim().ifBlank { "do the thing" } }
    val authorings: Arb<HabitAuthoring> = Arb.bind(
        Arb.int(0, 2),
        Arb.list(descriptions, 1..40)
    ) { mode, descs ->
        when (mode) {
            0 -> HabitAuthoring.Template("tmpl-x")
            1 -> HabitAuthoring.Llm(descs)
            else -> HabitAuthoring.Manual(descs)
        }
    }

    val commands: Arb<InlineGoalWithHabitCommand> = Arb.bind(
        Arb.uuid(),
        names,
        categories,
        keywordLists,
        authorings
    ) { userId, name, category, keywords, authoring ->
        val correlationId = CorrelationId.random()
        InlineGoalWithHabitCommand(
            goal = CreateGoalCommand(
                userId = userId,
                name = name,
                category = category,
                keywords = keywords,
                correlationId = correlationId,
                origin = CreationOrigin.DASHBOARD
            ),
            authoring = authoring,
            withReminder = false,
            correlationId = correlationId
        )
    }

    // --- The core property: an injected habit failure persists NOTHING --------------------

    "injected habit-track failure rolls back the whole unit: 0 goals, 0 habits, 0 committed events" {
        checkAll(config, commands) { command ->
            val stores = InlineGoalCaptureFakes.InMemoryStores()
            val goalRepo = InlineGoalCaptureFakes.InMemoryGoalRepository(stores)
            val habitRepo = InlineGoalCaptureFakes.FaultInjectingHabitTrackRepository()
            val changeLog = InlineGoalCaptureFakes.InMemoryChangeLogRepository(stores)
            val tx = InlineGoalCaptureFakes.RollbackTransactionRunner(stores)

            val interactor = CreateGoalWithHabitInteractor(
                goalRepository = goalRepo,
                habitTrackRepository = habitRepo,
                changeLogRepository = changeLog,
                authoringResolver = authoringResolver,
                transactionRunner = tx,
                deviceIdProvider = deviceIdProvider,
                clock = clock
            )

            val result = interactor(command)

            // The transaction aborted and mapped the injected cause to Failure (DQC-3.3).
            result.shouldBeInstanceOf<CreationResult.Failure>()

            // No partial state remains after rollback.
            stores.goals.shouldBeEmpty()
            stores.habits.shouldBeEmpty()
            stores.changeLog.shouldBeEmpty()

            // The habit repository was reached (the goal was written first, then habit save threw).
            habitRepo.saveAttempts shouldBe 1
        }
    }

    // --- Control: without fault injection the same command DOES persist everything ---------

    "control: without injected failure the same command persists exactly 1 goal + 1 habit and commits events" {
        checkAll(config, commands) { command ->
            val stores = InlineGoalCaptureFakes.InMemoryStores()
            val goalRepo = InlineGoalCaptureFakes.InMemoryGoalRepository(stores)
            val habitRepo = InlineGoalCaptureFakes.InMemoryHabitTrackRepository(stores)
            val changeLog = InlineGoalCaptureFakes.InMemoryChangeLogRepository(stores)
            val tx = InlineGoalCaptureFakes.RollbackTransactionRunner(stores)

            val interactor = CreateGoalWithHabitInteractor(
                goalRepository = goalRepo,
                habitTrackRepository = habitRepo,
                changeLogRepository = changeLog,
                authoringResolver = authoringResolver,
                transactionRunner = tx,
                deviceIdProvider = deviceIdProvider,
                clock = clock
            )

            val result = interactor(command)

            result.shouldBeInstanceOf<CreationResult.Success<GoalWithHabit>>()
            stores.goals.size shouldBe 1
            stores.habits.size shouldBe 1
            // Goal (4 fields) + habit (4 fields) + 30 checkpoints (each 4 fields) => events committed.
            (stores.changeLog.isNotEmpty()) shouldBe true
        }
    }

    // --- Explicit example: the goal write inside the transaction is undone on habit throw ---

    "example: goal saved before the throwing habit save is rolled back to zero" {
        val userId = UUID.randomUUID()
        val correlationId = CorrelationId.random()
        val command = InlineGoalWithHabitCommand(
            goal = CreateGoalCommand(
                userId = userId,
                name = "Run a 5k",
                category = "Fitness",
                keywords = listOf("running", "cardio"),
                correlationId = correlationId,
                origin = CreationOrigin.DASHBOARD
            ),
            authoring = HabitAuthoring.Manual(listOf("Walk 10 minutes")),
            withReminder = false,
            correlationId = correlationId
        )

        val stores = InlineGoalCaptureFakes.InMemoryStores()
        val interactor = CreateGoalWithHabitInteractor(
            goalRepository = InlineGoalCaptureFakes.InMemoryGoalRepository(stores),
            habitTrackRepository = InlineGoalCaptureFakes.FaultInjectingHabitTrackRepository(),
            changeLogRepository = InlineGoalCaptureFakes.InMemoryChangeLogRepository(stores),
            authoringResolver = authoringResolver,
            transactionRunner = InlineGoalCaptureFakes.RollbackTransactionRunner(stores),
            deviceIdProvider = deviceIdProvider,
            clock = clock
        )

        val result = interactor(command)

        result.shouldBeInstanceOf<CreationResult.Failure>()
        stores.goals.shouldBeEmpty()
        stores.habits.shouldBeEmpty()
        stores.changeLog.shouldBeEmpty()
    }
})

/**
 * In-memory fakes for the Inline_Goal_Capture atomicity property test. Grouped in an object so the
 * `RollbackTransactionRunner` can share the single [InMemoryStores] instance the repositories write
 * to and snapshot/restore it for true rollback semantics.
 */
private object InlineGoalCaptureFakes {

    /** Shared mutable stores the repositories append to; snapshotted by the transaction runner. */
    class InMemoryStores {
        val goals: MutableList<GoalProfile> = mutableListOf()
        val habits: MutableList<HabitTrack> = mutableListOf()
        val changeLog: MutableList<ChangeLogEntry> = mutableListOf()
    }

    /**
     * A [TransactionRunner] providing real rollback: it snapshots every store before running the
     * block and, if the block throws, clears each store and restores the snapshot so no partial
     * write survives — then rethrows so the interactor maps the cause to [CreationResult.Failure].
     */
    class RollbackTransactionRunner(private val stores: InMemoryStores) : TransactionRunner {
        override suspend fun <T> inTransaction(block: suspend () -> T): T {
            val goalsSnapshot = stores.goals.toList()
            val habitsSnapshot = stores.habits.toList()
            val changeLogSnapshot = stores.changeLog.toList()
            return try {
                block()
            } catch (t: Throwable) {
                stores.goals.clear(); stores.goals.addAll(goalsSnapshot)
                stores.habits.clear(); stores.habits.addAll(habitsSnapshot)
                stores.changeLog.clear(); stores.changeLog.addAll(changeLogSnapshot)
                throw t
            }
        }
    }

    /** Minimal in-memory [GoalRepository]; `save` appends to the shared store. */
    class InMemoryGoalRepository(private val stores: InMemoryStores) : GoalRepository {
        override suspend fun findById(id: UUID): GoalProfile? = stores.goals.firstOrNull { it.id == id }
        override suspend fun findByUserId(userId: UUID): List<GoalProfile> =
            stores.goals.filter { it.userId == userId }
        override suspend fun findByUserIdAndName(userId: UUID, name: String): GoalProfile? =
            stores.goals.firstOrNull { it.userId == userId && it.name == name }
        override suspend fun save(goal: GoalProfile): GoalProfile {
            stores.goals.add(goal)
            return goal
        }
        override suspend fun delete(id: UUID) { stores.goals.removeAll { it.id == id } }
        override suspend fun countActiveByUserId(userId: UUID): Int =
            stores.goals.count { it.userId == userId && it.isActive }
        override fun observeByUserId(userId: UUID): Flow<List<GoalProfile>> =
            flowOf(stores.goals.filter { it.userId == userId })
    }

    /** In-memory [HabitTrackRepository] that succeeds; used by the control run. */
    class InMemoryHabitTrackRepository(private val stores: InMemoryStores) : HabitTrackRepository {
        override suspend fun findById(id: UUID): HabitTrack? = stores.habits.firstOrNull { it.id == id }
        override suspend fun findActiveByGoalId(goalId: UUID): List<HabitTrack> =
            stores.habits.filter { it.goalId == goalId && !it.isFinished }
        override suspend fun findActiveByUserId(userId: UUID): List<HabitTrack> =
            stores.habits.filter { it.userId == userId && !it.isFinished }
        override suspend fun save(track: HabitTrack): HabitTrack {
            stores.habits.add(track)
            return track
        }
        override suspend fun delete(id: UUID) { stores.habits.removeAll { it.id == id } }
        override fun observeActiveByUserId(userId: UUID): Flow<List<HabitTrack>> =
            flowOf(stores.habits.filter { it.userId == userId && !it.isFinished })
    }

    /**
     * Fault-injecting [HabitTrackRepository]: `save` always throws, simulating a habit failure at
     * the exact point in the transaction where the goal has already been persisted (DQC-3.3).
     * Records how many times `save` was attempted so the test can assert the failure path was hit.
     */
    class FaultInjectingHabitTrackRepository : HabitTrackRepository {
        var saveAttempts: Int = 0
            private set

        override suspend fun findById(id: UUID): HabitTrack? = null
        override suspend fun findActiveByGoalId(goalId: UUID): List<HabitTrack> = emptyList()
        override suspend fun findActiveByUserId(userId: UUID): List<HabitTrack> = emptyList()
        override suspend fun save(track: HabitTrack): HabitTrack {
            saveAttempts++
            throw IllegalStateException("Injected habit-track persistence failure")
        }
        override suspend fun delete(id: UUID) { /* no-op fake */ }
        override fun observeActiveByUserId(userId: UUID): Flow<List<HabitTrack>> = flowOf(emptyList())
    }

    /** Minimal in-memory [ChangeLogRepository]; `append` appends to the shared store. */
    class InMemoryChangeLogRepository(private val stores: InMemoryStores) : ChangeLogRepository {
        override suspend fun append(entry: ChangeLogEntry) { stores.changeLog.add(entry) }
        override suspend fun findSince(userId: UUID, since: Instant): List<ChangeLogEntry> =
            stores.changeLog.filter { it.userId == userId && !it.timestamp.isBefore(since) }
        override suspend fun countByUserId(userId: UUID): Long =
            stores.changeLog.count { it.userId == userId }.toLong()
        override suspend fun archiveOlderThan(cutoff: Instant) {
            stores.changeLog.removeAll { it.timestamp.isBefore(cutoff) }
        }
        override fun observeRecent(userId: UUID, limit: Int, offset: Int): Flow<List<ChangeLogEntry>> =
            flowOf(stores.changeLog.filter { it.userId == userId }.drop(offset).take(limit))
    }
}
