package com.dopashift.app.di

import com.dopashift.domain.creation.CorrelationIdFactory
import com.dopashift.domain.creation.DeviceIdProvider
import com.dopashift.domain.creation.DomainClock
import com.dopashift.domain.creation.GoalSuggestionService
import com.dopashift.domain.creation.HabitAuthoringResolver
import com.dopashift.domain.creation.HabitPlanResult
import com.dopashift.domain.creation.HabitTemplateProvider
import com.dopashift.domain.creation.SuggestionResult
import com.dopashift.domain.creation.TransactionRunner
import com.dopashift.domain.creation.usecase.CreateDailyTodoInteractor
import com.dopashift.domain.creation.usecase.CreateDailyTodoUseCase
import com.dopashift.domain.creation.usecase.CreateGoalInteractor
import com.dopashift.domain.creation.usecase.CreateGoalUseCase
import com.dopashift.domain.creation.usecase.CreateGoalWithHabitInteractor
import com.dopashift.domain.creation.usecase.CreateGoalWithHabitUseCase
import com.dopashift.domain.creation.usecase.CreateHabitTrackInteractor
import com.dopashift.domain.creation.usecase.CreateHabitTrackUseCase
import com.dopashift.domain.creation.usecase.ObserveDashboardSummaryInteractor
import com.dopashift.domain.creation.usecase.ObserveDashboardSummaryUseCase
import com.dopashift.domain.creation.usecase.UndoCreationInteractor
import com.dopashift.domain.creation.usecase.UndoCreationUseCase
import com.dopashift.domain.repository.ChangeLogRepository
import com.dopashift.domain.repository.DailyTodoRepository
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the Dashboard Quick-Create use cases (task 13.2), wiring the pure-Kotlin `domain`
 * interactors to the repository/service ports bound in [QuickCreateRepositoryModule].
 *
 * There is a single implementation per use case — the same instance the dedicated Goals, Habits,
 * and Daily Tasks screens use — so the Dashboard is not a parallel data path (DQC-1.10, DQC-6.1).
 * The three single-entity use cases ([CreateGoalUseCase], [CreateDailyTodoUseCase],
 * [CreateHabitTrackUseCase]) are provided here so they are reused verbatim by the Dashboard, the
 * dedicated screens, and the Intercept_Overlay.
 *
 * The interactors are pure Kotlin without `@Inject`-visible Hilt scoping of their own, so they are
 * constructed explicitly here from the injected ports. Their extra collaborators noted in task 10.1
 * — the [HabitAuthoringResolver], [DeviceIdProvider], and [DomainClock] — are supplied via Hilt:
 * the resolver is provided below from the bound [HabitTemplateProvider]; the clock and device-id
 * ports are bound in [QuickCreateRepositoryModule].
 */
@Module
@InstallIn(SingletonComponent::class)
object QuickCreateUseCaseModule {

    /**
     * The shared [HabitAuthoringResolver] (a plain `domain` class, not `@Inject`-annotated) that
     * both [CreateHabitTrackUseCase] and [CreateGoalWithHabitUseCase] use to resolve any authoring
     * mode to exactly 30 valid checkpoints (DQC-3.6, DQC-3.9).
     */
    @Provides
    @Singleton
    fun provideHabitAuthoringResolver(
        templateProvider: HabitTemplateProvider
    ): HabitAuthoringResolver = HabitAuthoringResolver(templateProvider)

    @Provides
    @Singleton
    fun provideCreateGoalUseCase(
        goalRepository: GoalRepository,
        changeLogRepository: ChangeLogRepository,
        deviceIdProvider: DeviceIdProvider,
        clock: DomainClock
    ): CreateGoalUseCase = CreateGoalInteractor(
        goalRepository = goalRepository,
        changeLogRepository = changeLogRepository,
        deviceIdProvider = deviceIdProvider,
        clock = clock
    )

    @Provides
    @Singleton
    fun provideCreateDailyTodoUseCase(
        dailyTodoRepository: DailyTodoRepository,
        changeLogRepository: ChangeLogRepository,
        deviceIdProvider: DeviceIdProvider,
        clock: DomainClock
    ): CreateDailyTodoUseCase = CreateDailyTodoInteractor(
        dailyTodoRepository = dailyTodoRepository,
        changeLogRepository = changeLogRepository,
        deviceIdProvider = deviceIdProvider,
        clock = clock
    )

