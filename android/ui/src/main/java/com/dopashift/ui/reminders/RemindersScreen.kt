package com.dopashift.ui.reminders

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dopashift.domain.entity.RecurrenceType
import com.dopashift.domain.entity.Reminder
import com.dopashift.domain.entity.ReminderEntityType
import com.dopashift.ui.theme.DopaShiftTheme
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Notifications/Reminders screen implementing Requirements:
 * - 5.1: Set reminders on any entity type (shared reminder model)
 * - 5.2: One-off reminders scheduled at any future date/time
 * - 5.3: Repeating reminders (daily, weekdays, weekly, monthly, custom interval)
 * - 5.4: Conditional reminders (fire only if entity incomplete)
 * - 5.11: Escalation configuration (interval, max count)
 * - 5.13: Quiet hours display and configuration
 *
 * Uses Material3 components and DopaShiftTheme.
 */
@Composable
fun RemindersScreen(
    viewModel: RemindersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()

    RemindersContent(
        uiState = uiState,
        formState = formState,
        onCreateClick = viewModel::showCreateDialog,
        onEditClick = viewModel::showEditDialog,
        onDeleteClick = viewModel::deleteReminder,
        onDismissDialog = viewModel::dismissDialog,
        onSaveReminder = viewModel::saveReminder,
        onEntityTypeChange = viewModel::updateEntityType,
        onEntityIdChange = viewModel::updateEntityId,
        onScheduledTimeChange = viewModel::updateScheduledTime,
        onScheduledDateChange = viewModel::updateScheduledDate,
        onRecurrenceTypeChange = viewModel::updateRecurrenceType,
        onToggleWeekday = viewModel::toggleWeekday,
        onCustomIntervalChange = viewModel::updateCustomIntervalDays,
        onConditionalChange = viewModel::updateConditional,
        onEscalationIntervalChange = viewModel::updateEscalationIntervalMinutes,
        onMaxEscalationsChange = viewModel::updateMaxEscalations,
        onClearError = viewModel::clearError
    )
}

@Composable
private fun RemindersContent(
    uiState: RemindersUiState,
    formState: ReminderFormState,
    onCreateClick: () -> Unit,
    onEditClick: (Reminder) -> Unit,
    onDeleteClick: (java.util.UUID) -> Unit,
    onDismissDialog: () -> Unit,
    onSaveReminder: () -> Unit,
    onEntityTypeChange: (ReminderEntityType) -> Unit,
    onEntityIdChange: (String) -> Unit,
    onScheduledTimeChange: (LocalTime) -> Unit,
    onScheduledDateChange: (java.time.LocalDate?) -> Unit,
    onRecurrenceTypeChange: (RecurrenceType?) -> Unit,
    onToggleWeekday: (DayOfWeek) -> Unit,
    onCustomIntervalChange: (Int) -> Unit,
    onConditionalChange: (Boolean) -> Unit,
    onEscalationIntervalChange: (Int) -> Unit,
    onMaxEscalationsChange: (Int) -> Unit,
    onClearError: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onClearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateClick,
                modifier = Modifier.semantics { contentDescription = "Create reminder" }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add reminder")
            }
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = DopaShiftTheme.spacing.gutter)
        ) {
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))

            Text(
                text = "Notifications & Reminders",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

            // Quiet hours display (Requirement 5.13)
            QuietHoursDisplay(
                startTime = uiState.quietHoursStart,
                endTime = uiState.quietHoursEnd
            )

            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))

            if (uiState.reminders.isEmpty()) {
                EmptyRemindersState()
            } else {
                RemindersList(
                    reminders = uiState.reminders,
                    onEditClick = onEditClick,
                    onDeleteClick = onDeleteClick
                )
            }
        }

        // Create/Edit dialog
        if (uiState.showCreateDialog) {
            ReminderFormDialog(
                isEditing = uiState.editingReminder != null,
                formState = formState,
                onDismiss = onDismissDialog,
                onSave = onSaveReminder,
                onEntityTypeChange = onEntityTypeChange,
                onEntityIdChange = onEntityIdChange,
                onScheduledTimeChange = onScheduledTimeChange,
                onScheduledDateChange = onScheduledDateChange,
                onRecurrenceTypeChange = onRecurrenceTypeChange,
                onToggleWeekday = onToggleWeekday,
                onCustomIntervalChange = onCustomIntervalChange,
                onConditionalChange = onConditionalChange,
                onEscalationIntervalChange = onEscalationIntervalChange,
                onMaxEscalationsChange = onMaxEscalationsChange
            )
        }
    }
}

