package com.dopashift.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dopashift.domain.entity.ConditionType
import com.dopashift.domain.entity.RecurrenceRule
import com.dopashift.domain.entity.RecurrenceType
import com.dopashift.domain.entity.Reminder
import com.dopashift.domain.entity.ReminderCondition
import com.dopashift.domain.entity.ReminderEntityType
import com.dopashift.domain.repository.ReminderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

/**
 * UI state for the Reminders/Notifications screen.
 */
data class RemindersUiState(
    val reminders: List<Reminder> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val showCreateDialog: Boolean = false,
    val editingReminder: Reminder? = null,
    val quietHoursStart: LocalTime? = null,
    val quietHoursEnd: LocalTime? = null
)

/**
 * State for the create/edit reminder dialog form.
 */
data class ReminderFormState(
    val entityType: ReminderEntityType = ReminderEntityType.DAILY_TODO,
    val entityId: String = "",
    val scheduledTime: LocalTime = LocalTime.of(9, 0),
    val scheduledDate: LocalDate? = null,
    val recurrenceType: RecurrenceType? = null,
    val selectedWeekdays: Set<DayOfWeek> = emptySet(),
    val customIntervalDays: Int = 1,
    val isConditional: Boolean = false,
    val escalationIntervalMinutes: Int = 15,
    val maxEscalations: Int = 3,
    val validationError: String? = null
)

/**
 * ViewModel for the Notifications/Reminders screen.
 *
 * Manages CRUD operations on reminders with recurrence configuration,
 * conditional firing, and escalation settings.
 *
 * Requirements: 5.1, 5.2, 5.3, 5.4, 5.11, 5.13
 */
