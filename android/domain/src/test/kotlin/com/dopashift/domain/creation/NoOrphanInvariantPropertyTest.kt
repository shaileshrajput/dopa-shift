package com.dopashift.domain.creation

import com.dopashift.domain.creation.usecase.CreateGoalWithHabitInteractor
import com.dopashift.domain.creation.usecase.CreateHabitTrackInteractor
import com.dopashift.domain.entity.ChangeLogEntry
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.repository.ChangeLogRepository
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import io.kotest.assertions.withClue
import io.kotest.core.Tag
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
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

/** One generated creation operation in a sequence, exercising one of the two creation paths. */
private sealed interface Op

/** Inline_Goal_Capture: create a goal + habit atomically; [fail] injects a habit-save failure. */
private data class InlineOp(val name: String, val category: String, val fail: Boolean) : Op

/**
 * Single-entity habit create. When [referenceExistingGoal] is true and a saved goal exists it
 * targets that goal; otherwise it targets a never-saved goal id (which must be rejected).
 */
private data class SingleOp(val referenceExistingGoal: Boolean, val category: String) : Op

/**
 * Property 7: No-Orphan Invariant Across All Creation Paths.
 *
 * *For any* sequence of creation operations from any Dashboard entry point, the persisted store
 * SHALL never contain a [HabitTrack] referencing a non-existent or unsaved [GoalProfile]
 * (DQC-3.4, upholding the parent's no-orphan-references invariant, parent Req 1 AC6).
 *
 * This test exercises BOTH creation paths that can produce a habit, driving them against the
 * SAME in-memory goal/habit stores so the invariant is checked across their interleaving:
 *  - the single-entity path [CreateHabitTrackInteractor] ([CreateHabitTrackUseCase]), which
 *    requires a pre-existing, saved goal; and
 *  - the Inline_Goal_Capture path [CreateGoalWithHabitInteractor] ([CreateGoalWithHabitUseCase]),
 *    which creates a goal and its dependent habit as one atomic transaction.
 *
 * After EVERY operation in a generated sequence — successes, validation errors, rejections, and
 * fault-injected transaction rollbacks — the test asserts the invariant holds over the whole
 * habit store: every persisted [HabitTrack.goalId] resolves to a [GoalProfile] present in the
 * goal store. A rollback (from a fault-injecting habit repository) must leave neither a goal nor
 * a habit behind, and a rejected single-entity create (missing goal) must persist no habit — in
 * every case the store never contains an orphan.
 *
 * **Validates: Requirements 3.4**
 *
 * Tags: Feature: dashboard-quick-create, Property 7 (satisfies DQC-7.2)
 */
