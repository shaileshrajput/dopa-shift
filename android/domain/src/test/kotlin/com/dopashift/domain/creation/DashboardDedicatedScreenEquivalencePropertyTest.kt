package com.dopashift.domain.creation

// Feature: dashboard-quick-create, Property 1: Dashboard and Dedicated-Screen Creation Are Equivalent

import com.dopashift.domain.creation.usecase.CreateDailyTodoInteractor
import com.dopashift.domain.creation.usecase.CreateGoalInteractor
import com.dopashift.domain.entity.ChangeLogEntry
import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.repository.ChangeLogRepository
import com.dopashift.domain.repository.DailyTodoRepository
import com.dopashift.domain.repository.GoalRepository
import io.kotest.core.Tag
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
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
 * Property 1: Dashboard and Dedicated-Screen Creation Are Equivalent.
 *
 * Requirement 1.10 / 6.1: every entity creatable from the Dashboard is created through the
 * SAME underlying use case, the SAME validation rules, and the SAME Change_Log emission as the
 * dedicated screen — the Dashboard path is NOT a parallel implementation.
 *
 * This test proves that by driving each single-entity creation use case
 * ([CreateGoalInteractor], [CreateDailyTodoInteractor]) TWICE over the SAME input — once with a
 * [CreationOrigin.DASHBOARD] command and once with a [CreationOrigin.DEDICATED_SCREEN] command —
 * against independent but identically-configured in-memory fakes, and asserting the two runs
 * produce:
 *   1. the SAME accept/reject outcome (the same [CreationResult] variant, including identical
 *      field-error maps on rejection), and
 *   2. the SAME sequence of Change_Log events, compared modulo the values that are expected to
 *      differ per run (each entry's own `id`, the created entity's `entityId`, and `timestamp`)
 *      and modulo `origin` (which is carried on the command, never written into a Change_Log
 *      event). A fixed [DomainClock] and [DeviceIdProvider] pin the remaining fields so any
 *      surviving difference is a genuine behavioral divergence between the two origins.
 *
 * Because a single interactor instance backs both origins (there is no per-origin branch in the
 * production code), the only variable in play is the command's [CreationOrigin]; equivalence of
 * the observable outputs therefore demonstrates the absence of a parallel data path
 * (DQC-1.10, DQC-6.1).
 *
 * Kotest property testing, 200 iterations (>= 100 per the design Testing Strategy). Inputs span
 * both valid and invalid fields so the accept/reject-equivalence claim is exercised on both
 * branches.
 *
 * **Feature: dashboard-quick-create, Property 1**
 * **Validates: Requirements 1.10, 6.1** (satisfies DQC-7.1)
 */
