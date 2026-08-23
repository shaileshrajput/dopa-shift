package com.dopashift.ui.tasks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.ui.theme.DopaShiftTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

/**
 * Daily Tasks screen implementing Requirements 4.1, 4.2, 4.3.
 *
 * Features:
 * - List todos for selected day (reactive via Flow)
 * - Create new todo (FAB triggers inline input)
 * - Edit existing todo (inline editing)
 * - Delete todo (swipe to dismiss)
 * - Mark complete with checkbox — strikethrough text, un-check to revert
 * - 500-char text validation
 */
@Composable
fun DailyTasksScreen(
    viewModel: DailyTasksViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DailyTasksScreenContent(
        uiState = uiState,
        onDateChange = viewModel::selectDate,
        onCreateTodo = viewModel::createTodo,
        onUpdateTodoText = viewModel::updateTodoText,
        onDeleteTodo = viewModel::deleteTodo,
        onToggleComplete = viewModel::toggleComplete,
        onStartEditing = viewModel::startEditing,
        onCancelEditing = viewModel::cancelEditing,
        onClearError = viewModel::clearError
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyTasksScreenContent(
    uiState: DailyTasksUiState,
    onDateChange: (LocalDate) -> Unit,
    onCreateTodo: (String) -> Unit,
    onUpdateTodoText: (UUID, String) -> Unit,
    onDeleteTodo: (UUID) -> Unit,
    onToggleComplete: (DailyTodoItem) -> Unit,
    onStartEditing: (UUID) -> Unit,
    onCancelEditing: () -> Unit,
    onClearError: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showNewTaskInput by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Daily Tasks") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewTaskInput = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add task")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Date selector
            DateSelector(
                selectedDate = uiState.selectedDate,
                onDateChange = onDateChange
            )

            // Task count
            val completedCount = uiState.todos.count { it.isCompleted }
            val totalCount = uiState.todos.size
            if (totalCount > 0) {
                Text(
                    text = "$completedCount of $totalCount completed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = DopaShiftTheme.spacing.gutter,
                        vertical = DopaShiftTheme.spacing.compact
                    )
                )
            }

            // Content
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = DopaShiftTheme.spacing.gutter),
                        verticalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base)
                    ) {
                        // New task input at top when triggered
                        if (showNewTaskInput) {
                            item(key = "new_task_input") {
                                NewTaskInput(
                                    onSubmit = { text ->
                                        onCreateTodo(text)
                                        showNewTaskInput = false
                                    },
                                    onDismiss = { showNewTaskInput = false }
                                )
                            }
                        }

                        // Incomplete tasks first
                        val incompleteTodos = uiState.todos.filter { !it.isCompleted }
                        val completedTodos = uiState.todos.filter { it.isCompleted }

                        items(incompleteTodos, key = { it.id }) { item ->
                            SwipeableTodoItem(
                                item = item,
                                isEditing = uiState.editingItemId == item.id,
                                onToggleComplete = { onToggleComplete(item) },
                                onStartEditing = { onStartEditing(item.id) },
                                onCancelEditing = onCancelEditing,
                                onSaveEdit = { newText -> onUpdateTodoText(item.id, newText) },
                                onDelete = { onDeleteTodo(item.id) }
                            )
                        }

                        // Completed section header
                        if (completedTodos.isNotEmpty()) {
                            item(key = "completed_header") {
                                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
                                Text(
                                    text = "Completed",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(
                                        vertical = DopaShiftTheme.spacing.compact
                                    )
                                )
                            }
                        }

                        items(completedTodos, key = { it.id }) { item ->
                            SwipeableTodoItem(
                                item = item,
                                isEditing = uiState.editingItemId == item.id,
                                onToggleComplete = { onToggleComplete(item) },
                                onStartEditing = { onStartEditing(item.id) },
                                onCancelEditing = onCancelEditing,
                                onSaveEdit = { newText -> onUpdateTodoText(item.id, newText) },
                                onDelete = { onDeleteTodo(item.id) }
                            )
                        }

                        // Empty state
                        if (uiState.todos.isEmpty() && !showNewTaskInput) {
                            item(key = "empty_state") {
                                EmptyTasksState()
                            }
                        }

                        // Bottom spacer for FAB clearance
                        item(key = "bottom_spacer") {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
    }
}

// === Date Selector ===

