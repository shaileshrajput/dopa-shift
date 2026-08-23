package com.dopashift.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.repository.DailyTodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * UI state for the Daily Tasks screen.
 */
data class DailyTasksUiState(
    val todos: List<DailyTodoItem> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
    val editingItemId: UUID? = null,
    val errorMessage: String? = null
)

/**
 * ViewModel for the Daily Tasks screen.
 *
 * Provides reactive todo list via Room Flow, full CRUD operations,
 * inline editing support, and 500-char text validation.
 *
 * Requirements: 4.1, 4.2, 4.3
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DailyTasksViewModel @Inject constructor(
    private val dailyTodoRepository: DailyTodoRepository
) : ViewModel() {

    // TODO: Replace with actual user ID from auth/session layer
    private val userId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _editingItemId = MutableStateFlow<UUID?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    /**
     * Reactive list of todos for the selected date.
     * Room emits a new list whenever the underlying data changes.
     */
    private val todos: StateFlow<List<DailyTodoItem>> = _selectedDate
        .flatMapLatest { date ->
            dailyTodoRepository.observeByUserIdAndDate(userId, date)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList()
        )

    /**
     * Combined UI state from all reactive sources.
     */
    val uiState: StateFlow<DailyTasksUiState> = combine(
        todos,
        _selectedDate,
        _editingItemId,
        _errorMessage
    ) { todoList, date, editingId, error ->
        DailyTasksUiState(
            todos = todoList,
            selectedDate = date,
            isLoading = false,
            editingItemId = editingId,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = DailyTasksUiState()
    )

    // === Date Navigation ===

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    // === CRUD Operations ===

    /**
     * Create a new daily todo item.
     * Enforces 500-char text limit (Requirement 4.2, 4.12) and
     * 100-items-per-day limit (Requirement 4.2).
     */
    fun createTodo(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        if (trimmed.length > 500) {
            _errorMessage.value = "Task text must be at most 500 characters"
            return
        }

        viewModelScope.launch {
            val date = _selectedDate.value
            val count = dailyTodoRepository.countByUserIdAndDate(userId, date)
            if (count >= 100) {
                _errorMessage.value = "Maximum of 100 tasks per day reached"
                return@launch
            }

            val now = Instant.now()
            val item = DailyTodoItem(
                id = UUID.randomUUID(),
                userId = userId,
                text = trimmed,
                dueDateTime = null,
                isCompleted = false,
                createdAt = now,
                updatedAt = now,
                dayDate = date
            )
            dailyTodoRepository.save(item)
        }
    }

    /**
     * Update an existing todo's text via inline editing.
     * Enforces 500-char text limit (Requirement 4.12).
     */
    fun updateTodoText(itemId: UUID, newText: String) {
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) {
            _errorMessage.value = "Task text cannot be empty"
            return
        }

        if (trimmed.length > 500) {
            _errorMessage.value = "Task text must be at most 500 characters"
            return
        }

        viewModelScope.launch {
            val existing = dailyTodoRepository.findById(itemId) ?: return@launch
            val updated = existing.copy(
                text = trimmed,
                updatedAt = Instant.now()
            )
            dailyTodoRepository.save(updated)
            _editingItemId.value = null
        }
    }

    /**
     * Delete a todo item (Requirement 4.2).
     */
    fun deleteTodo(itemId: UUID) {
        viewModelScope.launch {
            dailyTodoRepository.delete(itemId)
        }
    }

    /**
     * Toggle completion status — checked shows strikethrough,
     * un-check reverts (Requirement 4.3).
     */
    fun toggleComplete(item: DailyTodoItem) {
        viewModelScope.launch {
            val updated = item.copy(
                isCompleted = !item.isCompleted,
                updatedAt = Instant.now()
            )
            dailyTodoRepository.save(updated)
        }
    }

    // === Inline Editing ===

    fun startEditing(itemId: UUID) {
        _editingItemId.value = itemId
    }

    fun cancelEditing() {
        _editingItemId.value = null
    }

    // === Error Handling ===

    fun clearError() {
        _errorMessage.value = null
    }
}
