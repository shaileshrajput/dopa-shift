// Feature: dashboard-quick-create, Task 14.2
// Validates:
//   DQC-1.6 — a successful creation emits a transient Undo confirmation whose Undo action
//             remains available for at least 5 s (UndoSnackbar.MIN_VISIBLE_MILLIS >= 5000).
//   DQC-6.3 — a single Correlation_ID is minted at the action's origin (openQuickCreate) and
//             propagated to the subsequent save and its diagnostic events.
//   DQC-6.4 — the four action kinds record the matching machine-parseable DiagnosticEventType
//             (QUICK_CREATE_OPENED, ENTITY_CREATED, CREATION_ABANDONED, UNDO_INVOKED).
// Also exercises save routing (saveGoal/saveTodo/saveHabit route to the correct use case, with
// the inline/attached habit split) and undo dispatch to UndoCreationUseCase.
package com.dopashift.ui.quickcreate

import com.dopashift.domain.creation.CategoryKeywordProvider
import com.dopashift.domain.creation.CorrelationId
import com.dopashift.domain.creation.CorrelationIdFactory
import com.dopashift.domain.creation.CreateGoalCommand
import com.dopashift.domain.creation.CreateHabitCommand
import com.dopashift.domain.creation.CreateTodoCommand
import com.dopashift.domain.creation.CreationResult
import com.dopashift.domain.creation.DashboardSummary
import com.dopashift.domain.creation.DiagnosticEventType
import com.dopashift.domain.creation.DiagnosticsEventSink
import com.dopashift.domain.creation.DomainClock
import com.dopashift.domain.creation.EntityRef
import com.dopashift.domain.creation.GoalSuggestionService
import com.dopashift.domain.creation.GoalWithHabit
import com.dopashift.domain.creation.HabitAuthoring
import com.dopashift.domain.creation.HabitPlanResult
import com.dopashift.domain.creation.HabitTemplate
import com.dopashift.domain.creation.HabitTemplateProvider
import com.dopashift.domain.creation.InlineGoalWithHabitCommand
import com.dopashift.domain.creation.OnboardingStatus
import com.dopashift.domain.creation.SuggestionResult
import com.dopashift.domain.creation.UndoToken
import com.dopashift.domain.creation.UserId
import com.dopashift.domain.creation.usecase.CreateDailyTodoUseCase
import com.dopashift.domain.creation.usecase.CreateGoalUseCase
import com.dopashift.domain.creation.usecase.CreateGoalWithHabitUseCase
import com.dopashift.domain.creation.usecase.CreateHabitTrackUseCase
import com.dopashift.domain.creation.usecase.ObserveDashboardSummaryUseCase
import com.dopashift.domain.creation.usecase.UndoCreationUseCase
import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import com.dopashift.domain.repository.SyncStatusProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Unit tests for [QuickCreateViewModel] (task 14.2).
 *
 * Every collaborator is an in-memory fake — the use cases record the command they receive and
 * return a pre-seeded [CreationResult]; [RecordingDiagnosticsSink] captures every recorded
 * (event, correlationId) pair; [FixedCorrelationIdFactory] mints a deterministic, monotonically
 * numbered id so the "one id at origin, propagated to the save" contract (DQC-6.3) is asserted
 * exactly rather than by identity of a random value.
 *
 * `viewModelScope` runs on [Dispatchers.Main], which is bound to this test's
 * [StandardTestDispatcher]; [advanceUntilIdle] drains the launched coroutines deterministically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuickCreateViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var createGoal: FakeCreateGoalUseCase
    private lateinit var createTodo: FakeCreateTodoUseCase
    private lateinit var createHabit: FakeCreateHabitUseCase
    private lateinit var createGoalWithHabit: FakeCreateGoalWithHabitUseCase
    private lateinit var undo: FakeUndoUseCase
    private lateinit var diagnostics: RecordingDiagnosticsSink
    private lateinit var correlationIds: FixedCorrelationIdFactory
    private lateinit var clock: FixedClock

    private lateinit var viewModel: QuickCreateViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        createGoal = FakeCreateGoalUseCase()
        createTodo = FakeCreateTodoUseCase()
        createHabit = FakeCreateHabitUseCase()
        createGoalWithHabit = FakeCreateGoalWithHabitUseCase()
        undo = FakeUndoUseCase()
        diagnostics = RecordingDiagnosticsSink()
        correlationIds = FixedCorrelationIdFactory()
        clock = FixedClock(today = LocalDate.of(2026, 9, 1))
        viewModel = QuickCreateViewModel(
            createGoal = createGoal,
            createTodo = createTodo,
            createHabit = createHabit,
            createGoalWithHabit = createGoalWithHabit,
            undo = undo,
            observeSummary = FakeObserveSummaryUseCase(),
            diagnostics = diagnostics,
            templates = NoopTemplateProvider,
            keywords = NoopKeywordProvider,
            suggestions = NoopSuggestionService,
            correlationIds = correlationIds,
            clock = clock,
            goalRepository = EmptyGoalRepository,
            habitTrackRepository = EmptyHabitTrackRepository,
            syncStatusProvider = NoSyncPendingProvider,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- Save routing (DQC-1.10: Dashboard drives the same use cases) ----------------

    @Test
    fun saveGoal_routesToCreateGoalUseCase_only() = runTest(dispatcher) {
        createGoal.result = successGoal()

        viewModel.openQuickCreate(ActiveSheet.Goal)
        viewModel.saveGoal(name = "Run a marathon", category = "Fitness", keywords = listOf("running"))
        advanceUntilIdle()

        assertEquals(1, createGoal.invocations.size)
        assertEquals(0, createTodo.invocations.size)
        assertEquals(0, createHabit.invocations.size)
        assertEquals(0, createGoalWithHabit.invocations.size)
        val cmd = createGoal.invocations.single()
        assertEquals("Run a marathon", cmd.name)
        assertEquals("Fitness", cmd.category)
        assertEquals(listOf("running"), cmd.keywords)
    }

    @Test
    fun saveTodo_routesToCreateTodoUseCase_withTodayFromClock() = runTest(dispatcher) {
        createTodo.result = successTodo()

        viewModel.openQuickCreate(ActiveSheet.Todo)
        viewModel.saveTodo(text = "Buy groceries")
        advanceUntilIdle()

        assertEquals(1, createTodo.invocations.size)
        assertEquals(0, createGoal.invocations.size)
        val cmd = createTodo.invocations.single()
        assertEquals("Buy groceries", cmd.text)
        // dayDate is today in the user's tz, taken from DomainClock (DQC-4.3).
        assertEquals(LocalDate.of(2026, 9, 1), cmd.dayDate)
    }

    @Test
    fun saveHabit_withGoalId_routesToCreateHabitTrackUseCase() = runTest(dispatcher) {
        createHabit.result = successHabit()
        val goalId = UUID.randomUUID()

        viewModel.openQuickCreate(ActiveSheet.Habit)
        viewModel.saveHabit(goalId = goalId, authoring = HabitAuthoring.Template("fitness"))
        advanceUntilIdle()

        assertEquals(1, createHabit.invocations.size)
        assertEquals(0, createGoalWithHabit.invocations.size)
        assertEquals(goalId, createHabit.invocations.single().goalId)
    }

    @Test
    fun saveHabit_withoutGoalId_routesToInlineGoalCaptureUseCase() = runTest(dispatcher) {
        createGoalWithHabit.result = successGoalWithHabit()

        viewModel.openQuickCreate(ActiveSheet.Habit)
        viewModel.saveHabit(
            goalId = null,
            authoring = HabitAuthoring.Template("fitness"),
            inlineGoal = InlineGoalFields(
                name = "Get fit",
                category = "Fitness",
                keywords = listOf("gym"),
            ),
        )
        advanceUntilIdle()

        assertEquals(1, createGoalWithHabit.invocations.size)
        assertEquals(0, createHabit.invocations.size)
        assertEquals("Get fit", createGoalWithHabit.invocations.single().goal.name)
    }

    // ---- Undo dispatch (DQC-1.7) + UNDO_INVOKED (DQC-6.4) ----------------------------

    @Test
    fun onUndo_dispatchesTokenToUndoUseCase_andRecordsUndoInvoked() = runTest(dispatcher) {
        undo.result = CreationResult.Success(Unit, undoToken(correlationId = "undo-corr"))
        val token = undoToken(correlationId = "undo-corr")

        viewModel.onUndo(token)
        advanceUntilIdle()

        assertEquals(1, undo.invocations.size)
        assertSame(token, undo.invocations.single())
        // UNDO_INVOKED is recorded against the token's Correlation_ID (DQC-6.4).
        assertTrue(
            diagnostics.events.any {
                it.type == DiagnosticEventType.UNDO_INVOKED && it.correlationId.value == "undo-corr"
            }
        )
    }

    // ---- Snackbar emission with the >=5s Undo contract (DQC-1.6) ---------------------

    @Test
    fun successfulCreation_emitsUndoSnackbar_carryingUndoTokenAndMinVisible5s() = runTest(dispatcher) {
        val token = undoToken(correlationId = "1")
        createGoal.result = CreationResult.Success(goalProfile(), token)

        val emitted = mutableListOf<UndoSnackbar>()
        val collector = launch { viewModel.snackbar.collect { emitted.add(it) } }
        advanceUntilIdle()

        viewModel.openQuickCreate(ActiveSheet.Goal)
        viewModel.saveGoal(name = "Ship v1", category = "Career Growth", keywords = listOf("focus"))
        advanceUntilIdle()

        assertEquals(1, emitted.size)
        // The snackbar carries the UndoToken so Undo can delete via the standard path (DQC-1.7).
        assertSame(token, emitted.single().undoToken)
        // The Undo action must remain available for at least 5 s (DQC-1.6).
        assertTrue(UndoSnackbar.MIN_VISIBLE_MILLIS >= 5_000L)

        collector.cancel()
    }

    // ---- Abandonment recording (DQC-6.4) + confirm-discard (DQC-1.9) -----------------

    @Test
    fun dismissSheetWithUnsavedInput_recordsCreationAbandoned_andEmitsConfirmDiscard() =
        runTest(dispatcher) {
            val events = mutableListOf<QuickCreateEvent>()
            val collector = launch { viewModel.events.collect { events.add(it) } }
            advanceUntilIdle()

            viewModel.openQuickCreate(ActiveSheet.Goal)
            advanceUntilIdle()
            viewModel.dismissSheetWithUnsavedInput()
            advanceUntilIdle()

            assertTrue(
                diagnostics.events.any { it.type == DiagnosticEventType.CREATION_ABANDONED }
            )
            assertTrue(events.any { it is QuickCreateEvent.ConfirmDiscard })

            collector.cancel()
        }

    // ---- One Correlation_ID minted at origin, propagated to the save (DQC-6.3) -------

    @Test
    fun openQuickCreate_mintsOneCorrelationId_recordsOpened_andPropagatesToSave() =
        runTest(dispatcher) {
            createGoal.result = successGoal()

            viewModel.openQuickCreate(ActiveSheet.Goal)
            advanceUntilIdle()
            viewModel.saveGoal(name = "Learn Kotlin", category = "Learning", keywords = listOf("kotlin"))
            advanceUntilIdle()

            // Exactly one id was minted at origin (openQuickCreate), not one-per-call.
            assertEquals(1, correlationIds.mintedCount)
            val originId = correlationIds.minted.single()

            // QUICK_CREATE_OPENED carries the origin id (DQC-6.4).
            val opened = diagnostics.events.single { it.type == DiagnosticEventType.QUICK_CREATE_OPENED }
            assertEquals(originId, opened.correlationId)

            // The same id propagated to the save command (DQC-6.3)...
            assertEquals(originId, createGoal.invocations.single().correlationId)
            // ...and to the ENTITY_CREATED diagnostic recorded after a successful save.
            val created = diagnostics.events.single { it.type == DiagnosticEventType.ENTITY_CREATED }
            assertEquals(originId, created.correlationId)
        }

    @Test
    fun successfulSave_closesSheet_andClearsActiveSheetToNone() = runTest(dispatcher) {
        createGoal.result = successGoal()

        viewModel.openQuickCreate(ActiveSheet.Goal)
        advanceUntilIdle()
        assertEquals(ActiveSheet.Goal, viewModel.activeSheet.value)

        viewModel.saveGoal(name = "Goal", category = "Fitness", keywords = listOf("k"))
        advanceUntilIdle()

        assertEquals(ActiveSheet.None, viewModel.activeSheet.value)
    }

    @Test
    fun validationError_keepsSheetOpen_andSurfacesFieldErrors() = runTest(dispatcher) {
        createGoal.result = CreationResult.ValidationError(mapOf("name" to "Name is required"))

        val events = mutableListOf<QuickCreateEvent>()
        val collector = launch { viewModel.events.collect { events.add(it) } }
        advanceUntilIdle()

        viewModel.openQuickCreate(ActiveSheet.Goal)
        viewModel.saveGoal(name = "", category = "Fitness", keywords = listOf("k"))
        advanceUntilIdle()

        // Sheet stays open so the user's input is not discarded (DQC-2.11).
        assertEquals(ActiveSheet.Goal, viewModel.activeSheet.value)
        val fieldErrors = events.filterIsInstance<QuickCreateEvent.FieldErrors>().single()
        assertEquals("Name is required", fieldErrors.fieldErrors["name"])

        collector.cancel()
    }

    // ---- Fakes & builders ------------------------------------------------------------

    private fun successGoal() = CreationResult.Success(goalProfile(), undoToken())
    private fun successTodo() = CreationResult.Success(todoItem(), undoToken())
    private fun successHabit() = CreationResult.Success(habitTrack(), undoToken())
    private fun successGoalWithHabit() =
        CreationResult.Success(GoalWithHabit(goalProfile(), habitTrack()), undoToken())

    private fun undoToken(correlationId: String = "corr"): UndoToken =
        UndoToken(
            entityRefs = listOf(EntityRef(type = "goal", id = UUID.randomUUID())),
            correlationId = CorrelationId(correlationId),
        )

    private fun goalProfile(name: String = "Goal") = GoalProfile(
        id = UUID.randomUUID(),
        userId = USER_ID,
        name = name,
        category = "Fitness",
        keywords = listOf("k"),
        createdAt = FIXED_INSTANT,
        updatedAt = FIXED_INSTANT,
    )

    private fun todoItem() = DailyTodoItem(
        id = UUID.randomUUID(),
        userId = USER_ID,
        text = "todo",
        dueDateTime = null,
        createdAt = FIXED_INSTANT,
        updatedAt = FIXED_INSTANT,
        dayDate = LocalDate.of(2026, 9, 1),
    )

    private fun habitTrack() = HabitTrack(
        id = UUID.randomUUID(),
        goalId = UUID.randomUUID(),
        userId = USER_ID,
        startDate = LocalDate.of(2026, 9, 1),
        currentDay = 1,
    )

    private class FakeCreateGoalUseCase : CreateGoalUseCase {
        val invocations = mutableListOf<CreateGoalCommand>()
        var result: CreationResult<GoalProfile> = CreationResult.Failure(IllegalStateException("unset"))
        override suspend fun invoke(command: CreateGoalCommand): CreationResult<GoalProfile> {
            invocations.add(command)
            return result
        }
    }

    private class FakeCreateTodoUseCase : CreateDailyTodoUseCase {
        val invocations = mutableListOf<CreateTodoCommand>()
        var result: CreationResult<DailyTodoItem> = CreationResult.Failure(IllegalStateException("unset"))
        override suspend fun invoke(command: CreateTodoCommand): CreationResult<DailyTodoItem> {
            invocations.add(command)
            return result
        }
    }

    private class FakeCreateHabitUseCase : CreateHabitTrackUseCase {
        val invocations = mutableListOf<CreateHabitCommand>()
        var result: CreationResult<HabitTrack> = CreationResult.Failure(IllegalStateException("unset"))
        override suspend fun invoke(command: CreateHabitCommand): CreationResult<HabitTrack> {
            invocations.add(command)
            return result
        }
    }

    private class FakeCreateGoalWithHabitUseCase : CreateGoalWithHabitUseCase {
        val invocations = mutableListOf<InlineGoalWithHabitCommand>()
        var result: CreationResult<GoalWithHabit> = CreationResult.Failure(IllegalStateException("unset"))
        override suspend fun invoke(command: InlineGoalWithHabitCommand): CreationResult<GoalWithHabit> {
            invocations.add(command)
            return result
        }
    }

    private class FakeUndoUseCase : UndoCreationUseCase {
        val invocations = mutableListOf<UndoToken>()
        var result: CreationResult<Unit> = CreationResult.Success(Unit, dummyToken())
        override suspend fun invoke(token: UndoToken): CreationResult<Unit> {
            invocations.add(token)
            return result
        }

        private companion object {
            fun dummyToken() = UndoToken(
                entityRefs = listOf(EntityRef("goal", UUID.randomUUID())),
                correlationId = CorrelationId("dummy"),
            )
        }
    }

    private class FakeObserveSummaryUseCase : ObserveDashboardSummaryUseCase {
        override fun invoke(userId: UserId): Flow<DashboardSummary> = flowOf(
            DashboardSummary(
                userId = userId,
                activeGoalCount = 0,
                todayTodoCount = 0,
                activeHabitCount = 0,
                onboardingStatus = OnboardingStatus.NOT_STARTED,
            )
        )
    }

    private class RecordingDiagnosticsSink : DiagnosticsEventSink {
        data class Recorded(val type: DiagnosticEventType, val correlationId: CorrelationId)

        val events = mutableListOf<Recorded>()
        override suspend fun record(
            event: DiagnosticEventType,
            correlationId: CorrelationId,
            meta: Map<String, String>,
        ) {
            events.add(Recorded(event, correlationId))
        }
    }

    /** Mints deterministic, monotonically-numbered ids so propagation can be asserted exactly. */
    private class FixedCorrelationIdFactory : CorrelationIdFactory {
        val minted = mutableListOf<CorrelationId>()
        val mintedCount: Int get() = minted.size
        private var counter = 0
        override fun newId(): CorrelationId =
            CorrelationId("corr-${counter++}").also { minted.add(it) }
    }

    private class FixedClock(private val today: LocalDate) : DomainClock {
        override fun now(): Instant = FIXED_INSTANT
        override fun today(): LocalDate = today
    }

    private object NoopTemplateProvider : HabitTemplateProvider {
        override fun templatesForCategory(category: String): List<HabitTemplate> = emptyList()
        override fun defaultTemplate(category: String): HabitTemplate =
            HabitTemplate(
                id = "default",
                category = category.ifBlank { "Fitness" },
                title = "Default",
                checkpointDescriptions = (1..30).map { "Day $it" },
            )
    }

    private object NoopKeywordProvider : CategoryKeywordProvider {
        override fun presetCategories(): List<String> = emptyList()
        override fun suggestedKeywords(category: String): List<String> = emptyList()
    }

    private object NoopSuggestionService : GoalSuggestionService {
        override val isConfigured: Boolean = false
        override suspend fun suggestKeywordsAndDescription(
            name: String,
            description: String?,
            keywords: List<String>,
        ): SuggestionResult = SuggestionResult.Failure("not configured")

        override suspend fun generateHabitPlan(
            goalName: String,
            category: String,
            keywords: List<String>,
        ): HabitPlanResult = HabitPlanResult.Failure("not configured")
    }

    private object EmptyGoalRepository : GoalRepository {
        override suspend fun findById(id: UUID): GoalProfile? = null
        override suspend fun findByUserId(userId: UUID): List<GoalProfile> = emptyList()
        override suspend fun findByUserIdAndName(userId: UUID, name: String): GoalProfile? = null
        override suspend fun save(goal: GoalProfile): GoalProfile = goal
        override suspend fun delete(id: UUID) = Unit
        override suspend fun countActiveByUserId(userId: UUID): Int = 0
        override fun observeByUserId(userId: UUID): Flow<List<GoalProfile>> = flowOf(emptyList())
    }

    private object EmptyHabitTrackRepository : HabitTrackRepository {
        override suspend fun findById(id: UUID): HabitTrack? = null
        override suspend fun findActiveByGoalId(goalId: UUID): List<HabitTrack> = emptyList()
        override suspend fun findActiveByUserId(userId: UUID): List<HabitTrack> = emptyList()
        override suspend fun save(track: HabitTrack): HabitTrack = track
        override suspend fun delete(id: UUID) = Unit
        override fun observeActiveByUserId(userId: UUID): Flow<List<HabitTrack>> = flowOf(emptyList())
    }

    private object NoSyncPendingProvider : SyncStatusProvider {
        override fun observeSyncPending(): Flow<Boolean> = flowOf(false)
    }

    private companion object {
        val USER_ID: UserId = UUID.fromString("00000000-0000-0000-0000-000000000000")
        val FIXED_INSTANT: Instant = Instant.parse("2026-09-01T00:00:00Z")
    }
}
