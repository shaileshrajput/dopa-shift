package com.dopashift.domain.creation

// Feature: dashboard-quick-create, Property 8: Change_Log Ordering — Goal Before Dependent

import com.dopashift.domain.creation.usecase.CreateGoalWithHabitInteractor
import com.dopashift.domain.entity.ChangeLogEntry
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.repository.ChangeLogRepository
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import io.kotest.core.Tag
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
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
 * Property 8: Change_Log Ordering — Goal Before Dependent.
 *
 * Requirement 6.2: when Inline_Goal_Capture creates a Goal_Profile and a dependent entity in one
 * transaction, the goal's Change_Log events are emitted BEFORE the dependent entity's, so replay
 * on another device never produces a temporarily orphaned reference.
 *
 * The subject under test is [CreateGoalWithHabitInteractor] (task 10.1), driven with in-memory
 * fakes. A recording [ChangeLogRepository] fake captures the exact append order. For every
 * successful Inline_Goal_Capture, this test asserts that in the recorded append sequence EVERY
 * goal event index is strictly less than EVERY dependent (habit track + checkpoint) event index —
 * i.e. all goal `create` events precede all habit/checkpoint `create` events (DQC-6.2, DQC-7.7).
 *
 * Goal events are identified by `entityId == savedGoal.id`; dependent events are the track's own
 * events plus one event per checkpoint. The interactor persists a real goal and habit through the
 * fakes so the recorded ids are the true persisted ids — no mock stubs of the ordering itself.
 *
 * Kotest property testing, 200 iterations (>= 100 per the design Testing Strategy).
 *
 * **Feature: dashboard-quick-create, Property 8**
 * **Validates: Requirements 6.2** (also satisfies DQC-7.7)
 */
