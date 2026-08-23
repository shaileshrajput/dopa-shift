package com.dopashift.interception.overlay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dopashift.domain.entity.CheckpointStatus
import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.entity.HabitCheckpoint
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.repository.DailyTodoRepository
import com.dopashift.domain.repository.HabitTrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    private val habitTrackRepository: HabitTrackRepository
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
            triggeringPackageName = _triggeringPackage.value
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
     * Uses the same Change_Log pipeline as normal todo updates (Requirement 2.10).
     */
    fun completeTodo(todoId: UUID) {
        viewModelScope.launch {
            val todo = dailyTodoRepository.findById(todoId) ?: return@launch
            val updated = todo.copy(
                isCompleted = true,
                updatedAt = Instant.now()
            )
            dailyTodoRepository.save(updated)

            // Update local state to reflect completion
            _pendingTodos.value = _pendingTodos.value.map {
                if (it.id == todoId) updated else it
            }

            // An action has been performed — enable dismissal
            _canDismiss.value = true
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

            // An action has been performed — enable dismissal
            _canDismiss.value = true
        }
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
}
