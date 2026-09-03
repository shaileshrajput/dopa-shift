package com.dopashift.interception.overlay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dopashift.domain.creation.CreationResult
import com.dopashift.domain.creation.GoalWithHabit
import com.dopashift.domain.creation.HabitAuthoring
import com.dopashift.domain.entity.CheckpointStatus
import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.entity.HabitCheckpoint
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.repository.DailyTodoRepository
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import com.dopashift.ui.quickcreate.InlineGoalFields
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the Intercept Overlay screen.
 *
 * Manages overlay state including pending todos, current habit track,
 * video suggestion, and the engagement timer (30s minimum before dismissal).
 *
 * Requirements: 2.4, 2.5, 2.9, 3.1, 3.6, 6.2, 17.7
 */
@HiltViewModel
class OverlayViewModel @Inject constructor(
    private val dailyTodoRepository: DailyTodoRepository,
    private val habitTrackRepository: HabitTrackRepository,
    private val goalRepository: GoalRepository,
    private val todoCompletionHandler: OverlayTodoCompletionHandler,
    private val inlineGoalCaptureHandler: OverlayInlineGoalCaptureHandler
) : ViewModel() {

    // TODO: Replace with actual user ID from auth/session layer
    private val userId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

    private val _pendingTodos = MutableStateFlow<List<DailyTodoItem>>(emptyList())
    private val _currentHabitTrack = MutableStateFlow<HabitTrack?>(null)
    private val _currentCheckpoint = MutableStateFlow<HabitCheckpoint?>(null)
    private val _videoUrl = MutableStateFlow<String?>(null)
    private val _videoTitle = MutableStateFlow<String?>(null)
    private val _elapsedSeconds = MutableStateFlow(0)
    private val _canDismiss = MutableStateFlow(false)
    private val _isLoading = MutableStateFlow(true)
    private val _triggeringPackage = MutableStateFlow("")
    private val _hasZeroGoals = MutableStateFlow(false)

    /**
     * Whether the user has recorded at least one engagement action on the Intercept_Screen —
     * specifically checking a habit checkpoint or completing a to-do (Requirement 4.9). Set once
     * and never cleared for the lifetime of the overlay. The hosting [OverlayService] reads this
     * on dismissal and reports it back to the Engine so [FocusExtensionManager.grantIfEngaged] can
     * decide whether to grant a Focus_Extension.
     */
    private val _engagementRecorded = MutableStateFlow(false)
    val engagementRecorded: StateFlow<Boolean> = _engagementRecorded.asStateFlow()

    /**
     * The primary Intercept_Screen action the user selected — "Continue to app" or "Switch to
     * DopaShift" (Requirement 3.1). Emitted exactly once when the user taps a primary control;
     * the hosting [OverlayService] collects this result and forwards it to the Engine to route the
     * correct behavior and append a local-only audit entry (Requirements 3.2, 3.3, 3.4). The
     * routing/audit wiring itself lives in the service and is intentionally not performed here.
     *
     * Modeled as a replay-1 [SharedFlow] (a one-shot event, not retained UI state) so a late
     * collector on the Service still observes the tapped action before teardown. The last emitted
     * value is also exposed synchronously via [selectedAction] for the service's dismissal path.
     */
    private val _action = MutableSharedFlow<InterceptAction>(replay = 1, extraBufferCapacity = 1)
    val action: SharedFlow<InterceptAction> = _action.asSharedFlow()

    /**
     * The last primary action the user selected, or null if none has been tapped yet. Read by
     * [OverlayService] on dismissal to decide routing and audit (Requirements 3.2, 3.3, 3.4).
     */
    private var _selectedAction: InterceptAction? = null

    val uiState: StateFlow<OverlayUiState> = combine(
        _pendingTodos,
        _currentHabitTrack,
        _currentCheckpoint,
        _elapsedSeconds,
        _canDismiss
    ) { todos, habitTrack, checkpoint, elapsed, canDismiss ->
        OverlayUiState(
            pendingTodos = todos,
            currentHabitTrack = habitTrack,
            currentCheckpoint = checkpoint,
            videoSuggestionUrl = _videoUrl.value,
            videoTitle = _videoTitle.value,
            elapsedSeconds = elapsed,
            canDismiss = canDismiss,
            isLoading = _isLoading.value,
            triggeringPackageName = _triggeringPackage.value,
            hasZeroGoals = _hasZeroGoals.value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OverlayUiState()
    )

    /**
     * Initializes the overlay with data loading and starts the engagement timer.
     *
     * @param triggeringPackageName The app package that triggered overlay display.
     */
    fun initialize(triggeringPackageName: String) {
        _triggeringPackage.value = triggeringPackageName
        loadData()
        startEngagementTimer()
    }

    private fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true

            // Load pending (incomplete) todos for today
            val today = LocalDate.now()
            val todosForToday = dailyTodoRepository.findByUserIdAndDate(userId, today)
            _pendingTodos.value = todosForToday.filter { !it.isCompleted }

            // Load earliest active habit track for overlay
            val activeTracks = habitTrackRepository.findActiveByUserId(userId)
            val earliestTrack = activeTracks
                .filter { !it.isFinished }
                .minByOrNull { it.startDate }

            _currentHabitTrack.value = earliestTrack
            _currentCheckpoint.value = earliestTrack?.checkpoints
                ?.firstOrNull { it.dayNumber == earliestTrack.currentDay && it.status == CheckpointStatus.PENDING }

            // Detect the zero-goal case so the overlay can offer Inline_Goal_Capture instead of
            // dead-ending a user who has no goal to attach a habit to (Requirement 5.10).
            _hasZeroGoals.value = goalRepository.findByUserId(userId).isEmpty()

            // TODO (Task 7.10 integration): Load video suggestion from cache or API
            // For now, leave as null — the overlay handles the empty state gracefully.

            _isLoading.value = false
        }
    }

    /**
     * Starts a 1-second interval timer for engagement tracking.
     * After [OverlayUiState.MIN_ENGAGEMENT_SECONDS], the dismiss button becomes enabled.
     *
     * Requirement 2.5: 30-second minimum cooldown + alternative action before dismissal.
     */
    private fun startEngagementTimer() {
        viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                val newElapsed = _elapsedSeconds.value + 1
                _elapsedSeconds.value = newElapsed

                if (newElapsed >= OverlayUiState.MIN_ENGAGEMENT_SECONDS) {
                    _canDismiss.value = true
                }
            }
        }
    }

    /**
     * Marks a todo item as completed from the overlay.
     *
     * Routes through [OverlayTodoCompletionHandler] so completion goes through the **same
     * Change_Log pipeline** as a normal completion elsewhere in the app: the todo is persisted
     * and a `ChangeLogEntry` is appended for sync (Requirements 4.8, 4.7). Recording this action
     * flips [engagementRecorded] so the Engine can grant a Focus_Extension (Requirement 4.9).
     */
    fun completeTodo(todoId: UUID) {
        viewModelScope.launch {
            val updated = todoCompletionHandler.completeTodo(todoId) ?: return@launch

            // Update local state to reflect completion
            _pendingTodos.value = _pendingTodos.value.map {
                if (it.id == todoId) updated else it
            }

            // An action has been recorded — enable dismissal and mark engagement (Req 4.9).
            _canDismiss.value = true
            _engagementRecorded.value = true
        }
    }

    /**
     * Marks the current day's habit checkpoint as completed.
     * Same Change_Log pipeline as normal checkpoint updates (Requirement 2.11).
     */
    fun completeHabitCheckpoint() {
        viewModelScope.launch {
            val track = _currentHabitTrack.value ?: return@launch
            val checkpoint = _currentCheckpoint.value ?: return@launch

            val updatedCheckpoint = checkpoint.copy(
                status = CheckpointStatus.COMPLETED,
                completedAt = Instant.now()
            )

            // Update the track's checkpoint list
            val updatedCheckpoints = track.checkpoints.map {
                if (it.id == checkpoint.id) updatedCheckpoint else it
            }
            val updatedTrack = track.copy(checkpoints = updatedCheckpoints)
            habitTrackRepository.save(updatedTrack)

            _currentCheckpoint.value = updatedCheckpoint
            _currentHabitTrack.value = updatedTrack

            // An action has been recorded — enable dismissal and mark engagement (Req 4.9).
            // Habit completion uses HabitTrackRepository.save, which is the same pipeline normal
            // habit completion uses (it does not append a separate ChangeLogEntry), so no change
            // is needed to preserve pipeline parity here.
            _canDismiss.value = true
            _engagementRecorded.value = true
        }
    }

    /**
     * Reaches Inline_Goal_Capture from within the overlay for a zero-goal user (Requirement 5.10).
     *
     * Delegates to [OverlayInlineGoalCaptureHandler], which drives the SAME
     * [com.dopashift.domain.creation.usecase.CreateGoalWithHabitUseCase] the Dashboard uses (origin
     * INTERCEPT_OVERLAY, no parallel data path). On success the intercepted user now has a goal and
     * a habit, so the overlay's zero-goal state clears and the freshly created checkpoint is
     * surfaced; creating a goal + habit also counts as a recorded engagement action (Req 4.9).
     *
     * @param inlineGoal the minimum valid goal fields collected inline (name, category, >=1 keyword).
     * @param authoring how the habit's 30 checkpoints are sourced (Template | Llm | Manual).
     * @param withReminder whether a daily checkpoint Reminder was requested (offered, not required).
     */
    fun captureInlineGoal(
        inlineGoal: InlineGoalFields,
        authoring: HabitAuthoring,
        withReminder: Boolean = false
    ) {
        viewModelScope.launch {
            val result = inlineGoalCaptureHandler.captureGoalWithHabit(
                userId = userId,
                inlineGoal = inlineGoal,
                authoring = authoring,
                withReminder = withReminder
            )
            if (result is CreationResult.Success) {
                val created: GoalWithHabit = result.value
                _hasZeroGoals.value = false

                // Surface the just-created habit's day-1 checkpoint in the overlay.
                _currentHabitTrack.value = created.habit
                _currentCheckpoint.value = created.habit.checkpoints
                    .firstOrNull { it.dayNumber == created.habit.currentDay && it.status == CheckpointStatus.PENDING }

                // Creating a goal + habit is a recorded engagement action (Req 4.9).
                _canDismiss.value = true
                _engagementRecorded.value = true
            }
        }
    }

    /**
     * Handles a tap on the "Continue to app" primary control (Requirement 3.3).
     *
     * Emits an [InterceptAction.CONTINUE_TO_APP] result for the hosting service to consume; the
     * service performs the actual overlay removal, focus return, Session_Usage reset, and audit
     * append (Requirements 3.3, 3.4) — this ViewModel only signals the choice.
     */
    fun onContinueToApp() {
        emitAction(InterceptAction.CONTINUE_TO_APP)
    }

    /**
     * Handles a tap on the "Switch to DopaShift" primary control (Requirement 3.2).
     *
     * Emits an [InterceptAction.SWITCH_TO_DOPASHIFT] result for the hosting service to consume; the
     * service launches the DopaShift main activity, resets Session_Usage, and appends the audit
     * entry (Requirements 3.2, 3.4) — this ViewModel only signals the choice.
     */
    fun onSwitchToDopaShift() {
        emitAction(InterceptAction.SWITCH_TO_DOPASHIFT)
    }

    private fun emitAction(action: InterceptAction) {
        _selectedAction = action
        _action.tryEmit(action)
    }

    /**
     * The last primary action the user selected on the Intercept_Screen, or null if none was
     * tapped. Read by [OverlayService] on dismissal to route behavior and append the audit entry
     * (Requirements 3.2, 3.3, 3.4).
     */
    fun selectedAction(): InterceptAction? {
        return _selectedAction
    }

    /**
     * Whether the overlay can be dismissed.
     * True if either:
     * - 30 seconds have elapsed, OR
     * - The user performed an alternative action (completed a todo or habit checkpoint).
     */
    fun canDismissOverlay(): Boolean {
        return _canDismiss.value
    }

    /**
     * Whether the user recorded at least one engagement action (checked a habit or completed a
     * to-do) during this overlay session (Requirement 4.9). Read by [OverlayService] on dismissal.
     */
    fun hasRecordedEngagement(): Boolean {
        return _engagementRecorded.value
    }

    /** The package that triggered this overlay; used to attribute the engagement result. */
    fun triggeringPackageName(): String {
        return _triggeringPackage.value
    }
}