@Composable
private fun DateSelector(
    selectedDate: LocalDate,
    onDateChange: (LocalDate) -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val isToday = selectedDate == LocalDate.now()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = DopaShiftTheme.spacing.gutter,
                vertical = DopaShiftTheme.spacing.base
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onDateChange(selectedDate.minusDays(1)) }) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous day")
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isToday) "Today" else selectedDate.format(dateFormatter),
                style = MaterialTheme.typography.titleMedium
            )
            if (isToday) {
                Text(
                    text = selectedDate.format(dateFormatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(onClick = { onDateChange(selectedDate.plusDays(1)) }) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Next day")
        }
    }
}

// === New Task Input ===

@Composable
private fun NewTaskInput(
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val charCount = text.length
    val isOverLimit = charCount > 500

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(DopaShiftTheme.spacing.stackSm)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = { Text("What do you need to do?") },
                isError = isOverLimit,
                supportingText = {
                    Text(
                        text = "$charCount/500",
                        color = if (isOverLimit) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (text.isNotBlank() && !isOverLimit) {
                            onSubmit(text)
                        }
                    }
                ),
                maxLines = 3
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }
                IconButton(
                    onClick = {
                        if (text.isNotBlank() && !isOverLimit) {
                            onSubmit(text)
                        }
                    },
                    enabled = text.isNotBlank() && !isOverLimit
                ) {
                    Icon(Icons.Default.Done, contentDescription = "Add task")
                }
            }
        }
    }
}

// === Swipeable Todo Item ===

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTodoItem(
    item: DailyTodoItem,
    isEditing: Boolean,
    onToggleComplete: () -> Unit,
    onStartEditing: () -> Unit,
    onCancelEditing: () -> Unit,
    onSaveEdit: (String) -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
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
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    else -> Color.Transparent
                },
                label = "swipe_bg_color"
            )
            val scale by animateFloatAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) 1f else 0.75f,
                label = "swipe_icon_scale"
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
                    contentDescription = "Delete",
                    modifier = Modifier.scale(scale),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        enableDismissFromStartToEnd = false
    ) {
        if (isEditing) {
            EditingTodoRow(
                item = item,
                onSave = onSaveEdit,
                onCancel = onCancelEditing
            )
        } else {
            TodoItemRow(
                item = item,
                onToggleComplete = onToggleComplete,
                onStartEditing = onStartEditing
            )
        }
    }
}

// === Todo Item Row (display mode) ===

@Composable
private fun TodoItemRow(
    item: DailyTodoItem,
    onToggleComplete: () -> Unit,
    onStartEditing: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isCompleted)
                MaterialTheme.colorScheme.surfaceContainerLow
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onStartEditing)
                .padding(
                    horizontal = DopaShiftTheme.spacing.stackSm,
                    vertical = DopaShiftTheme.spacing.base
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isCompleted,
                onCheckedChange = { onToggleComplete() }
            )
            Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.base))
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                color = if (item.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onStartEditing) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit task",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(DopaShiftTheme.spacing.compact)
                )
            }
        }
    }
}

// === Editing Todo Row (inline edit mode) ===

@Composable
private fun EditingTodoRow(
    item: DailyTodoItem,
    onSave: (String) -> Unit,
    onCancel: () -> Unit
) {
    var editText by rememberSaveable(item.id) { mutableStateOf(item.text) }
    val focusRequester = remember { FocusRequester() }
    val charCount = editText.length
    val isOverLimit = charCount > 500

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(DopaShiftTheme.spacing.stackSm)) {
            OutlinedTextField(
                value = editText,
                onValueChange = { editText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                isError = isOverLimit,
                supportingText = {
                    Text(
                        text = "$charCount/500",
                        color = if (isOverLimit) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (editText.isNotBlank() && !isOverLimit) {
                            onSave(editText)
                        }
                    }
                ),
                maxLines = 3
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel edit")
                }
                IconButton(
                    onClick = {
                        if (editText.isNotBlank() && !isOverLimit) {
                            onSave(editText)
                        }
                    },
                    enabled = editText.isNotBlank() && !isOverLimit
                ) {
                    Icon(Icons.Default.Done, contentDescription = "Save edit")
                }
            }
        }
    }
}

// === Empty State ===

@Composable
private fun EmptyTasksState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = DopaShiftTheme.spacing.stackLg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No tasks for this day",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
        Text(
            text = "Tap + to add your first task",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
