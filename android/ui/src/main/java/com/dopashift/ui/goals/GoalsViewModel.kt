package com.dopashift.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dopashift.domain.entity.GoalChecklistItem
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.repository.GoalChecklistItemRepository
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Progress data for a single goal, used for the progress ring on goal cards.
 */
data class GoalProgress(
    val goalId: UUID,
    val completedItems: Int,
    val totalItems: Int,
    val activeHabitTracks: Int
) {
    /** Progress fraction 0.0–1.0 for the circular progress ring. */
    val fraction: Float
        get() = if (totalItems == 0) 0f else completedItems.toFloat() / totalItems
}

/**
 * UI state for the Goals & Habits screen.
 */
data class GoalsUiState(
    val goals: List<GoalProfile> = emptyList(),
    val goalProgressMap: Map<UUID, GoalProgress> = emptyMap(),
    val selectedGoal: GoalProfile? = null,
    val selectedGoalChecklist: List<GoalChecklistItem> = emptyList(),
    val selectedGoalHabitTracks: List<HabitTrack> = emptyList(),
    val isLoading: Boolean = true,
    val showEditDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val deleteDialogDependents: DeleteDependents? = null,
    val errorMessage: String? = null
)

/**
 * Dependent items that will be affected by goal deletion.
 */
data class DeleteDependents(
    val checklistItems: List<GoalChecklistItem>,
    val habitTracks: List<HabitTrack>,
    val availableGoals: List<GoalProfile>
)

/**
 * Action chosen by the user during goal deletion.
 */
sealed class DeleteGoalAction {
    data object DeleteAll : DeleteGoalAction()
    data class Reassign(val targetGoalId: UUID) : DeleteGoalAction()
}

/**
 * ViewModel for the Goals & Habits screen.
 *
 * Provides reactive goal list, detail view with checklist items and habit tracks,
 * and CRUD operations with dependency confirmation for deletion.
 *
 * Room's invalidation tracker fires within milliseconds of a write,
 * providing sub-second propagation per Requirement 14.4.
 */