// =====================================================================================
// Quiet Hours Display (Requirement 5.13)
// =====================================================================================

@Composable
private fun QuietHoursDisplay(
    startTime: LocalTime?,
    endTime: LocalTime?
) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DopaShiftTheme.spacing.gutter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.NotificationsOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.stackSm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Quiet Hours",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                if (startTime != null && endTime != null) {
                    Text(
                        text = "${startTime.format(timeFormatter)} – ${endTime.format(timeFormatter)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Reminders suppressed during this window",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Not configured",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// =====================================================================================
// Empty State
// =====================================================================================

@Composable
private fun EmptyRemindersState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))
            Text(
                text = "No reminders yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
            Text(
                text = "Tap + to create your first reminder",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

// =====================================================================================
// Reminders List with Swipe-to-Delete
// =====================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemindersList(
    reminders: List<Reminder>,
    onEditClick: (Reminder) -> Unit,
    onDeleteClick: (java.util.UUID) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base)
    ) {
        items(
            items = reminders,
            key = { it.id }
        ) { reminder ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value == SwipeToDismissBoxValue.EndToStart) {
                        onDeleteClick(reminder.id)
                        true
                    } else {
                        false
                    }
                }
            )

            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {
                    val color by animateColorAsState(
                        targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            Color.Transparent
                        },
                        label = "swipe_bg"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(color)
                            .padding(horizontal = DopaShiftTheme.spacing.gutter),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete reminder",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                },
                enableDismissFromStartToEnd = false
            ) {
                ReminderCard(
                    reminder = reminder,
                    onEditClick = { onEditClick(reminder) },
                    onDeleteClick = { onDeleteClick(reminder.id) }
                )
            }
        }
    }
}

// =====================================================================================
// Reminder Card
// =====================================================================================