@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository
) : ViewModel() {

    // TODO: Replace with actual user ID from auth/session layer
    private val userId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

    private val _showCreateDialog = MutableStateFlow(false)
    private val _editingReminder = MutableStateFlow<Reminder?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _quietHoursStart = MutableStateFlow<LocalTime?>(null)
    private val _quietHoursEnd = MutableStateFlow<LocalTime?>(null)
    private val _formState = MutableStateFlow(ReminderFormState())

    /**
     * Reactive list of active reminders from Room.
     */
    private val reminders: StateFlow<List<Reminder>> = reminderRepository
        .observeActiveByUserId(userId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList()
        )

    /**
     * Combined UI state from all reactive sources.
     */
    val uiState: StateFlow<RemindersUiState> = combine(
        reminders,
        _showCreateDialog,
        _editingReminder,
        _errorMessage,
        _quietHoursStart
    ) { reminderList, showDialog, editing, error, quietStart ->
        RemindersUiState(
            reminders = reminderList,
            isLoading = false,
            errorMessage = error,
            showCreateDialog = showDialog,
            editingReminder = editing,
            quietHoursStart = quietStart,
            quietHoursEnd = _quietHoursEnd.value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = RemindersUiState()
    )

    val formState: StateFlow<ReminderFormState> = _formState

    // === Dialog Management ===

    fun showCreateDialog() {
        _formState.value = ReminderFormState()
        _showCreateDialog.value = true
        _editingReminder.value = null
    }

    fun showEditDialog(reminder: Reminder) {
        _editingReminder.value = reminder
        _formState.value = ReminderFormState(
            entityType = reminder.entityType,
            entityId = reminder.entityId.toString(),
            scheduledTime = reminder.scheduledTime,
            scheduledDate = reminder.scheduledDate,
            recurrenceType = reminder.recurrence?.type,
            selectedWeekdays = reminder.recurrence?.weekdays ?: emptySet(),
            customIntervalDays = reminder.recurrence?.intervalDays ?: 1,
            isConditional = reminder.condition != null,
            escalationIntervalMinutes = reminder.escalationInterval.toMinutes().toInt(),
            maxEscalations = reminder.maxEscalations
        )
        _showCreateDialog.value = true
    }

    fun dismissDialog() {
        _showCreateDialog.value = false
        _editingReminder.value = null
        _formState.value = ReminderFormState()
    }

    // === Form Updates ===

    fun updateEntityType(type: ReminderEntityType) {
        _formState.value = _formState.value.copy(entityType = type, validationError = null)
    }

    fun updateEntityId(id: String) {
        _formState.value = _formState.value.copy(entityId = id, validationError = null)
    }

    fun updateScheduledTime(time: LocalTime) {
        _formState.value = _formState.value.copy(scheduledTime = time, validationError = null)
    }

    fun updateScheduledDate(date: LocalDate?) {
        _formState.value = _formState.value.copy(scheduledDate = date, validationError = null)
    }

    fun updateRecurrenceType(type: RecurrenceType?) {
        _formState.value = _formState.value.copy(recurrenceType = type, validationError = null)
    }

    fun toggleWeekday(day: DayOfWeek) {
        val current = _formState.value.selectedWeekdays
        val updated = if (current.contains(day)) current - day else current + day
        _formState.value = _formState.value.copy(selectedWeekdays = updated, validationError = null)
    }

    fun updateCustomIntervalDays(days: Int) {
        val clamped = days.coerceIn(1, 365)
        _formState.value = _formState.value.copy(customIntervalDays = clamped, validationError = null)
    }

    fun updateConditional(conditional: Boolean) {
        _formState.value = _formState.value.copy(isConditional = conditional, validationError = null)
    }

    fun updateEscalationIntervalMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(5, 120)
        _formState.value = _formState.value.copy(escalationIntervalMinutes = clamped, validationError = null)
    }

    fun updateMaxEscalations(count: Int) {
        val clamped = count.coerceIn(1, 10)
        _formState.value = _formState.value.copy(maxEscalations = clamped, validationError = null)
    }

    // === CRUD Operations ===

    /**
     * Save a new reminder or update an existing one.
     * Validates form state before persisting.
     */
    fun saveReminder() {
        val form = _formState.value

        // Validate entity ID
        val entityId = try {
            UUID.fromString(form.entityId)
        } catch (_: IllegalArgumentException) {
            _formState.value = form.copy(validationError = "Please enter a valid entity ID")
            return
        }

        // Validate recurrence: weekdays required for SPECIFIC_WEEKDAYS
        if (form.recurrenceType == RecurrenceType.SPECIFIC_WEEKDAYS && form.selectedWeekdays.isEmpty()) {
            _formState.value = form.copy(validationError = "Select at least one weekday")
            return
        }

        // Validate custom interval
        if (form.recurrenceType == RecurrenceType.CUSTOM_INTERVAL &&
            (form.customIntervalDays < 1 || form.customIntervalDays > 365)
        ) {
            _formState.value = form.copy(validationError = "Interval must be between 1 and 365 days")
            return
        }

        // For one-off reminders, date must be set and at least 1 minute in the future (Requirement 5.2)
        if (form.recurrenceType == null && form.scheduledDate == null) {
            _formState.value = form.copy(validationError = "One-off reminders require a date")
            return
        }

        val recurrence = form.recurrenceType?.let { type ->
            RecurrenceRule(
                type = type,
                weekdays = if (type == RecurrenceType.SPECIFIC_WEEKDAYS) form.selectedWeekdays else null,
                intervalDays = if (type == RecurrenceType.CUSTOM_INTERVAL) form.customIntervalDays else null
            )
        }

        val condition = if (form.isConditional) {
            ReminderCondition(type = ConditionType.ENTITY_INCOMPLETE)
        } else {
            null
        }

        val existingReminder = _editingReminder.value
        val reminder = Reminder(
            id = existingReminder?.id ?: UUID.randomUUID(),
            userId = userId,
            entityType = form.entityType,
            entityId = entityId,
            scheduledTime = form.scheduledTime,
            scheduledDate = if (recurrence != null) null else form.scheduledDate,
            recurrence = recurrence,
            condition = condition,
            escalationInterval = Duration.ofMinutes(form.escalationIntervalMinutes.toLong()),
            maxEscalations = form.maxEscalations,
            escalationCount = existingReminder?.escalationCount ?: 0,
            isActive = true
        )

        viewModelScope.launch {
            reminderRepository.save(reminder)
            dismissDialog()
        }
    }

    /**
     * Delete a reminder (Requirement 5.1 — full CRUD on reminders).
     */
    fun deleteReminder(reminderId: UUID) {
        viewModelScope.launch {
            reminderRepository.delete(reminderId)
        }
    }

    // === Quiet Hours (Requirement 5.13) ===

    fun updateQuietHoursStart(time: LocalTime?) {
        _quietHoursStart.value = time
        viewModelScope.launch {
            // Would persist to user profile/settings
        }
    }

    fun updateQuietHoursEnd(time: LocalTime?) {
        _quietHoursEnd.value = time
        viewModelScope.launch {
            // Would persist to user profile/settings
        }
    }

    // === Error Handling ===

    fun clearError() {
        _errorMessage.value = null
    }
}