class NoOrphanInvariantPropertyTest : StringSpec({

    tags(
        Tag("Feature: dashboard-quick-create"),
        Tag("Property 7")
    )

    // >= 100 iterations per the design Testing Strategy.
    val config = PropTestConfig(iterations = 200)

    // ---------------------------------------------------------------------------------
    // In-memory fakes: the SINGLE source of truth both creation paths write to.
    // ---------------------------------------------------------------------------------

    /**
     * A shared in-memory goal store. Both interactors read and write it so the no-orphan
     * check has one authoritative view of which goals exist.
     */
    class FakeGoalRepository : GoalRepository {
        val goals = LinkedHashMap<UUID, GoalProfile>()

        override suspend fun findById(id: UUID): GoalProfile? = goals[id]
        override suspend fun findByUserId(userId: UUID): List<GoalProfile> =
            goals.values.filter { it.userId == userId }
        override suspend fun findByUserIdAndName(userId: UUID, name: String): GoalProfile? =
            goals.values.firstOrNull { it.userId == userId && it.name.equals(name, ignoreCase = true) }
        override suspend fun save(goal: GoalProfile): GoalProfile {
            goals[goal.id] = goal
            return goal
        }
        override suspend fun delete(id: UUID) { goals.remove(id) }
        override suspend fun countActiveByUserId(userId: UUID): Int =
            goals.values.count { it.userId == userId && it.isActive }
        override fun observeByUserId(userId: UUID): Flow<List<GoalProfile>> =
            flowOf(goals.values.filter { it.userId == userId })
    }

    /**
     * A shared in-memory habit store with an optional [failNextSaves] switch that makes
     * [save] throw, modelling a persistence failure inside the Inline_Goal_Capture transaction.
     * When it throws, nothing is added to [tracks] — combined with the [TransactionRunner]
     * rolling back the goal write, the whole unit leaves no orphan.
     */
    class FakeHabitTrackRepository : HabitTrackRepository {
        val tracks = LinkedHashMap<UUID, HabitTrack>()
        var failNextSaves: Boolean = false

        override suspend fun findById(id: UUID): HabitTrack? = tracks[id]
        override suspend fun findActiveByGoalId(goalId: UUID): List<HabitTrack> =
            tracks.values.filter { it.goalId == goalId && !it.isFinished }
        override suspend fun findActiveByUserId(userId: UUID): List<HabitTrack> =
            tracks.values.filter { it.userId == userId && !it.isFinished }
        override suspend fun save(track: HabitTrack): HabitTrack {
            if (failNextSaves) error("Injected habit persistence failure")
            tracks[track.id] = track
            return track
        }
        override suspend fun delete(id: UUID) { tracks.remove(id) }
        override fun observeActiveByUserId(userId: UUID): Flow<List<HabitTrack>> =
            flowOf(tracks.values.filter { it.userId == userId && !it.isFinished })
    }

    /** A no-op change-log store; ordering/rollback of events is covered by other properties. */
    class FakeChangeLogRepository : ChangeLogRepository {
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

    /**
     * A [TransactionRunner] backed by the shared goal store so a mid-block failure rolls the
     * goal write back — mirroring Room's atomic transaction (DQC-3.3). It snapshots the goal
     * store before running the block and restores it if the block throws, so a failed
     * Inline_Goal_Capture leaves zero goals and zero habits (and therefore no orphan).
     */
    class RollbackTransactionRunner(private val goalRepo: FakeGoalRepository) : TransactionRunner {
        override suspend fun <T> inTransaction(block: suspend () -> T): T {
            val snapshot = LinkedHashMap(goalRepo.goals)
            return try {
                block()
            } catch (t: Throwable) {
                goalRepo.goals.clear()
                goalRepo.goals.putAll(snapshot)
                throw t
            }
        }
    }

    val fixedToday = LocalDate.of(2025, 6, 1)
    val fixedNow: Instant = Instant.parse("2025-06-01T09:00:00Z")
    val clock = object : DomainClock {
        override fun now(): Instant = fixedNow
        override fun today(): LocalDate = fixedToday
    }
    val deviceIdProvider = DeviceIdProvider { "test-device" }

    /** A template provider yielding a valid 30-checkpoint template for any category. */
    val templateProvider = object : HabitTemplateProvider {
        private fun template(category: String): HabitTemplate =
            HabitTemplate(
                id = "tmpl-${category.ifBlank { "general" }}",
                category = category.ifBlank { "general" },
                title = "Plan for ${category.ifBlank { "general" }}",
                checkpointDescriptions = (1..HabitTemplate.CHECKPOINT_COUNT).map { "Day $it micro-habit" }
            )

        override fun templatesForCategory(category: String): List<HabitTemplate> =
            listOf(template(category))
        override fun defaultTemplate(category: String): HabitTemplate = template(category)
    }
    val authoringResolver = HabitAuthoringResolver(templateProvider)

    /**
     * Assert the no-orphan invariant over the whole habit store: every persisted habit
     * references a goal that exists in the goal store (DQC-3.4).
     */
    suspend fun assertNoOrphans(goalRepo: FakeGoalRepository, habitRepo: FakeHabitTrackRepository) {
        habitRepo.tracks.values.forEach { track ->
            val goalExists = goalRepo.findById(track.goalId) != null
            withClue("Habit ${track.id} references goalId ${track.goalId} which is not in the goal store") {
                goalExists.shouldBeTrue()
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // Generators: a sequence of creation operations across BOTH paths.
    // ---------------------------------------------------------------------------------

    // Each operation is one of the creation paths, with inputs that produce a mix of
    // success, rejection (single-entity with a missing goal), and rollback (inline w/ fault).
    val userId: UUID = UUID.randomUUID()

    val nonBlank: Arb<String> = Arb.string(1..30).let { arb ->
        arbitrary { rs -> arb.bind().trim().ifBlank { "seed" } }
    }

    val authoringArb: Arb<HabitAuthoring> = arbitrary { rs ->
        when (Arb.int(0..2).bind()) {
            0 -> HabitAuthoring.Template("tmpl-any")
            1 -> HabitAuthoring.Llm(Arb.list(nonBlank, 1..30).bind())
            else -> HabitAuthoring.Manual(Arb.list(nonBlank, 1..30).bind())
        }
    }

    val opArb: Arb<Op> = arbitrary { rs ->
        when (Arb.int(0..2).bind()) {
            0, 1 -> InlineOp(
                name = nonBlank.bind() + "-" + Arb.int(0..1_000_000).bind(),
                category = nonBlank.bind(),
                fail = Arb.boolean().bind()
            )
            else -> SingleOp(
                referenceExistingGoal = Arb.boolean().bind(),
                category = nonBlank.bind()
            )
        }
    }

    val sequences: Arb<List<Op>> = Arb.list(opArb, 1..12)

    // ---------------------------------------------------------------------------------
    // Property: the invariant holds after every operation, across both paths interleaved.
    // ---------------------------------------------------------------------------------

    "Property 7 (DQC-3.4): no persisted habit ever references a missing/unsaved goal, across both creation paths" {
        checkAll(config, sequences) { ops ->
            runBlocking {
                val goalRepo = FakeGoalRepository()
                val habitRepo = FakeHabitTrackRepository()
                val changeLog = FakeChangeLogRepository()
                val tx = RollbackTransactionRunner(goalRepo)

                val inlineUseCase = CreateGoalWithHabitInteractor(
                    goalRepository = goalRepo,
                    habitTrackRepository = habitRepo,
                    changeLogRepository = changeLog,
                    authoringResolver = authoringResolver,
                    transactionRunner = tx,
                    deviceIdProvider = deviceIdProvider,
                    clock = clock
                )
                val singleUseCase = CreateHabitTrackInteractor(
                    goalRepository = goalRepo,
                    habitTrackRepository = habitRepo,
                    changeLogRepository = changeLog,
                    authoringResolver = authoringResolver,
                    deviceIdProvider = deviceIdProvider,
                    clock = clock
                )

                // The invariant holds vacuously on an empty store.
                assertNoOrphans(goalRepo, habitRepo)

                for (op in ops) {
                    when (op) {
                        is InlineOp -> {
                            habitRepo.failNextSaves = op.fail
                            val goalCommand = CreateGoalCommand(
                                userId = userId,
                                name = op.name,
                                category = op.category,
                                keywords = listOf("kw"),
                                correlationId = CorrelationId.random(),
                                origin = CreationOrigin.DASHBOARD
                            )
                            inlineUseCase(
                                InlineGoalWithHabitCommand(
                                    goal = goalCommand,
                                    authoring = HabitAuthoring.Template("tmpl-any"),
                                    correlationId = goalCommand.correlationId
                                )
                            )
                            habitRepo.failNextSaves = false
                        }

                        is SingleOp -> {
                            val existingGoal = goalRepo.goals.values
                                .firstOrNull { it.userId == userId }
                            val goalId = if (op.referenceExistingGoal && existingGoal != null) {
                                existingGoal.id
                            } else {
                                // A goal id that is NOT saved => single-entity path must reject
                                // and persist no habit (no-orphan).
                                UUID.randomUUID()
                            }
                            singleUseCase(
                                CreateHabitCommand(
                                    userId = userId,
                                    goalId = goalId,
                                    authoring = HabitAuthoring.Template("tmpl-any"),
                                    correlationId = CorrelationId.random(),
                                    origin = CreationOrigin.DASHBOARD
                                )
                            )
                        }
                    }

                    // The core assertion: after EVERY operation, no habit is orphaned.
                    assertNoOrphans(goalRepo, habitRepo)
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // Focused examples pinning down the two ways an orphan could otherwise arise.
    // ---------------------------------------------------------------------------------

    "Inline_Goal_Capture rollback leaves neither the goal nor the habit (no orphan)" {
        runBlocking {
            val goalRepo = FakeGoalRepository()
            val habitRepo = FakeHabitTrackRepository()
            val changeLog = FakeChangeLogRepository()
            val tx = RollbackTransactionRunner(goalRepo)
            val inlineUseCase = CreateGoalWithHabitInteractor(
                goalRepo, habitRepo, changeLog, authoringResolver, tx, deviceIdProvider, clock
            )

            habitRepo.failNextSaves = true
            val goalCommand = CreateGoalCommand(
                userId = userId,
                name = "Learn Kotlin",
                category = "Learning",
                keywords = listOf("kotlin"),
                correlationId = CorrelationId.random()
            )
            inlineUseCase(
                InlineGoalWithHabitCommand(
                    goal = goalCommand,
                    authoring = HabitAuthoring.Template("tmpl-any"),
                    correlationId = goalCommand.correlationId
                )
            )

            goalRepo.goals.size shouldBe 0
            habitRepo.tracks.size shouldBe 0
            assertNoOrphans(goalRepo, habitRepo)
        }
    }

    "Single-entity habit create for a missing goal persists no habit (no orphan)" {
        runBlocking {
            val goalRepo = FakeGoalRepository()
            val habitRepo = FakeHabitTrackRepository()
            val changeLog = FakeChangeLogRepository()
            val singleUseCase = CreateHabitTrackInteractor(
                goalRepo, habitRepo, changeLog, authoringResolver, deviceIdProvider, clock
            )

            singleUseCase(
                CreateHabitCommand(
                    userId = userId,
                    goalId = UUID.randomUUID(), // never saved
                    authoring = HabitAuthoring.Template("tmpl-any"),
                    correlationId = CorrelationId.random()
                )
            )

            habitRepo.tracks.size shouldBe 0
            assertNoOrphans(goalRepo, habitRepo)
        }
    }

    "A successful inline create followed by a single-entity create against that goal keeps every habit linked" {
        runBlocking {
            val goalRepo = FakeGoalRepository()
            val habitRepo = FakeHabitTrackRepository()
            val changeLog = FakeChangeLogRepository()
            val tx = RollbackTransactionRunner(goalRepo)
            val inlineUseCase = CreateGoalWithHabitInteractor(
                goalRepo, habitRepo, changeLog, authoringResolver, tx, deviceIdProvider, clock
            )
            val singleUseCase = CreateHabitTrackInteractor(
                goalRepo, habitRepo, changeLog, authoringResolver, deviceIdProvider, clock
            )

            val goalCommand = CreateGoalCommand(
                userId = userId,
                name = "Get fit",
                category = "Fitness",
                keywords = listOf("run"),
                correlationId = CorrelationId.random()
            )
            inlineUseCase(
                InlineGoalWithHabitCommand(
                    goal = goalCommand,
                    authoring = HabitAuthoring.Template("tmpl-any"),
                    correlationId = goalCommand.correlationId
                )
            )
            val savedGoal = goalRepo.goals.values.first()

            singleUseCase(
                CreateHabitCommand(
                    userId = userId,
                    goalId = savedGoal.id,
                    authoring = HabitAuthoring.Manual(listOf("stretch")),
                    correlationId = CorrelationId.random()
                )
            )

            goalRepo.goals.size shouldBe 1
            habitRepo.tracks.size shouldBe 2
            assertNoOrphans(goalRepo, habitRepo)
        }
    }
})
