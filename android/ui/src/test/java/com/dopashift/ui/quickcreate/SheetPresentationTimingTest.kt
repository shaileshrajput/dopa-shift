// Feature: dashboard-quick-create, Task 18.3 — sheet-presentation timing integration test.
// Tag: DQC-1.3 — selecting a Quick_Create_Menu action presents the corresponding Creation_Sheet
//                within 300 ms of touch-up, measured to the sheet's first rendered frame.
//
// The sheet's first rendered frame is driven by the QuickCreateViewModel.activeSheet state
// transition: DashboardScreen presents the ModalBottomSheet keyed off activeSheet, so the
// wall-clock cost of `openQuickCreate(sheet)` flipping activeSheet from None to the requested
// sheet is the client-controlled portion of the 300 ms budget. This test asserts that transition
// is effectively immediate (well under 300 ms) for every action, and that it is synchronous (the
// state is observable on return, not deferred to a coroutine), so the composition can render on
// the very next frame.
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
import com.dopashift.domain.creation.GoalSuggestionService
import com.dopashift.domain.creation.GoalWithHabit
import com.dopashift.domain.creation.HabitAuthoring
import com.dopashift.domain.creation.HabitPlanResult
import com.dopashift.domain.creation.HabitTemplate
import com.dopashift.domain.creation.HabitTemplateProvider
import com.dopashift.domain.creation.InlineGoalWithHabitCommand
import com.dopashift.domain.creation.OnboardingStatus
import com.dopashift.domain.creation.SuggestionResult
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * DQC-1.3 sheet-presentation timing test for [QuickCreateViewModel] (task 18.3).
 *
 * All collaborators are trivial in-memory fakes (a sheet open touches none of the use cases; it
 * only mints a Correlation_ID and records QUICK_CREATE_OPENED asynchronously). The assertion is
 * that `openQuickCreate(sheet)` flips [QuickCreateViewModel.activeSheet] to the requested sheet
 * synchronously and far inside the 300 ms budget — the diagnostics `record` is dispatched on the
 * view-model scope and does not gate the state transition, so the sheet can render immediately.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SheetPresentationTimingTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var viewModel: QuickCreateViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = QuickCreateViewModel(
            createGoal = NoopCreateGoal,
            createTodo = NoopCreateTodo,
            createHabit = NoopCreateHabit,
            createGoalWithHabit = NoopCreateGoalWithHabit,
            undo = NoopUndo,
            observeSummary = NoopObserveSummary,
            diagnostics = NoopDiagnosticsSink,
            templates = NoopTemplateProvider,
            keywords = NoopKeywordProvider,
            suggestions = NoopSuggestionService,
            correlationIds = MonotonicCorrelationIdFactory(),
            clock = FixedClock(LocalDate.of(2026, 9, 1)),
            goalRepository = NoopGoalRepository,
            habitTrackRepository = NoopHabitTrackRepository,
            syncStatusProvider = NoopSyncStatusProvider,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * The activeSheet transition (which drives the sheet's first frame) completes within the
     * 300 ms budget for each of the three Quick_Create_Menu actions (DQC-1.3, DQC-1.2 order).
     */
    @Test
    fun `each creation sheet is presented within 300 ms of selection`() = runTest(dispatcher) {
        val sheets = listOf(ActiveSheet.Goal, ActiveSheet.Habit, ActiveSheet.Todo)

        for (sheet in sheets) {
            // Reset to a dismissed state before each selection.
            viewModel.dismissSheet()
            assertEquals(ActiveSheet.None, viewModel.activeSheet.value)

            val start = System.nanoTime()
            viewModel.openQuickCreate(sheet)
            // The sheet is presentable the moment activeSheet reflects the selection; the
            // diagnostics record is a fire-and-forget coroutine and must NOT be awaited here.
            val presented = viewModel.activeSheet.value
            val elapsedMillis = (System.nanoTime() - start) / 1_000_000

            assertEquals("selecting $sheet presents that sheet", sheet, presented)
            assertTrue(
                "presenting $sheet must be within 300ms (DQC-1.3) but took ${elapsedMillis}ms",
                elapsedMillis <= 300
            )
        }
    }

    /**
     * The presentation is synchronous: activeSheet is already the requested sheet on return from
     * [QuickCreateViewModel.openQuickCreate], without advancing the dispatcher — so the ModalBottomSheet
     * can be composed on the next frame rather than after a coroutine hop (DQC-1.3).
     */
    @Test
    fun `sheet presentation is synchronous with the selection`() = runTest(dispatcher) {
        viewModel.openQuickCreate(ActiveSheet.Goal)
        // No advanceUntilIdle(): the state transition must already be visible.
        assertEquals(ActiveSheet.Goal, viewModel.activeSheet.value)
    }

    // ---- Trivial fakes ---------------------------------------------------------------

    private object NoopCreateGoal : CreateGoalUseCase {
        override suspend fun invoke(command: CreateGoalCommand): CreationResult<GoalProfile> =
            CreationResult.Failure(IllegalStateException("not used"))
    }

    private object NoopCreateTodo : CreateDailyTodoUseCase {
        override suspend fun invoke(command: CreateTodoCommand): CreationResult<DailyTodoItem> =
            CreationResult.Failure(IllegalStateException("not used"))
    }

    private object NoopCreateHabit : CreateHabitTrackUseCase {
        override suspend fun invoke(command: CreateHabitCommand): CreationResult<HabitTrack> =
            CreationResult.Failure(IllegalStateException("not used"))
    }

    private object NoopCreateGoalWithHabit : CreateGoalWithHabitUseCase {
        override suspend fun invoke(command: InlineGoalWithHabitCommand): CreationResult<GoalWithHabit> =
            CreationResult.Failure(IllegalStateException("not used"))
    }

    private object NoopUndo : UndoCreationUseCase {
        override suspend fun invoke(token: com.dopashift.domain.creation.UndoToken): CreationResult<Unit> =
            CreationResult.Failure(IllegalStateException("not used"))
    }

    private object NoopObserveSummary : ObserveDashboardSummaryUseCase {
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

    private object NoopDiagnosticsSink : DiagnosticsEventSink {
        override suspend fun record(
            event: DiagnosticEventType,
            correlationId: CorrelationId,
            meta: Map<String, String>,
        ) = Unit
    }

    private class MonotonicCorrelationIdFactory : CorrelationIdFactory {
        private var counter = 0
        override fun newId(): CorrelationId = CorrelationId("corr-${counter++}")
    }

    private class FixedClock(private val today: LocalDate) : DomainClock {
        override fun now(): Instant = Instant.parse("2026-09-01T00:00:00Z")
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

    private object NoopGoalRepository : GoalRepository {
        override suspend fun findById(id: UUID): GoalProfile? = null
        override suspend fun findByUserId(userId: UUID): List<GoalProfile> = emptyList()
        override suspend fun findByUserIdAndName(userId: UUID, name: String): GoalProfile? = null
        override suspend fun save(goal: GoalProfile): GoalProfile = goal
        override suspend fun delete(id: UUID) = Unit
        override suspend fun countActiveByUserId(userId: UUID): Int = 0
        override fun observeByUserId(userId: UUID): Flow<List<GoalProfile>> = flowOf(emptyList())
    }

    private object NoopHabitTrackRepository : HabitTrackRepository {
        override suspend fun findById(id: UUID): HabitTrack? = null
        override suspend fun findActiveByGoalId(goalId: UUID): List<HabitTrack> = emptyList()
        override suspend fun findActiveByUserId(userId: UUID): List<HabitTrack> = emptyList()
        override suspend fun save(track: HabitTrack): HabitTrack = track
        override suspend fun delete(id: UUID) = Unit
        override fun observeActiveByUserId(userId: UUID): Flow<List<HabitTrack>> = flowOf(emptyList())
    }

    private object NoopSyncStatusProvider : SyncStatusProvider {
        override fun observeSyncPending(): Flow<Boolean> = flowOf(false)
    }
}
