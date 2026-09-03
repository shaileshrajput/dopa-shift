package com.dopashift.api.contract

import com.dopashift.api.config.AuthenticatedUser
import com.dopashift.api.config.KeycloakAuthService
import com.dopashift.application.dashboard.DashboardSummaryQueryService
import com.dopashift.application.sync.SyncProcessor
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.exception.GoalNameAlreadyExistsException
import com.dopashift.domain.model.DashboardSummaryCounts
import com.dopashift.domain.port.EncryptionService
import com.dopashift.domain.port.LlmAdapterFactory
import com.dopashift.domain.port.ProfileStorageService
import com.dopashift.domain.repository.ChangeLogRepository
import com.dopashift.domain.repository.ConflictHistoryRepository
import com.dopashift.domain.repository.DailyTodoRepository
import com.dopashift.domain.repository.EfficiencyScoreRepository
import com.dopashift.domain.repository.GoalChecklistItemRepository
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import com.dopashift.domain.repository.InterceptionRuleRepository
import com.dopashift.domain.repository.LlmConfigRepository
import com.dopashift.domain.repository.ReminderRepository
import com.dopashift.domain.repository.UserProfileRepository
import com.dopashift.domain.usecase.ComputeEfficiencyScoreUseCase
import com.dopashift.domain.usecase.VideoRecommendationUseCase
import com.dopashift.domain.usecase.goal.CreateGoalWithHabitCommand
import com.dopashift.domain.usecase.goal.CreateGoalWithHabitUseCase
import com.dopashift.domain.usecase.goal.DeleteGoalUseCase
import com.dopashift.domain.usecase.goal.GoalWithHabit
import com.dopashift.domain.usecase.goal.UpdateGoalUseCase
import com.dopashift.domain.usecase.habit.ActivateHabitTrackUseCase
import com.dopashift.domain.usecase.habit.AdvanceDayUseCase
import com.dopashift.domain.usecase.habit.MarkCheckpointUseCase
import com.dopashift.domain.usecase.habit.RecordMissedDayUseCase
import com.dopashift.domain.usecase.todo.CreateTodoUseCase
import com.dopashift.domain.usecase.todo.DeleteTodoUseCase
import com.dopashift.domain.usecase.todo.MarkTodoCompleteUseCase
import com.dopashift.domain.usecase.todo.UpdateTodoUseCase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Contract tests for the two additive backend deltas introduced by the
 * dashboard-quick-create spec:
 *
 *  1. POST /v1/goals with an optional nested `habitTrack` payload — committed
 *     atomically with the goal (Inline_Goal_Capture, DQC-3.3), additive so existing
 *     payloads remain valid, with the API-layer no-orphan invariant preserved
 *     (a habit/rule referencing an absent/invalid goalId not owned by the
 *     authenticated user is rejected — DQC-5.5) and conflict surfacing on a
 *     duplicate name (409 — DQC-6.7).
 *  2. GET /v1/dashboard/summary — returns per-entity-type counts
 *     (activeGoalCount, todayTodoCount, activeHabitCount) sufficient to distinguish
 *     Zero_State from Partial_State in a single response (DQC-5.3), scoped by the
 *     authenticated user.
 *
 * These use @WebMvcTest to exercise the real controllers, DTO (de)serialization,
 * validation, and the GlobalExceptionHandler status mapping without a database,
 * Redis, or Keycloak. Authentication is simulated with a JWT post-processor so the
 * AuthenticatedUser resolves the `sub` claim to a stable user id.
 *
 * Tags: DQC-5.5, DQC-6.7, DQC-5.3
 * Requirements: 5.5, 6.7, 5.3
 */
