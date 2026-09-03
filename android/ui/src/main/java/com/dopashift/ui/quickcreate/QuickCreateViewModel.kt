package com.dopashift.ui.quickcreate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dopashift.domain.creation.CorrelationId
import com.dopashift.domain.creation.CorrelationIdFactory
import com.dopashift.domain.creation.CreateGoalCommand
import com.dopashift.domain.creation.CreateHabitCommand
import com.dopashift.domain.creation.CreateTodoCommand
import com.dopashift.domain.creation.CreationResult
import com.dopashift.domain.creation.DashboardSummary
import com.dopashift.domain.creation.DiagnosticEventType
import com.dopashift.domain.creation.DiagnosticsEventSink
import com.dopashift.domain.creation.GoalId
import com.dopashift.domain.creation.HabitAuthoring
import com.dopashift.domain.creation.InlineGoalWithHabitCommand
import com.dopashift.domain.creation.OnboardingStatus
import com.dopashift.domain.creation.ReminderDraft
import com.dopashift.domain.creation.UndoToken
import com.dopashift.domain.creation.UserId
import com.dopashift.domain.creation.usecase.CreateDailyTodoUseCase
import com.dopashift.domain.creation.usecase.CreateGoalUseCase
import com.dopashift.domain.creation.usecase.CreateGoalWithHabitUseCase
import com.dopashift.domain.creation.usecase.CreateHabitTrackUseCase
import com.dopashift.domain.creation.usecase.ObserveDashboardSummaryUseCase
import com.dopashift.domain.creation.usecase.UndoCreationUseCase
import com.dopashift.domain.creation.CategoryKeywordProvider
import com.dopashift.domain.creation.DomainClock
import com.dopashift.domain.creation.GoalSuggestionService
import com.dopashift.domain.creation.HabitTemplateProvider
import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.presentation.DefaultHabitGoalSelector
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import com.dopashift.domain.repository.SyncStatusProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * State holder for the Dashboard's quick-create surface (DQC-1, DQC-6).
 *
 * This ViewModel is the Dashboard's *creation* brain: it exposes the Zero/Partial/populated
 * [summary], the currently presented [activeSheet], and the transient [snackbar] carrying the
 * sync-safe Undo affordance. Every save routes to the *same* domain use case the dedicated
 * Goals/Habits/Daily-Tasks screens drive — there is no parallel data path (DQC-1.10, DQC-6.1).
 *
 * ### Correlation & diagnostics (DQC-6.3, DQC-6.4)
 * A [CorrelationId] is minted at the point of origin — [openQuickCreate] — and propagated
 * through the sheet's save call and every diagnostic event for that action. The four action
 * kinds are recorded via [DiagnosticsEventSink] with the machine-parseable event type:
 * [DiagnosticEventType.QUICK_CREATE_OPENED] on open, [DiagnosticEventType.ENTITY_CREATED] on a
 * successful save, [DiagnosticEventType.CREATION_ABANDONED] on a discard, and
 * [DiagnosticEventType.UNDO_INVOKED] on undo. Raw events stay local; only aggregated counts sync.
 *
 * ### No navigation away (DQC-1.5)
 * [summary] is observed from [ObserveDashboardSummaryUseCase]; because each successful creation
 * refreshes the underlying local counts, the Dashboard reflects the new entity reactively without
 * the user ever leaving the screen.
 *
 * The three [HabitTemplateProvider]/[CategoryKeywordProvider]/[GoalSuggestionService] collaborators
 * are injected here so the creation sheets (built in tasks 15.x) can source preset categories,
 * suggested keywords, bundled templates, and — when configured — AI suggestions without a second
 * state holder. LLM affordances are gated on [GoalSuggestionService.isConfigured] (DQC-2.5, DQC-3.7).
 */