class DashboardDedicatedScreenEquivalencePropertyTest : StringSpec({

    tags(
        Tag("Feature: dashboard-quick-create"),
        Tag("Property 1")
    )

    val config = PropTestConfig(iterations = 200)

    // --- Fakes ------------------------------------------------------------------------

    /** In-memory goal store; [save] persists and returns the goal so uniqueness reads see it. */
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

    /** In-memory to-do store; [save] persists and returns the item; count feeds the daily cap. */
    class FakeDailyTodoRepository : DailyTodoRepository {
        val saved = LinkedHashMap<UUID, DailyTodoItem>()

        override suspend fun findById(id: UUID): DailyTodoItem? = saved[id]
        override suspend fun findByUserIdAndDate(userId: UUID, date: LocalDate): List<DailyTodoItem> =
            saved.values.filter { it.userId == userId && it.dayDate == date }
        override suspend fun countByUserIdAndDate(userId: UUID, date: LocalDate): Int =
            saved.values.count { it.userId == userId && it.dayDate == date }
        override suspend fun save(item: DailyTodoItem): DailyTodoItem {
            saved[item.id] = item
            return item
        }
        override suspend fun delete(id: UUID) { saved.remove(id) }
        override fun observeByUserIdAndDate(userId: UUID, date: LocalDate): Flow<List<DailyTodoItem>> =
            emptyFlow()
    }

    /** Recording change-log fake that preserves append order for event-sequence comparison. */
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

    // Deterministic clock/device so any remaining event difference is a real divergence.
    val fixedInstant = Instant.parse("2025-01-01T00:00:00Z")
    val fixedToday = LocalDate.of(2025, 1, 1)
    val clock = object : DomainClock {
        override fun now(): Instant = fixedInstant
        override fun today(): LocalDate = fixedToday
    }
    val deviceIdProvider = DeviceIdProvider { "device-under-test" }

    /**
     * Project a recorded [ChangeLogEntry] onto the fields that MUST match across origins.
     *
     * Excluded on purpose: `id` (fresh per entry), `entityId` (the created entity gets a fresh
     * random id each run), and `timestamp` (pinned here but conceptually per-run). `origin` is
     * never part of a Change_Log entry — it lives on the command — so equivalence of these
     * projections is exactly the "modulo ids/timestamps/origin" comparison the property requires.
     */
    fun ChangeLogEntry.normalized(): List<String?> =
        listOf(entityType, field, value, deviceId, userId.toString())

    fun List<ChangeLogEntry>.normalizedSequence(): List<List<String?>> = map { it.normalized() }

    /** Reduce a [CreationResult] to a comparable accept/reject descriptor (ids/values excluded). */
    fun CreationResult<*>.outcomeDescriptor(): Any =
        when (this) {
            is CreationResult.Success -> "Success"
            is CreationResult.ValidationError -> "ValidationError" to fieldErrors
            is CreationResult.Rejected -> "Rejected" to reason
            is CreationResult.Failure -> "Failure" to (cause::class.qualifiedName ?: "unknown")
        }

    // --- Generators -------------------------------------------------------------------

    // Goal name spanning valid (1..NAME_MAX) and invalid (empty / over-long) ranges.
    val goalNames = Arb.string(0..(GoalValidator.NAME_MAX + 10))
    val categories = Arb.string(0..(GoalValidator.CATEGORY_MAX + 10))
    val keywordLists = Arb.list(
        Arb.string(0..(GoalValidator.KEYWORD_MAX_LEN + 5)),
        0..(GoalValidator.KEYWORDS_MAX + 3)
    )

    // To-do text spanning valid, blank, and over-long ranges.
    val todoTexts = Arb.string(0..(TodoValidator.TEXT_MAX + 25))

    // --- Properties -------------------------------------------------------------------

    "Property 1 (DQC-1.10, 6.1): goal creation is identical for DASHBOARD vs DEDICATED_SCREEN" {
        checkAll(
            config,
            Arb.uuid(),
            goalNames,
            categories,
            keywordLists
        ) { userId, name, category, keywords ->
            // Same correlation id and same fields; only the origin differs between the two runs.
            val correlationId = CorrelationId.random()

            fun run(origin: CreationOrigin): Pair<CreationResult<GoalProfile>, RecordingChangeLogRepository> {
                val changeLog = RecordingChangeLogRepository()
                val interactor = CreateGoalInteractor(
                    goalRepository = FakeGoalRepository(),
                    changeLogRepository = changeLog,
                    deviceIdProvider = deviceIdProvider,
                    clock = clock
                )
                val command = CreateGoalCommand(
                    userId = userId,
                    name = name,
                    category = category,
                    keywords = keywords,
                    correlationId = correlationId,
                    origin = origin
                )
                return runCatchingBlocking { interactor.invoke(command) } to changeLog
            }

            val (dashboardResult, dashboardLog) = run(CreationOrigin.DASHBOARD)
            val (dedicatedResult, dedicatedLog) = run(CreationOrigin.DEDICATED_SCREEN)

            // Same accept/reject outcome.
            dashboardResult.outcomeDescriptor() shouldBe dedicatedResult.outcomeDescriptor()

            // Same Change_Log events (modulo ids/timestamps/origin).
            dashboardLog.entries.normalizedSequence() shouldBe dedicatedLog.entries.normalizedSequence()
        }
    }

    "Property 1 (DQC-1.10, 6.1): to-do creation is identical for DASHBOARD vs DEDICATED_SCREEN" {
        checkAll(
            config,
            Arb.uuid(),
            todoTexts,
            Arb.int(0, TodoValidator.DAILY_CAP + 5)
        ) { userId, text, preExistingCount ->
            val correlationId = CorrelationId.random()

            // Seed both runs' stores with the same number of pre-existing items for the day so the
            // daily-cap branch is exercised identically across origins.
            fun seededTodoRepo(): FakeDailyTodoRepository {
                val repo = FakeDailyTodoRepository()
                repeat(preExistingCount) {
                    repo.saved[UUID.randomUUID()] = DailyTodoItem(
                        id = UUID.randomUUID(),
                        userId = userId,
                        text = "seed",
                        dueDateTime = null,
                        isCompleted = false,
                        createdAt = fixedInstant,
                        updatedAt = fixedInstant,
                        dayDate = fixedToday
                    )
                }
                return repo
            }

            fun run(origin: CreationOrigin): Pair<CreationResult<DailyTodoItem>, RecordingChangeLogRepository> {
                val changeLog = RecordingChangeLogRepository()
                val interactor = CreateDailyTodoInteractor(
                    dailyTodoRepository = seededTodoRepo(),
                    changeLogRepository = changeLog,
                    deviceIdProvider = deviceIdProvider,
                    clock = clock
                )
                val command = CreateTodoCommand(
                    userId = userId,
                    text = text,
                    dayDate = fixedToday,
                    correlationId = correlationId,
                    origin = origin
                )
                return runCatchingBlocking { interactor.invoke(command) } to changeLog
            }

            val (dashboardResult, dashboardLog) = run(CreationOrigin.DASHBOARD)
            val (dedicatedResult, dedicatedLog) = run(CreationOrigin.DEDICATED_SCREEN)

            dashboardResult.outcomeDescriptor() shouldBe dedicatedResult.outcomeDescriptor()
            dashboardLog.entries.normalizedSequence() shouldBe dedicatedLog.entries.normalizedSequence()
        }
    }

    // Explicit example: a clearly valid goal accepts identically and emits the same event shape.
    "example: a valid goal yields Success with identical Change_Log events across origins" {
        val userId = UUID.randomUUID()
        val correlationId = CorrelationId.random()

        fun run(origin: CreationOrigin): Pair<CreationResult<GoalProfile>, RecordingChangeLogRepository> {
            val changeLog = RecordingChangeLogRepository()
            val interactor = CreateGoalInteractor(
                goalRepository = FakeGoalRepository(),
                changeLogRepository = changeLog,
                deviceIdProvider = deviceIdProvider,
                clock = clock
            )
            val result = kotlinx.coroutines.runBlocking {
                interactor.invoke(
                    CreateGoalCommand(
                        userId = userId,
                        name = "Get fit",
                        category = "Fitness",
                        keywords = listOf("gym", "running"),
                        correlationId = correlationId,
                        origin = origin
                    )
                )
            }
            return result to changeLog
        }

        val (dashboardResult, dashboardLog) = run(CreationOrigin.DASHBOARD)
        val (dedicatedResult, dedicatedLog) = run(CreationOrigin.DEDICATED_SCREEN)

        dashboardResult.outcomeDescriptor() shouldBe "Success"
        dashboardResult.outcomeDescriptor() shouldBe dedicatedResult.outcomeDescriptor()
        dashboardLog.entries.normalizedSequence() shouldBe dedicatedLog.entries.normalizedSequence()
    }
})

/** Bridge running a suspend creation call from a synchronous property block. */
private fun <T> runCatchingBlocking(block: suspend () -> T): T =
    kotlinx.coroutines.runBlocking { block() }