class ChangeLogGoalBeforeDependentOrderingPropertyTest : StringSpec({

    tags(
        Tag("Feature: dashboard-quick-create"),
        Tag("Property 8")
    )

    val config = PropTestConfig(iterations = 200)

    // --- Fakes ------------------------------------------------------------------------

    /** In-memory goal store; [save] persists and returns the goal so its id can be referenced. */
    class FakeGoalRepository : GoalRepository {
        val saved = LinkedHashMap<UUID, GoalProfile>()

        override suspend fun findById(id: UUID): GoalProfile? = saved[id]
        override suspend fun findByUserId(userId: UUID): List<GoalProfile> =
            saved.values.filter { it.userId == userId }
        override suspend fun findByUserIdAndName(userId: UUID, name: String): GoalProfile? =
            saved.values.firstOrNull { it.userId == userId && it.name == name }
        override suspend fun save(goal: GoalProfile): GoalProfile {
            saved[goal.id] = goal
            return goal
        }
        override suspend fun delete(id: UUID) { saved.remove(id) }
        override suspend fun countActiveByUserId(userId: UUID): Int =
            saved.values.count { it.userId == userId && it.isActive }
        override fun observeByUserId(userId: UUID): Flow<List<GoalProfile>> = emptyFlow()
    }

    /** In-memory habit-track store; [save] persists and returns the track. */
    class FakeHabitTrackRepository : HabitTrackRepository {
        val saved = LinkedHashMap<UUID, HabitTrack>()

        override suspend fun findById(id: UUID): HabitTrack? = saved[id]
        override suspend fun findActiveByGoalId(goalId: UUID): List<HabitTrack> =
            saved.values.filter { it.goalId == goalId && !it.isFinished }
        override suspend fun findActiveByUserId(userId: UUID): List<HabitTrack> =
            saved.values.filter { it.userId == userId && !it.isFinished }
        override suspend fun save(track: HabitTrack): HabitTrack {
            saved[track.id] = track
            return track
        }
        override suspend fun delete(id: UUID) { saved.remove(id) }
        override fun observeActiveByUserId(userId: UUID): Flow<List<HabitTrack>> = emptyFlow()
    }

    /** Recording change-log fake that preserves append order for the ordering assertion. */
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

    /** Template provider yielding a valid 30-checkpoint template for any category. */
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

    val fixedInstant = Instant.parse("2025-01-01T00:00:00Z")
    val fixedToday = LocalDate.of(2025, 1, 1)
    val clock = object : DomainClock {
        override fun now(): Instant = fixedInstant
        override fun today(): LocalDate = fixedToday
    }
    val deviceIdProvider = DeviceIdProvider { "device-under-test" }
    val resolver = HabitAuthoringResolver(templateProvider)

    fun newInteractor(
        goalRepo: GoalRepository,
        habitRepo: HabitTrackRepository,
        changeLog: ChangeLogRepository
    ) = CreateGoalWithHabitInteractor(
        goalRepository = goalRepo,
        habitTrackRepository = habitRepo,
        changeLogRepository = changeLog,
        authoringResolver = resolver,
        transactionRunner = ImmediateTransactionRunner,
        deviceIdProvider = deviceIdProvider,
        clock = clock
    )

    // --- Generators -------------------------------------------------------------------

    // Valid goal fields (within GoalValidator bounds) so Inline_Goal_Capture succeeds.
    val goalNames = Arb.string(1..GoalValidator.NAME_MAX).filter { it.trim().isNotEmpty() }
    val categories = Arb.string(1..GoalValidator.CATEGORY_MAX).filter { it.trim().isNotEmpty() }
    val keywords = Arb.list(
        Arb.string(1..GoalValidator.KEYWORD_MAX_LEN).filter { it.trim().isNotEmpty() },
        1..GoalValidator.KEYWORDS_MAX
    )

    // Non-blank descriptions within the checkpoint bound for LLM/Manual modes.
    val descriptions = Arb.string(1..HabitTemplate.DESCRIPTION_MAX).map { it.trim().ifBlank { "do the thing" } }

    // All three authoring modes, each carrying usable input.
    val authorings: Arb<HabitAuthoring> = Arb.bind(
        Arb.int(0, 2),
        Arb.list(descriptions, 1..40),
        Arb.string(1..12).map { "tmpl-$it" }
    ) { mode, descs, templateId ->
        when (mode) {
            0 -> HabitAuthoring.Template(templateId)
            1 -> HabitAuthoring.Llm(descs)
            else -> HabitAuthoring.Manual(descs)
        }
    }

    fun command(
        userId: UUID,
        name: String,
        category: String,
        kws: List<String>,
        authoring: HabitAuthoring,
        withReminder: Boolean
    ): InlineGoalWithHabitCommand {
        val correlationId = CorrelationId.random()
        return InlineGoalWithHabitCommand(
            goal = CreateGoalCommand(
                userId = userId,
                name = name,
                category = category,
                keywords = kws,
                correlationId = correlationId
            ),
            authoring = authoring,
            withReminder = withReminder,
            correlationId = correlationId
        )
    }

    // --- Properties -------------------------------------------------------------------

    "Property 8 (DQC-6.2): every goal event precedes every dependent habit/checkpoint event" {
        checkAll(
            config,
            Arb.uuid(),
            goalNames,
            categories,
            keywords,
            authorings,
            Arb.boolean()
        ) { userId, name, category, kws, authoring, withReminder ->
            val goalRepo = FakeGoalRepository()
            val habitRepo = FakeHabitTrackRepository()
            val changeLog = RecordingChangeLogRepository()
            val interactor = newInteractor(goalRepo, habitRepo, changeLog)

            val result = interactor.invoke(
                command(userId, name, category, kws, authoring, withReminder)
            )

            result.shouldBeInstanceOf<CreationResult.Success<GoalWithHabit>>()
            val goalId = result.value.goal.id
            val trackId = result.value.habit.id
            val checkpointIds = result.value.habit.checkpoints.map { it.id }.toSet()
            val dependentIds = checkpointIds + trackId

            // Partition the recorded append order into goal-owned vs dependent-owned indices.
            val goalIndices = changeLog.entries.indices.filter { changeLog.entries[it].entityId == goalId }
            val dependentIndices =
                changeLog.entries.indices.filter { changeLog.entries[it].entityId in dependentIds }

            // Both partitions are non-empty: at least one goal event and one dependent event exist.
            goalIndices.size.shouldBeGreaterThan(0)
            dependentIndices.size.shouldBeGreaterThan(0)

            // Ordering guarantee: the last goal event index is strictly before the first dependent
            // event index, so ALL goal events precede ALL dependent events (DQC-6.2, DQC-7.7).
            (goalIndices.max() < dependentIndices.min()) shouldBe true

            // Every recorded event belongs to either the goal or a dependent (no stray/foreign ids).
            (goalIndices.size + dependentIndices.size) shouldBe changeLog.entries.size
        }
    }

    "Property 8 (DQC-6.2): the track event precedes all of its own checkpoint events" {
        // A dependent-internal ordering check: within the habit's events, the track create events
        // come before the checkpoint create events, so a replay creates the track before its rows.
        checkAll(
            config,
            Arb.uuid(),
            goalNames,
            categories,
            keywords,
            authorings
        ) { userId, name, category, kws, authoring ->
            val goalRepo = FakeGoalRepository()
            val habitRepo = FakeHabitTrackRepository()
            val changeLog = RecordingChangeLogRepository()
            val interactor = newInteractor(goalRepo, habitRepo, changeLog)

            val result = interactor.invoke(
                command(userId, name, category, kws, authoring, withReminder = false)
            )

            result.shouldBeInstanceOf<CreationResult.Success<GoalWithHabit>>()
            val trackId = result.value.habit.id
            val checkpointIds = result.value.habit.checkpoints.map { it.id }.toSet()

            val trackIndices = changeLog.entries.indices.filter { changeLog.entries[it].entityId == trackId }
            val checkpointIndices =
                changeLog.entries.indices.filter { changeLog.entries[it].entityId in checkpointIds }

            trackIndices.size.shouldBeGreaterThan(0)
            checkpointIndices.size.shouldBeGreaterThan(0)
            (trackIndices.max() < checkpointIndices.min()) shouldBe true

            // Exactly 30 checkpoints are represented among the dependent events (DQC-3.6).
            checkpointIds.size shouldBe HabitTemplate.CHECKPOINT_COUNT
        }
    }

    // Explicit example: a Template-mode capture yields goal events first, then track, then 30 checkpoints.
    "example: goal 'name' event is recorded before the first checkpoint event" {
        val goalRepo = FakeGoalRepository()
        val habitRepo = FakeHabitTrackRepository()
        val changeLog = RecordingChangeLogRepository()
        val interactor = newInteractor(goalRepo, habitRepo, changeLog)

        val result = interactor.invoke(
            command(
                userId = UUID.randomUUID(),
                name = "Get fit",
                category = "Fitness",
                kws = listOf("gym", "running"),
                authoring = HabitAuthoring.Template("tmpl-Fitness"),
                withReminder = false
            )
        )

        result.shouldBeInstanceOf<CreationResult.Success<GoalWithHabit>>()
        val goalId = result.value.goal.id
        val checkpointIds = result.value.habit.checkpoints.map { it.id }.toSet()

        val entityTypesInOrder = changeLog.entries.map { it.entityType }
        // Goal events appear before HabitTrack and HabitCheckpoint events.
        entityTypesInOrder.shouldContainAll("GoalProfile", "HabitTrack", "HabitCheckpoint")

        val lastGoalIdx = changeLog.entries.indexOfLast { it.entityId == goalId }
        val firstCheckpointIdx = changeLog.entries.indexOfFirst { it.entityId in checkpointIds }
        (lastGoalIdx < firstCheckpointIdx) shouldBe true
    }
})

/**
 * A [TransactionRunner] that runs the block immediately in the calling coroutine — the pure-domain
 * analogue of Room's `withTransaction` for tests. On success the block's result is returned; a
 * thrown exception propagates (the interactor maps it to [CreationResult.Failure]).
 */
private object ImmediateTransactionRunner : TransactionRunner {
    override suspend fun <R> inTransaction(block: suspend () -> R): R = block()
}