@HiltViewModel
class QuickCreateViewModel @Inject constructor(
    private val createGoal: CreateGoalUseCase,
    private val createTodo: CreateDailyTodoUseCase,
    private val createHabit: CreateHabitTrackUseCase,
    private val createGoalWithHabit: CreateGoalWithHabitUseCase,
    private val undo: UndoCreationUseCase,
    private val observeSummary: ObserveDashboardSummaryUseCase,
    private val diagnostics: DiagnosticsEventSink,
    private val templates: HabitTemplateProvider,
    private val keywords: CategoryKeywordProvider,
    private val suggestions: GoalSuggestionService,
    private val correlationIds: CorrelationIdFactory,
    private val clock: DomainClock,
    private val goalRepository: GoalRepository,
    private val habitTrackRepository: HabitTrackRepository,
    private val syncStatusProvider: SyncStatusProvider,
) : ViewModel() {

    // TODO: Replace with the authenticated user id from the auth/session layer (Keycloak).
    // Every read/write is scoped by this id; no endpoint accepts another user's id alone.
    private val userId: UserId = UUID.fromString("00000000-0000-0000-0000-000000000000")

    /**
     * The single feed that drives Zero_State / Partial_State / populated rendering (DQC-5.3).
     * Observed (not fetched once) so each creation reflects on the Dashboard within 1 s (DQC-1.5).
     */
    val summary: StateFlow<DashboardSummary> = observeSummary(userId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = DashboardSummary(
            userId = userId,
            activeGoalCount = 0,
            todayTodoCount = 0,
            activeHabitCount = 0,
            onboardingStatus = OnboardingStatus.NOT_STARTED
        )
    )

    // The category the user has selected/typed in the currently open goal or habit sheet, used to
    // resolve bundled suggested keywords and the template category (DQC-2.3, DQC-3.6). Reset when a
    // sheet opens; updated via [onCategoryChanged].
    private val _selectedCategory = MutableStateFlow("")

    // The active goals feed, shared by the habit sheet (goal selection / default) and by goal-name
    // uniqueness in the goal sheet. Scoped to the authenticated user.
    private val goals: StateFlow<List<GoalProfile>> = goalRepository.observeByUserId(userId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = emptyList()
    )

    private val activeHabitTracks: StateFlow<List<HabitTrack>> =
        habitTrackRepository.observeActiveByUserId(userId).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList()
        )

    private val syncPending: StateFlow<Boolean> = syncStatusProvider.observeSyncPending().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = false
    )

    /**
     * State driving [GoalCreationSheet]: preset categories and per-category suggested keywords from
     * the bundled providers (DQC-2.2, DQC-2.3), the user's existing goal names for case-insensitive
     * uniqueness (DQC-2.6), and whether the LLM "Suggest with AI" action is available (DQC-2.5).
     */
    val goalSheetState: StateFlow<GoalSheetState> =
        combine(goals, _selectedCategory) { goalList, category ->
            GoalSheetState(
                presetCategories = keywords.presetCategories(),
                suggestedKeywords = if (category.isBlank()) emptyList()
                else keywords.suggestedKeywords(category),
                existingGoalNamesLower = goalList.asSequence()
                    .map { it.name.trim().lowercase() }
                    .toSet(),
                llmConfigured = suggestions.isConfigured,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = GoalSheetState(llmConfigured = suggestions.isConfigured)
        )

    /**
     * State driving [HabitCreationSheet]: the active goals for the selection step (or, when empty,
     * Inline_Goal_Capture), the most-recently-updated default goal (DQC-3.1), preset categories and
     * suggested keywords for the inline goal, and whether the selected goal already has an active
     * track for the precedence disclosure (DQC-3.11).
     */
    val habitSheetState: StateFlow<HabitSheetState> =
        combine(goals, activeHabitTracks, _selectedCategory) { goalList, tracks, category ->
            val defaultGoal = DefaultHabitGoalSelector.selectDefault(goalList)
            val templateCategory = when {
                category.isNotBlank() -> category
                else -> defaultGoal?.category
            }
            HabitSheetState(
                goals = goalList,
                defaultGoalId = defaultGoal?.id,
                presetCategories = keywords.presetCategories(),
                suggestedKeywords = if (category.isBlank()) emptyList()
                else keywords.suggestedKeywords(category),
                templateCategory = templateCategory,
                llmConfigured = suggestions.isConfigured,
                existingActiveTrackForSelectedGoal = defaultGoal != null &&
                    tracks.any { it.goalId == defaultGoal.id },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = HabitSheetState(llmConfigured = suggestions.isConfigured)
        )

    /**
     * State driving [TodoCreationSheet] and [TodaysFocusQuickAdd]: today's to-do count for the
     * daily-cap enforcement (DQC-4.6) and the offline/sync-pending indicator (DQC-4.9).
     */
    val todoSheetState: StateFlow<TodoSheetState> =
        combine(summary, syncPending) { summary, pending ->
            TodoSheetState(
                todayCount = summary.todayTodoCount,
                isOffline = pending,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = TodoSheetState()
        )

    /** State for the Today's Focus inline quick-add (DQC-1.4, DQC-4.2). */
    val quickAddState: StateFlow<QuickAddState> =
        combine(summary, syncPending) { summary, pending ->
            QuickAddState(
                todayCount = summary.todayTodoCount,
                isOffline = pending,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = QuickAddState()
        )

    /**
     * Resolves the concrete [HabitAuthoring] payload for the sheet's selected mode and category.
     * Template mode maps to the bundled default template for the category (DQC-3.6); Manual maps to
     * the authored checkpoints (auto-fill to 30 happens in the domain resolver, DQC-3.9). LLM plan
     * resolution is handled by the domain layer; when unconfigured the sheet never offers it.
     */
    fun resolveTemplateAuthoring(category: String?): HabitAuthoring {
        val resolvedCategory = category?.takeIf { it.isNotBlank() } ?: DEFAULT_TEMPLATE_CATEGORY
        val template = templates.defaultTemplate(resolvedCategory)
        return HabitAuthoring.Template(templateId = template.id)
    }

    private val _activeSheet = MutableStateFlow<ActiveSheet>(ActiveSheet.None)

    /** Which Creation_Sheet is currently presented (DQC-1.2, DQC-1.3). */
    val activeSheet: StateFlow<ActiveSheet> = _activeSheet.asStateFlow()

    /** Notifies the ViewModel the sheet's category changed so it can refresh suggested keywords. */
    fun onCategoryChanged(category: String) {
        _selectedCategory.value = category
    }

    // Buffered so a creation that completes before the UI subscribes still surfaces its Undo
    // snackbar exactly once; replay stays 0 so it is not re-shown on recomposition.
    private val _snackbar = MutableSharedFlow<UndoSnackbar>(
        replay = 0,
        extraBufferCapacity = 1
    )

    /** Transient post-creation confirmations carrying the sync-safe Undo action (DQC-1.6, DQC-1.7). */
    val snackbar: SharedFlow<UndoSnackbar> = _snackbar.asSharedFlow()

    // One-shot effects the composition consumes exactly once (e.g. the confirm-discard prompt).
    private val _events = Channel<QuickCreateEvent>(Channel.BUFFERED)

    /** One-shot quick-create effects (confirm-discard prompt, non-blocking failure notices). */
    val events: Flow<QuickCreateEvent> = _events.receiveAsFlow()

    /**
     * The Correlation_ID minted for the in-flight creation action, generated at origin in
     * [openQuickCreate] and reused by the subsequent save and its diagnostic events (DQC-6.3).
     */
    private var currentCorrelationId: CorrelationId? = null

    // ---- Menu / sheet lifecycle -----------------------------------------------------

    /**
     * Opens a Creation_Sheet from the Quick_Create_Menu. Mints the action's Correlation_ID at the
     * point of origin (DQC-6.3) and records [DiagnosticEventType.QUICK_CREATE_OPENED] (DQC-6.4).
     *
     * @param sheet the sheet to present (Goal, Habit, or Todo). [ActiveSheet.None] is a no-op.
     */
    fun openQuickCreate(sheet: ActiveSheet) {
        if (sheet is ActiveSheet.None) return
        val correlationId = correlationIds.newId()
        currentCorrelationId = correlationId
        _selectedCategory.value = ""
        _activeSheet.value = sheet
        viewModelScope.launch {
            diagnostics.record(DiagnosticEventType.QUICK_CREATE_OPENED, correlationId)
        }
    }

    /**
     * Dismisses the active sheet WITHOUT unsaved input (back gesture, scrim tap, FAB re-tap after
     * an empty sheet). Creates nothing (DQC-1.8) and clears the in-flight correlation.
     */
    fun dismissSheet() {
        _activeSheet.value = ActiveSheet.None
        currentCorrelationId = null
    }

    /**
     * Attempts to dismiss a sheet that contains unsaved user input. Rather than discarding
     * silently, this requests confirm-before-discard (DQC-1.9) by emitting
     * [QuickCreateEvent.ConfirmDiscard] and records [DiagnosticEventType.CREATION_ABANDONED]
     * against the action's origin Correlation_ID (DQC-6.4). The sheet stays open until the user
     * confirms via [confirmDiscard].
     */
    fun dismissSheetWithUnsavedInput() {
        val correlationId = currentCorrelationId
        viewModelScope.launch {
            if (correlationId != null) {
                diagnostics.record(DiagnosticEventType.CREATION_ABANDONED, correlationId)
            }
            _events.send(QuickCreateEvent.ConfirmDiscard)
        }
    }

    /** Confirms discarding unsaved input: closes the sheet and clears the correlation. */
    fun confirmDiscard() {
        _activeSheet.value = ActiveSheet.None
        currentCorrelationId = null
    }

    // ---- Saves (route to the SAME use cases the dedicated screens use — DQC-1.10) ----

    /**
     * Creates a goal via the shared [CreateGoalUseCase] (DQC-2). On success, closes the sheet and
     * emits the Undo snackbar; on validation/rejection/failure, keeps the sheet open and surfaces
     * the outcome non-blockingly so the user's input is never silently discarded (DQC-2.11).
     */
    fun saveGoal(
        name: String,
        category: String,
        keywords: List<String>,
        description: String? = null
    ) {
        val correlationId = ensureCorrelationId()
        viewModelScope.launch {
            val result = createGoal(
                CreateGoalCommand(
                    userId = userId,
                    name = name,
                    category = category,
                    keywords = keywords,
                    description = description,
                    correlationId = correlationId
                )
            )
            handleResult(result, correlationId) { goal: GoalProfile ->
                UndoSnackbar(message = "Created goal \"${goal.name}\"", undoToken = result.undoToken())
            }
        }
    }

    /**
     * Creates a standalone to-do via the shared [CreateDailyTodoUseCase] (DQC-4). `dayDate` is
     * today in the user's configured time zone (DQC-4.3), taken from [DomainClock].
     */
    fun saveTodo(
        text: String,
        dueDateTime: Instant? = null,
        reminder: ReminderDraft? = null
    ) {
        val correlationId = ensureCorrelationId()
        viewModelScope.launch {
            val result = createTodo(
                CreateTodoCommand(
                    userId = userId,
                    text = text,
                    dayDate = clock.today(),
                    dueDateTime = dueDateTime,
                    reminder = reminder,
                    correlationId = correlationId
                )
            )
            handleResult(result, correlationId) { _: DailyTodoItem ->
                UndoSnackbar(message = "Added to-do", undoToken = result.undoToken())
            }
        }
    }

    /**
     * Creates a habit (DQC-3). When [goalId] is non-null the habit attaches to that existing goal
     * via the shared [CreateHabitTrackUseCase]; when it is null the user had zero goals, so the
     * atomic Inline_Goal_Capture [CreateGoalWithHabitUseCase] creates the goal and habit as one
     * transaction from the inline goal fields — the client never persists a habit for a missing
     * goal (DQC-3.2, DQC-3.4).
     *
     * @param goalId the existing goal to attach to, or null to trigger Inline_Goal_Capture.
     * @param authoring how the 30 checkpoints are sourced (Template | Llm | Manual).
     * @param withReminder whether a daily checkpoint Reminder was requested (offered, not required).
     * @param inlineGoal the inline goal fields, required when [goalId] is null.
     */
    fun saveHabit(
        goalId: GoalId?,
        authoring: HabitAuthoring,
        withReminder: Boolean = false,
        inlineGoal: InlineGoalFields? = null
    ) {
        val correlationId = ensureCorrelationId()
        viewModelScope.launch {
            if (goalId != null) {
                val result = createHabit(
                    CreateHabitCommand(
                        userId = userId,
                        goalId = goalId,
                        authoring = authoring,
                        withReminder = withReminder,
                        correlationId = correlationId
                    )
                )
                handleResult(result, correlationId) { _: HabitTrack ->
                    UndoSnackbar(message = "Started habit", undoToken = result.undoToken())
                }
            } else {
                requireNotNull(inlineGoal) {
                    "inlineGoal is required when creating a habit with zero existing goals"
                }
                val result = createGoalWithHabit(
                    InlineGoalWithHabitCommand(
                        goal = CreateGoalCommand(
                            userId = userId,
                            name = inlineGoal.name,
                            category = inlineGoal.category,
                            keywords = inlineGoal.keywords,
                            description = inlineGoal.description,
                            correlationId = correlationId
                        ),
                        authoring = authoring,
                        withReminder = withReminder,
                        correlationId = correlationId
                    )
                )
                handleResult(result, correlationId) { value ->
                    UndoSnackbar(
                        message = "Created goal \"${value.goal.name}\" and started a habit",
                        undoToken = result.undoToken()
                    )
                }
            }
        }
    }

    // ---- Undo (sync-safe delete via the standard path — DQC-1.7) ---------------------

    /**
     * Activates Undo for a just-created entity. Delegates to [UndoCreationUseCase], which deletes
     * each referenced entity through the standard delete path so a Change_Log deletion event flows
     * through the Sync_Engine — never a local-only rollback (DQC-1.7). Records
     * [DiagnosticEventType.UNDO_INVOKED] against the token's Correlation_ID (DQC-6.4).
     */
    fun onUndo(token: UndoToken) {
        viewModelScope.launch {
            diagnostics.record(DiagnosticEventType.UNDO_INVOKED, token.correlationId)
            when (val result = undo(token)) {
                is CreationResult.Failure ->
                    _events.send(QuickCreateEvent.Notice("Couldn't undo — please try again"))
                is CreationResult.Rejected ->
                    _events.send(QuickCreateEvent.Notice(result.reason))
                is CreationResult.ValidationError, is CreationResult.Success -> Unit
            }
        }
    }

    // ---- Internal helpers ------------------------------------------------------------

    /**
     * Returns the in-flight Correlation_ID, minting a fresh one if the save was initiated without
     * an [openQuickCreate] (e.g. the Today's Focus inline quick-add, which has no sheet). Either
     * way the whole action shares one id (DQC-6.3).
     */
    private fun ensureCorrelationId(): CorrelationId =
        currentCorrelationId ?: correlationIds.newId().also { currentCorrelationId = it }

    /**
     * Central save-result handling shared by all three save paths: on [CreationResult.Success]
     * record ENTITY_CREATED, close the sheet, and emit the Undo snackbar; otherwise keep the sheet
     * open and surface the outcome non-blockingly, retaining the user's input (DQC-2.11).
     */
    private suspend fun <T> handleResult(
        result: CreationResult<T>,
        correlationId: CorrelationId,
        toSnackbar: (T) -> UndoSnackbar
    ) {
        when (result) {
            is CreationResult.Success -> {
                diagnostics.record(DiagnosticEventType.ENTITY_CREATED, correlationId)
                _activeSheet.value = ActiveSheet.None
                currentCorrelationId = null
                _snackbar.emit(toSnackbar(result.value))
            }

            is CreationResult.ValidationError ->
                _events.send(QuickCreateEvent.FieldErrors(result.fieldErrors))

            is CreationResult.Rejected ->
                _events.send(QuickCreateEvent.Notice(result.reason))

            is CreationResult.Failure ->
                _events.send(QuickCreateEvent.Notice("Couldn't save — please try again"))
        }
    }

    private companion object {
        // Fallback category used to resolve a bundled default template when the user has not chosen
        // a category yet; the asset provider always returns a template for it.
        const val DEFAULT_TEMPLATE_CATEGORY = "Learning"
    }
}

/**
 * The inline goal fields collected inside the habit sheet when the user has zero goals, so
 * Inline_Goal_Capture can create the goal atomically with the habit (DQC-3.2).
 *
 * @property name goal name (1-100 chars, unique per user, case-insensitive).
 * @property category goal category (1-50 chars).
 * @property keywords 1-20 keywords, each 1-50 chars.
 * @property description optional free-text description.
 */
data class InlineGoalFields(
    val name: String,
    val category: String,
    val keywords: List<String>,
    val description: String? = null
)

/**
 * One-shot effects surfaced by [QuickCreateViewModel], consumed exactly once by the composition
 * (never re-emitted on recomposition or configuration change).
 */
sealed interface QuickCreateEvent {

    /** Prompt the user to confirm discarding unsaved sheet input before it is dropped (DQC-1.9). */
    data object ConfirmDiscard : QuickCreateEvent

    /**
     * Client-side field validation failed; the sheet stays open with per-field inline errors and
     * the user's input retained.
     *
     * @property fieldErrors map of field name to human-readable error message.
     */
    data class FieldErrors(val fieldErrors: Map<String, String>) : QuickCreateEvent

    /**
     * A non-blocking notice (a back-end rejection reason, an undo failure, etc.) surfaced without
     * discarding the user's input (DQC-2.11).
     *
     * @property message the already-resolved, user-facing message.
     */
    data class Notice(val message: String) : QuickCreateEvent
}

/**
 * Extracts the [UndoToken] from a successful [CreationResult]. Only called from within the
 * [CreationResult.Success] branch, where the token is guaranteed present.
 */
private fun <T> CreationResult<T>.undoToken(): UndoToken =
    (this as CreationResult.Success).undo