@Composable
private fun ReminderCard(
    reminder: Reminder,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DopaShiftTheme.spacing.gutter)
        ) {
            // Header row: entity type + actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Entity type badge
                Text(
                    text = when (reminder.entityType) {
                        ReminderEntityType.DAILY_TODO -> "Daily Todo"
                        ReminderEntityType.GOAL_CHECKLIST -> "Goal Checklist"
                        ReminderEntityType.HABIT_CHECKPOINT -> "Habit Checkpoint"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )

                Row {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit reminder",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete reminder",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

            // Time display
            Text(
                text = reminder.scheduledTime.format(timeFormatter),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // Date or recurrence info
            val scheduleText = buildScheduleText(reminder, dateFormatter)
            Text(
                text = scheduleText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

            // Tags row: conditional, escalation
            Row(
                horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base)
            ) {
                if (reminder.condition != null) {
                    TagChip(text = "Conditional")
                }
                if (reminder.maxEscalations > 0) {
                    TagChip(
                        text = "Escalation ×${reminder.maxEscalations}"
                    )
                }
            }
        }
    }
}

@Composable
private fun TagChip(text: String) {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = DopaShiftTheme.spacing.base, vertical = DopaShiftTheme.spacing.compact)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun buildScheduleText(
    reminder: Reminder,
    dateFormatter: DateTimeFormatter
): String {
    val recurrence = reminder.recurrence
    val scheduledDate = reminder.scheduledDate
    return when {
        recurrence != null -> {
            when (recurrence.type) {
                RecurrenceType.DAILY -> "Repeats daily"
                RecurrenceType.SPECIFIC_WEEKDAYS -> {
                    val days = recurrence.weekdays
                        ?.sortedBy { it.value }
                        ?.joinToString(", ") {
                            it.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        }
                    "Repeats $days"
                }
                RecurrenceType.WEEKLY -> "Repeats weekly"
                RecurrenceType.MONTHLY -> "Repeats monthly"
                RecurrenceType.CUSTOM_INTERVAL -> {
                    val days = recurrence.intervalDays ?: 1
                    "Every $days day${if (days > 1) "s" else ""}"
                }
            }
        }
        scheduledDate != null -> scheduledDate.format(dateFormatter)
        else -> "One-off"
    }
}

// =====================================================================================
// Create/Edit Reminder Dialog (Requirements 5.1, 5.2, 5.3, 5.4, 5.11)
// =====================================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReminderFormDialog(
    isEditing: Boolean,
    formState: ReminderFormState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onEntityTypeChange: (ReminderEntityType) -> Unit,
    onEntityIdChange: (String) -> Unit,
    onScheduledTimeChange: (LocalTime) -> Unit,
    onScheduledDateChange: (java.time.LocalDate?) -> Unit,
    onRecurrenceTypeChange: (RecurrenceType?) -> Unit,
    onToggleWeekday: (DayOfWeek) -> Unit,
    onCustomIntervalChange: (Int) -> Unit,
    onConditionalChange: (Boolean) -> Unit,
    onEscalationIntervalChange: (Int) -> Unit,
    onMaxEscalationsChange: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEditing) "Edit Reminder" else "Create Reminder")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // === Entity Type Selector (Requirement 5.1) ===
                EntityTypeSelector(
                    selectedType = formState.entityType,
                    onTypeChange = onEntityTypeChange
                )

                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))

                // === Entity ID ===
                OutlinedTextField(
                    value = formState.entityId,
                    onValueChange = onEntityIdChange,
                    label = { Text("Entity ID") },
                    placeholder = { Text("UUID of the linked item") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))

                // === Time Selector ===
                TimeSelector(
                    time = formState.scheduledTime,
                    onTimeChange = onScheduledTimeChange
                )

                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))

                // === Recurrence Type (Requirement 5.3) ===
                RecurrenceTypeSelector(
                    selectedType = formState.recurrenceType,
                    onTypeChange = onRecurrenceTypeChange
                )

                // Weekday chips (visible for SPECIFIC_WEEKDAYS)
                if (formState.recurrenceType == RecurrenceType.SPECIFIC_WEEKDAYS) {
                    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
                    WeekdayChipSelector(
                        selectedDays = formState.selectedWeekdays,
                        onToggleDay = onToggleWeekday
                    )
                }

                // Custom interval input (visible for CUSTOM_INTERVAL)
                if (formState.recurrenceType == RecurrenceType.CUSTOM_INTERVAL) {
                    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
                    CustomIntervalInput(
                        days = formState.customIntervalDays,
                        onDaysChange = onCustomIntervalChange
                    )
                }

                // One-off date (visible when no recurrence)
                if (formState.recurrenceType == null) {
                    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
                    OutlinedTextField(
                        value = formState.scheduledDate?.toString() ?: "",
                        onValueChange = { input ->
                            val date = try {
                                java.time.LocalDate.parse(input)
                            } catch (_: Exception) {
                                null
                            }
                            onScheduledDateChange(date)
                        },
                        label = { Text("Date (YYYY-MM-DD)") },
                        placeholder = { Text("2025-01-15") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))

                // === Conditional Toggle (Requirement 5.4) ===
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Conditional",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Fire only if entity is incomplete",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = formState.isConditional,
                        onCheckedChange = onConditionalChange
                    )
                }

                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))

                // === Escalation Config (Requirement 5.11) ===
                Text(
                    text = "Escalation",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

                // Escalation interval (5-120 minutes)
                Text(
                    text = "Interval: ${formState.escalationIntervalMinutes} min",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = formState.escalationIntervalMinutes.toFloat(),
                    onValueChange = { onEscalationIntervalChange(it.toInt()) },
                    valueRange = 5f..120f,
                    steps = 22,
                    modifier = Modifier.fillMaxWidth()
                )

                // Max escalation count (1-10)
                Text(
                    text = "Max escalations: ${formState.maxEscalations}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = formState.maxEscalations.toFloat(),
                    onValueChange = { onMaxEscalationsChange(it.toInt()) },
                    valueRange = 1f..10f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth()
                )

                // Validation error
                if (formState.validationError != null) {
                    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
                    Text(
                        text = formState.validationError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(if (isEditing) "Save" else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// =====================================================================================
// Entity Type Selector (Requirement 5.1)
// =====================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntityTypeSelector(
    selectedType: ReminderEntityType,
    onTypeChange: (ReminderEntityType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = when (selectedType) {
                ReminderEntityType.DAILY_TODO -> "Daily Todo"
                ReminderEntityType.GOAL_CHECKLIST -> "Goal Checklist"
                ReminderEntityType.HABIT_CHECKPOINT -> "Habit Checkpoint"
            },
            onValueChange = {},
            readOnly = true,
            label = { Text("Entity Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ReminderEntityType.entries.forEach { type ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (type) {
                                ReminderEntityType.DAILY_TODO -> "Daily Todo"
                                ReminderEntityType.GOAL_CHECKLIST -> "Goal Checklist"
                                ReminderEntityType.HABIT_CHECKPOINT -> "Habit Checkpoint"
                            }
                        )
                    },
                    onClick = {
                        onTypeChange(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

// =====================================================================================
// Time Selector
// =====================================================================================

@Composable
private fun TimeSelector(
    time: LocalTime,
    onTimeChange: (LocalTime) -> Unit
) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var hourText by remember(time) { mutableStateOf(time.hour.toString().padStart(2, '0')) }
    var minuteText by remember(time) { mutableStateOf(time.minute.toString().padStart(2, '0')) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base)
    ) {
        OutlinedTextField(
            value = hourText,
            onValueChange = { input ->
                if (input.length <= 2 && input.all { it.isDigit() }) {
                    hourText = input
                    val hour = input.toIntOrNull()
                    if (hour != null && hour in 0..23) {
                        onTimeChange(LocalTime.of(hour, time.minute))
                    }
                }
            },
            label = { Text("Hour") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Text(":", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = minuteText,
            onValueChange = { input ->
                if (input.length <= 2 && input.all { it.isDigit() }) {
                    minuteText = input
                    val minute = input.toIntOrNull()
                    if (minute != null && minute in 0..59) {
                        onTimeChange(LocalTime.of(time.hour, minute))
                    }
                }
            },
            label = { Text("Minute") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }
}

// =====================================================================================
// Recurrence Type Selector (Requirement 5.3)
// =====================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurrenceTypeSelector(
    selectedType: RecurrenceType?,
    onTypeChange: (RecurrenceType?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedType?.let { recurrenceTypeLabel(it) } ?: "One-off (no repeat)",
            onValueChange = {},
            readOnly = true,
            label = { Text("Recurrence") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // One-off option
            DropdownMenuItem(
                text = { Text("One-off (no repeat)") },
                onClick = {
                    onTypeChange(null)
                    expanded = false
                }
            )
            // Recurrence options
            RecurrenceType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(recurrenceTypeLabel(type)) },
                    onClick = {
                        onTypeChange(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun recurrenceTypeLabel(type: RecurrenceType): String {
    return when (type) {
        RecurrenceType.DAILY -> "Daily"
        RecurrenceType.SPECIFIC_WEEKDAYS -> "Specific Weekdays"
        RecurrenceType.WEEKLY -> "Weekly"
        RecurrenceType.MONTHLY -> "Monthly"
        RecurrenceType.CUSTOM_INTERVAL -> "Custom Interval"
    }
}

// =====================================================================================
// Weekday Chip Selector (Requirement 5.3: specific weekdays)
// =====================================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdayChipSelector(
    selectedDays: Set<DayOfWeek>,
    onToggleDay: (DayOfWeek) -> Unit
) {
    Text(
        text = "Select days",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.compact))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base),
        verticalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.compact)
    ) {
        DayOfWeek.entries.forEach { day ->
            FilterChip(
                selected = selectedDays.contains(day),
                onClick = { onToggleDay(day) },
                label = {
                    Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
                }
            )
        }
    }
}

// =====================================================================================
// Custom Interval Input (Requirement 5.3: every N days, 1-365)
// =====================================================================================

@Composable
private fun CustomIntervalInput(
    days: Int,
    onDaysChange: (Int) -> Unit
) {
    var text by remember(days) { mutableStateOf(days.toString()) }

    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            if (input.length <= 3 && input.all { it.isDigit() }) {
                text = input
                val value = input.toIntOrNull()
                if (value != null && value in 1..365) {
                    onDaysChange(value)
                }
            }
        },
        label = { Text("Every N days (1–365)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