    @Provides
    @Singleton
    fun provideCreateHabitTrackUseCase(
        goalRepository: GoalRepository,
        habitTrackRepository: HabitTrackRepository,
        changeLogRepository: ChangeLogRepository,
        authoringResolver: HabitAuthoringResolver,
        deviceIdProvider: DeviceIdProvider,
        clock: DomainClock
    ): CreateHabitTrackUseCase = CreateHabitTrackInteractor(
        goalRepository = goalRepository,
        habitTrackRepository = habitTrackRepository,
        changeLogRepository = changeLogRepository,
        authoringResolver = authoringResolver,
        deviceIdProvider = deviceIdProvider,
        clock = clock
    )

    /**
     * Inline_Goal_Capture (DQC-3.2, DQC-3.3, DQC-6.2). Requires the goal/habit/change-log repos, a
     * [TransactionRunner] for the atomic unit, plus the [HabitAuthoringResolver], [DeviceIdProvider],
     * and [DomainClock] the interactor takes per task 10.1.
     */
    @Provides
    @Singleton
    fun provideCreateGoalWithHabitUseCase(
        goalRepository: GoalRepository,
        habitTrackRepository: HabitTrackRepository,
        changeLogRepository: ChangeLogRepository,
        authoringResolver: HabitAuthoringResolver,
        transactionRunner: TransactionRunner,
        deviceIdProvider: DeviceIdProvider,
        clock: DomainClock
    ): CreateGoalWithHabitUseCase = CreateGoalWithHabitInteractor(
        goalRepository = goalRepository,
        habitTrackRepository = habitTrackRepository,
        changeLogRepository = changeLogRepository,
        authoringResolver = authoringResolver,
        transactionRunner = transactionRunner,
        deviceIdProvider = deviceIdProvider,
        clock = clock
    )

    /**
     * Undo (DQC-1.7): deletes each referenced entity through the standard delete path so a
     * Change_Log deletion event flows through the Sync_Engine, never a local-only rollback.
     */
    @Provides
    @Singleton
    fun provideUndoCreationUseCase(
        goalRepository: GoalRepository,
        habitTrackRepository: HabitTrackRepository,
        dailyTodoRepository: DailyTodoRepository,
        changeLogRepository: ChangeLogRepository,
        deviceIdProvider: DeviceIdProvider,
        clock: DomainClock
    ): UndoCreationUseCase = UndoCreationInteractor(
        goalRepository = goalRepository,
        habitTrackRepository = habitTrackRepository,
        dailyTodoRepository = dailyTodoRepository,
        changeLogRepository = changeLogRepository,
        deviceIdProvider = deviceIdProvider,
        clock = clock
    )

    /**
     * Single feed driving Zero_State / Partial_State / populated rendering (DQC-5.3), sourced from
     * the [com.dopashift.domain.creation.DashboardSummaryRepository] bound in
     * [QuickCreateRepositoryModule].
     */
    @Provides
    @Singleton
    fun provideObserveDashboardSummaryUseCase(
        repository: com.dopashift.domain.creation.DashboardSummaryRepository
    ): ObserveDashboardSummaryUseCase = ObserveDashboardSummaryInteractor(repository)

    /**
     * Mints a [com.dopashift.domain.creation.CorrelationId] at the origin of each Dashboard
     * creation action (DQC-6.3). Provided as the random production factory; tests substitute a
     * deterministic factory to assert one Correlation_ID propagates across an action (Property 17).
     */
    @Provides
    @Singleton
    fun provideCorrelationIdFactory(): CorrelationIdFactory = CorrelationIdFactory.Random

    /**
     * Default BYO-LLM suggestion service (DQC-2.5, DQC-3.7). No LLM provider is configured in v1,
     * so [GoalSuggestionService.isConfigured] is `false`: the UI disables "Suggest with AI" and
     * defaults habit authoring to Template rather than presenting an action that errors on tap.
     * A real provider-backed implementation replaces this binding once BYO-LLM keys are wired
     * (Requirement 15), with no change to the callers that already degrade gracefully.
     */
    @Provides
    @Singleton
    fun provideGoalSuggestionService(): GoalSuggestionService = UnconfiguredGoalSuggestionService

    /**
     * The unconfigured [GoalSuggestionService] used until a BYO-LLM provider is wired. It reports
     * `isConfigured = false` and never performs a network call; its suggestion/plan methods return
     * non-blocking failures so any accidental invocation degrades to Template without losing input.
     */
    private object UnconfiguredGoalSuggestionService : GoalSuggestionService {
        override val isConfigured: Boolean = false

        override suspend fun suggestKeywordsAndDescription(
            name: String,
            description: String?,
            keywords: List<String>
        ): SuggestionResult = SuggestionResult.Failure(reason = "No LLM provider is configured")

        override suspend fun generateHabitPlan(
            goalName: String,
            category: String,
            keywords: List<String>
        ): HabitPlanResult = HabitPlanResult.Failure(reason = "No LLM provider is configured")
    }
}