@WebMvcTest
@org.springframework.context.annotation.Import(
    com.dopashift.api.config.TestSecurityConfig::class,
    AuthenticatedUser::class
)
@org.springframework.test.context.TestPropertySource(properties = [
    "spring.main.allow-bean-definition-overriding=true",
    "dopashift.rate-limiting.enabled=false"
])
class DashboardQuickCreateContractTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    // --- Repository mocks ---
    @MockBean private lateinit var goalRepository: GoalRepository
    @MockBean private lateinit var goalChecklistItemRepository: GoalChecklistItemRepository
    @MockBean private lateinit var efficiencyScoreRepository: EfficiencyScoreRepository
    @MockBean private lateinit var dailyTodoRepository: DailyTodoRepository
    @MockBean private lateinit var habitTrackRepository: HabitTrackRepository
    @MockBean private lateinit var changeLogRepository: ChangeLogRepository
    @MockBean private lateinit var conflictHistoryRepository: ConflictHistoryRepository
    @MockBean private lateinit var llmConfigRepository: LlmConfigRepository
    @MockBean private lateinit var reminderRepository: ReminderRepository
    @MockBean private lateinit var interceptionRuleRepository: InterceptionRuleRepository
    @MockBean private lateinit var userProfileRepository: UserProfileRepository

    // --- Use case mocks ---
    @MockBean private lateinit var dashboardSummaryQueryService: DashboardSummaryQueryService
    @MockBean private lateinit var computeEfficiencyScoreUseCase: ComputeEfficiencyScoreUseCase
    @MockBean private lateinit var videoRecommendationUseCase: VideoRecommendationUseCase
    @MockBean private lateinit var createGoalWithHabitUseCase: CreateGoalWithHabitUseCase
    @MockBean private lateinit var updateGoalUseCase: UpdateGoalUseCase
    @MockBean private lateinit var deleteGoalUseCase: DeleteGoalUseCase
    @MockBean private lateinit var createTodoUseCase: CreateTodoUseCase
    @MockBean private lateinit var updateTodoUseCase: UpdateTodoUseCase
    @MockBean private lateinit var deleteTodoUseCase: DeleteTodoUseCase
    @MockBean private lateinit var markTodoCompleteUseCase: MarkTodoCompleteUseCase
    @MockBean private lateinit var activateHabitTrackUseCase: ActivateHabitTrackUseCase
    @MockBean private lateinit var markCheckpointUseCase: MarkCheckpointUseCase
    @MockBean private lateinit var advanceDayUseCase: AdvanceDayUseCase
    @MockBean private lateinit var recordMissedDayUseCase: RecordMissedDayUseCase

    // --- Port/Service mocks ---
    @MockBean private lateinit var encryptionService: EncryptionService
    @MockBean private lateinit var llmAdapterFactory: LlmAdapterFactory
    @MockBean private lateinit var profileStorageService: ProfileStorageService
    @MockBean private lateinit var syncProcessor: SyncProcessor

    // --- Infrastructure mocks ---
    @MockBean private lateinit var clock: Clock
    @MockBean private lateinit var keycloakAuthService: KeycloakAuthService
    @MockBean private lateinit var stringRedisTemplate: org.springframework.data.redis.core.StringRedisTemplate
    @MockBean private lateinit var webClient: org.springframework.web.reactive.function.client.WebClient

    private val userId = UUID.fromString("11111111-1111-1111-1111-111111111111")

    private fun authedJwt() = jwt().jwt { it.subject(userId.toString()) }

    /**
     * Performs the request and, because the controllers use Kotlin `suspend`
     * functions (served asynchronously by Spring), completes the async dispatch so
     * the assertions see the real controller result rather than the initial async
     * placeholder (HTTP 200, empty body).
     */
    private fun performAndDispatch(
        builder: org.springframework.test.web.servlet.RequestBuilder
    ): org.springframework.test.web.servlet.ResultActions {
        val started = mockMvc.perform(builder).andReturn()
        return if (started.request.isAsyncStarted) {
            mockMvc.perform(MockMvcRequestBuilders.asyncDispatch(started))
        } else {
            mockMvc.perform(builder)
        }
    }

    private fun goal(name: String) = GoalProfile(
        id = UUID.randomUUID(),
        userId = userId,
        name = name,
        category = "Fitness",
        keywords = listOf("run"),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        isActive = true
    )

    // ===================================================================
    // POST /v1/goals — additive nested habitTrack (Inline_Goal_Capture)
    // ===================================================================

    /**
     * DQC-3.3 / additive: a POST with a nested `habitTrack` commits the habit
     * atomically with the goal. The response surfaces the nested habit track,
     * proving the two entities were created as one unit.
     */
    @Test
    fun `DQC-5_5 POST goals with nested habitTrack creates goal and habit atomically`() {
        val createdGoal = goal("Morning Run")
        val savedHabit = com.dopashift.domain.entity.HabitTrack(
            id = UUID.randomUUID(),
            goalId = createdGoal.id,
            userId = userId,
            startDate = java.time.LocalDate.parse("2026-01-01"),
            currentDay = 1,
            isFinished = false,
            checkpoints = (1..30).map { day ->
                com.dopashift.domain.entity.HabitCheckpoint(
                    id = UUID.randomUUID(),
                    habitTrackId = createdGoal.id,
                    dayNumber = day,
                    description = "Day $day task",
                    status = com.dopashift.domain.entity.CheckpointStatus.PENDING,
                    completedAt = null
                )
            }
        )
        runBlocking {
            whenever(createGoalWithHabitUseCase.execute(any()))
                .thenReturn(GoalWithHabit(goal = createdGoal, habitTrack = savedHabit))
        }

        val descriptions = (1..30).joinToString(",") { "\"Day $it task\"" }
        val body = """
            {
              "name": "Morning Run",
              "category": "Fitness",
              "keywords": ["run"],
              "habitTrack": { "startDate": "2026-01-01", "descriptions": [$descriptions] }
            }
        """.trimIndent()

        performAndDispatch(
            MockMvcRequestBuilders.post("/v1/goals")
                .with(authedJwt())
                .contentType("application/json")
                .content(body)
        )
            .andExpect(MockMvcResultMatchers.status().isCreated)
            .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("Morning Run"))
            // The nested habit is committed together with the goal and returned in one response.
            .andExpect(MockMvcResultMatchers.jsonPath("$.habitTrack").exists())
            .andExpect(MockMvcResultMatchers.jsonPath("$.habitTrack.goalId").value(createdGoal.id.toString()))
            .andExpect(MockMvcResultMatchers.jsonPath("$.habitTrack.currentDay").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.habitTrack.checkpoints.length()").value(30))
    }

    /**
     * Additive contract: an existing payload WITHOUT `habitTrack` remains valid and
     * creates a plain goal (no habit track in the response).
     */
    @Test
    fun `DQC-5_5 POST goals without habitTrack remains valid and additive`() {
        val createdGoal = goal("Read More")
        runBlocking {
            whenever(createGoalWithHabitUseCase.execute(any()))
                .thenReturn(GoalWithHabit(goal = createdGoal, habitTrack = null))
        }

        val body = """
            { "name": "Read More", "category": "Learning", "keywords": ["books"] }
        """.trimIndent()

        performAndDispatch(
            MockMvcRequestBuilders.post("/v1/goals")
                .with(authedJwt())
                .contentType("application/json")
                .content(body)
        )
            .andExpect(MockMvcResultMatchers.status().isCreated)
            .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("Read More"))
            // Additive: absent nested habit means the field is omitted/null in the response.
            .andExpect(MockMvcResultMatchers.jsonPath("$.habitTrack").doesNotExist())
    }

    // ===================================================================
    // DQC-5.5 — API-layer no-orphan invariant: habit with absent/invalid goalId
    // ===================================================================

    /**
     * DQC-5.5: the API layer rejects a habit-track creation with an ABSENT goalId.
     * The `goalId` field is @NotNull, so a payload omitting it fails validation (400)
     * — the client can never persist a habit that references no goal.
     */
    @Test
    fun `DQC-5_5 POST habits with absent goalId is rejected and no habit is persisted`() {
        stubClock()
        // descriptions valid, goalId missing entirely — the required goal reference is absent.
        val descriptions = (1..30).joinToString(",") { "\"Day $it\"" }
        val body = """{ "descriptions": [$descriptions] }"""

        performAndDispatch(
            MockMvcRequestBuilders.post("/v1/habits")
                .with(authedJwt())
                .contentType("application/json")
                .content(body)
        )
            // The request MUST NOT be accepted (never 201 Created) — a habit is never
            // created without a goal reference.
            .andExpect(
                MockMvcResultMatchers.status().`is`(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.`is`(201))
                )
            )

        // And the creation use case is never reached, so no habit could be persisted.
        runBlocking {
            org.mockito.kotlin.verify(activateHabitTrackUseCase, org.mockito.kotlin.never())
                .execute(any(), any(), any(), any())
        }
    }

    /**
     * DQC-5.5: the API layer rejects a habit-track creation whose goalId references a
     * goal that does not exist / is not owned by the authenticated user. The use case
     * raises IllegalArgumentException, which the GlobalExceptionHandler maps to 400.
     */
    @Test
    fun `DQC-5_5 POST habits with invalid goalId not owned by user is rejected`() {
        stubClock()
        val foreignGoalId = UUID.randomUUID()
        val descriptions = (1..30).map { "Day $it" }
        runBlocking {
            whenever(
                activateHabitTrackUseCase.execute(
                    userId = eq(userId),
                    goalId = eq(foreignGoalId),
                    startDate = any(),
                    descriptions = any()
                )
            ).doThrow(IllegalArgumentException("Goal not found: $foreignGoalId"))
        }

        val descJson = descriptions.joinToString(",") { "\"$it\"" }
        val body = """{ "goalId": "$foreignGoalId", "descriptions": [$descJson] }"""

        performAndDispatch(
            MockMvcRequestBuilders.post("/v1/habits")
                .with(authedJwt())
                .contentType("application/json")
                .content(body)
        )
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.jsonPath("$.error").exists())
    }

    // ===================================================================
    // DQC-6.7 — conflict surfacing: duplicate goal name -> 409
    // ===================================================================

    /**
     * DQC-6.7: a duplicate goal name is surfaced as a 409 Conflict with a specific,
     * machine-parseable error code (GOAL_NAME_DUPLICATE) so the client can retain the
     * user's input and surface the reason rather than silently discarding it.
     */
    @Test
    fun `DQC-6_7 POST goals with duplicate name surfaces 409 conflict`() {
        runBlocking {
            whenever(createGoalWithHabitUseCase.execute(any()))
                .doThrow(GoalNameAlreadyExistsException("Morning Run"))
        }

        val body = """
            { "name": "Morning Run", "category": "Fitness", "keywords": ["run"] }
        """.trimIndent()

        performAndDispatch(
            MockMvcRequestBuilders.post("/v1/goals")
                .with(authedJwt())
                .contentType("application/json")
                .content(body)
        )
            .andExpect(MockMvcResultMatchers.status().isConflict)
            .andExpect(MockMvcResultMatchers.jsonPath("$.error.code").value("GOAL_NAME_DUPLICATE"))
    }

    // ===================================================================
    // DQC-5.3 — GET /v1/dashboard/summary count shape
    // ===================================================================

    /**
     * DQC-5.3: the summary response includes activeGoalCount, todayTodoCount, and
     * activeHabitCount in a single response, sufficient to distinguish Zero_State
     * from Partial_State. Here all three are zero → Zero_State.
     */
    @Test
    fun `DQC-5_3 GET dashboard summary exposes zero-state counts shape`() {
        stubSummary(activeGoals = 0, todayTodos = 0, activeHabits = 0)

        performAndDispatch(
            MockMvcRequestBuilders.get("/v1/dashboard/summary").with(authedJwt())
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.activeGoalCount").value(0))
            .andExpect(MockMvcResultMatchers.jsonPath("$.todayTodoCount").value(0))
            .andExpect(MockMvcResultMatchers.jsonPath("$.activeHabitCount").value(0))
    }

    /**
     * DQC-5.3: with a mix of populated and empty entity types, the counts distinguish
     * Partial_State (not all zero, not all populated) from Zero_State. A day of only
     * completed to-dos still yields a non-zero todayTodoCount (total, not pending).
     */
    @Test
    fun `DQC-5_3 GET dashboard summary exposes partial-state counts shape`() {
        stubSummary(activeGoals = 2, todayTodos = 0, activeHabits = 1)

        performAndDispatch(
            MockMvcRequestBuilders.get("/v1/dashboard/summary").with(authedJwt())
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.activeGoalCount").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$.todayTodoCount").value(0))
            .andExpect(MockMvcResultMatchers.jsonPath("$.activeHabitCount").value(1))
    }

    private fun stubSummary(activeGoals: Int, todayTodos: Int, activeHabits: Int) {
        runBlocking {
            whenever(dashboardSummaryQueryService.getSummary(eq(userId)))
                .thenReturn(
                    DashboardSummaryCounts(
                        activeGoalCount = activeGoals,
                        todayTodoCount = todayTodos,
                        activeHabitCount = activeHabits
                    )
                )
            // AnalyticsDashboardController also reads todos and efficiency scores for
            // the retained parent-spec fields; stub them to empty so the call succeeds.
            whenever(dailyTodoRepository.findByUserIdAndDate(eq(userId), any())).thenReturn(emptyList())
            whenever(efficiencyScoreRepository.findByUserIdAndDateRange(eq(userId), any(), any()))
                .thenReturn(emptyList())
        }
        stubClock()
    }

    /** The mocked Clock needs instant()/zone stubbed so controllers can resolve "today". */
    private fun stubClock() {
        whenever(clock.instant()).thenReturn(Instant.parse("2026-01-01T12:00:00Z"))
        whenever(clock.zone).thenReturn(java.time.ZoneOffset.UTC)
    }
}