@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goalRepository: GoalRepository,
    private val checklistRepository: GoalChecklistItemRepository,
    private val habitTrackRepository: HabitTrackRepository
) : ViewModel() {

    // TODO: Replace with actual user ID from auth/session layer
    private val userId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

    private val _selectedGoalId = MutableStateFlow<UUID?>(null)

    private val _dialogState = MutableStateFlow(DialogState())

    private val _errorMessage = MutableStateFlow<String?>(null)

    /**
     * All goals for the current user, emitted reactively by Room.
     */
    private val goals: StateFlow<List<GoalProfile>> = goalRepository
        .observeByUserId(userId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList()
        )

    /**
     * All checklist items for the user, used to compute per-goal progress.
     */
    private val allChecklistItems: StateFlow<List<GoalChecklistItem>> = checklistRepository
        .observeByUserId(userId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList()
        )

    /**
     * All active habit tracks for the user, used for per-goal progress and detail view.
     */
    private val allHabitTracks: StateFlow<List<HabitTrack>> = habitTrackRepository
        .observeActiveByUserId(userId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList()
        )

    /**
     * Checklist items for the selected goal, switching reactively when selection changes.
     */
    private val selectedGoalChecklist: StateFlow<List<GoalChecklistItem>> = _selectedGoalId
        .flatMapLatest { goalId ->
            if (goalId != null) checklistRepository.observeByGoalId(goalId)
            else flowOf(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList()
        )

    /**
     * Habit tracks for the selected goal, switching reactively when selection changes.
     */
    private val selectedGoalHabitTracks: StateFlow<List<HabitTrack>> = _selectedGoalId
        .flatMapLatest { goalId ->
            if (goalId != null) {
                // HabitTrackRepository doesn't have observeByGoalId, so we observe all
                // and filter. Could be optimized with a dedicated Flow method.
                habitTrackRepository.observeActiveByUserId(userId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList()
        )

    /**
     * Combined UI state from all reactive sources.
     */
    val uiState: StateFlow<GoalsUiState> = combine(
        goals,
        _selectedGoalId,
        selectedGoalChecklist,
        selectedGoalHabitTracks,
        _dialogState,
        _errorMessage,
        allChecklistItems,
        allHabitTracks
    ) { values ->
        val goalsList = values[0] as List<GoalProfile>
        val selectedId = values[1] as UUID?
        val checklist = values[2] as List<GoalChecklistItem>
        val habitTracks = values[3] as List<HabitTrack>
        val dialogs = values[4] as DialogState
        val error = values[5] as String?
        val allChecklist = values[6] as List<GoalChecklistItem>
        val allTracks = values[7] as List<HabitTrack>

        val selectedGoal = selectedId?.let { id -> goalsList.find { it.id == id } }
        // Filter habit tracks to only those belonging to the selected goal
        val filteredHabitTracks = if (selectedId != null) {
            habitTracks.filter { it.goalId == selectedId }
        } else {
            emptyList()
        }

        // Compute per-goal progress for the progress ring
        val goalProgressMap = goalsList.associate { goal ->
            val goalChecklist = allChecklist.filter { it.goalId == goal.id }
            val completedCount = goalChecklist.count { it.isCompleted }
            val habitTrackCount = allTracks.count { it.goalId == goal.id }
            goal.id to GoalProgress(
                goalId = goal.id,
                completedItems = completedCount,
                totalItems = goalChecklist.size,
                activeHabitTracks = habitTrackCount
            )
        }

        GoalsUiState(
            goals = goalsList,
            goalProgressMap = goalProgressMap,
            selectedGoal = selectedGoal,
            selectedGoalChecklist = checklist,
            selectedGoalHabitTracks = filteredHabitTracks,
            isLoading = false,
            showEditDialog = dialogs.showEdit,
            showDeleteDialog = dialogs.showDelete,
            deleteDialogDependents = dialogs.deleteDependents,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = GoalsUiState()
    )

    // === Navigation ===

    fun selectGoal(goalId: UUID) {
        _selectedGoalId.value = goalId
    }

    fun clearSelection() {
        _selectedGoalId.value = null
    }

    // === Dialog Management ===

    fun showEditDialog() {
        _dialogState.update { it.copy(showEdit = true) }
    }

    fun dismissEditDialog() {
        _dialogState.update { it.copy(showEdit = false) }
    }

    /**
     * Shows delete confirmation dialog with dependent items listed.
     * Per Requirement 1.5: display all dependent items and require user choice.
     */
    fun showDeleteDialog() {
        val goal = resolveSelectedGoal() ?: return
        viewModelScope.launch {
            val checklistItems = checklistRepository.findByGoalId(goal.id)
            val habitTracks = habitTrackRepository.findActiveByGoalId(goal.id)
            val otherGoals = goals.value.filter { it.id != goal.id }

            _dialogState.update {
                it.copy(
                    showDelete = true,
                    deleteDependents = DeleteDependents(
                        checklistItems = checklistItems,
                        habitTracks = habitTracks,
                        availableGoals = otherGoals
                    )
                )
            }
        }
    }

    fun dismissDeleteDialog() {
        _dialogState.update { it.copy(showDelete = false, deleteDependents = null) }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // === CRUD Operations ===

    /**
     * Update the selected goal.
     */
    fun updateGoal(name: String, category: String, keywords: List<String>) {
        val goal = resolveSelectedGoal()
        if (goal == null) {
            _errorMessage.value = "No goal selected — please select a goal first"
            return
        }
        viewModelScope.launch {
            // Check name uniqueness if name changed (Requirement 1.7)
            if (name != goal.name) {
                val existing = goalRepository.findByUserIdAndName(userId, name)
                if (existing != null) {
                    _errorMessage.value = "A goal with this name already exists"
                    return@launch
                }
            }

            val updated = goal.copy(
                name = name,
                category = category,
                keywords = keywords,
                updatedAt = Instant.now()
            )
            goalRepository.save(updated)
            _dialogState.update { it.copy(showEdit = false) }
        }
    }

    /**
     * Delete the selected goal with dependency handling (Requirement 1.5, 1.6).
     */
    fun deleteGoal(action: DeleteGoalAction) {
        val goal = resolveSelectedGoal()
        if (goal == null) {
            _errorMessage.value = "No goal selected — please select a goal first"
            return
        }
        viewModelScope.launch {
            when (action) {
                is DeleteGoalAction.DeleteAll -> {
                    checklistRepository.deleteByGoalId(goal.id)
                    // Habit track deletion not directly supported — deactivation handled by backend sync
                }
                is DeleteGoalAction.Reassign -> {
                    checklistRepository.reassignToGoal(goal.id, action.targetGoalId)
                }
            }
            goalRepository.delete(goal.id)
            _selectedGoalId.value = null
            _dialogState.update { it.copy(showDelete = false, deleteDependents = null) }
        }
    }

    // === Checklist Operations ===

    /**
     * Resolves the currently selected goal directly from the authoritative state flows,
     * bypassing the combine-based uiState to avoid race conditions where uiState.value
     * hasn't yet re-emitted after a selection change.
     */
    private fun resolveSelectedGoal(): GoalProfile? {
        val selectedId = _selectedGoalId.value ?: return null
        return goals.value.find { it.id == selectedId }
    }

    /**
     * Add a checklist item to the selected goal (Requirement 1.3).
     */
    fun addChecklistItem(text: String) {
        val goal = resolveSelectedGoal()
        if (goal == null) {
            _errorMessage.value = "No goal selected — please select a goal first"
            return
        }
        viewModelScope.launch {
            val now = Instant.now()
            val item = GoalChecklistItem(
                id = UUID.randomUUID(),
                goalId = goal.id,
                userId = userId,
                text = text,
                isCompleted = false,
                createdAt = now,
                updatedAt = now
            )
            checklistRepository.save(item)
        }
    }

    /**
     * Toggle a checklist item's completion status.
     */
    fun toggleChecklistItem(item: GoalChecklistItem) {
        viewModelScope.launch {
            val updated = item.copy(
                isCompleted = !item.isCompleted,
                updatedAt = Instant.now()
            )
            checklistRepository.save(updated)
        }
    }

    /**
     * Delete a checklist item.
     */
    fun deleteChecklistItem(itemId: UUID) {
        viewModelScope.launch {
            checklistRepository.delete(itemId)
        }
    }
}

/**
 * Internal state for dialog visibility.
 */
private data class DialogState(
    val showEdit: Boolean = false,
    val showDelete: Boolean = false,
    val deleteDependents: DeleteDependents? = null
)
