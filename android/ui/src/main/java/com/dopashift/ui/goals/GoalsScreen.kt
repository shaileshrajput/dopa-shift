package com.dopashift.ui.goals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dopashift.domain.entity.CheckpointStatus
import com.dopashift.domain.entity.GoalChecklistItem
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.ui.theme.DopaShiftTheme
import java.util.UUID

/**
 * Goals & Habits screen implementing Requirements 1.1, 1.2, 1.3, 1.5, 1.6, 1.7.
 *
 * Features:
 * - Goal list with progress indicators and keyword chips
 * - Goal detail view with keywords, checklist items, and active habit tracks
 * - Create/edit/delete goal dialogs with dependency confirmation
 */
@Composable
fun GoalsScreen(
    viewModel: GoalsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    GoalsScreenContent(
        uiState = uiState,
        onGoalClick = viewModel::selectGoal,
        onBackClick = viewModel::clearSelection,
        onCreateClick = viewModel::showCreateDialog,
        onEditClick = viewModel::showEditDialog,
        onDeleteClick = viewModel::showDeleteDialog,
        onDismissCreate = viewModel::dismissCreateDialog,
        onDismissEdit = viewModel::dismissEditDialog,
        onDismissDelete = viewModel::dismissDeleteDialog,
        onCreateGoal = viewModel::createGoal,
        onUpdateGoal = viewModel::updateGoal,
        onDeleteGoal = viewModel::deleteGoal,
        onAddChecklistItem = viewModel::addChecklistItem,
        onToggleChecklistItem = viewModel::toggleChecklistItem,
        onDeleteChecklistItem = viewModel::deleteChecklistItem,
        onClearError = viewModel::clearError
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalsScreenContent(
    uiState: GoalsUiState,
    onGoalClick: (UUID) -> Unit,
    onBackClick: () -> Unit,
    onCreateClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onDismissCreate: () -> Unit,
    onDismissEdit: () -> Unit,
    onDismissDelete: () -> Unit,
    onCreateGoal: (String, String, List<String>) -> Unit,
    onUpdateGoal: (String, String, List<String>) -> Unit,
    onDeleteGoal: (DeleteGoalAction) -> Unit,
    onAddChecklistItem: (String) -> Unit,
    onToggleChecklistItem: (GoalChecklistItem) -> Unit,
    onDeleteChecklistItem: (UUID) -> Unit,
    onClearError: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

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
                title = {
                    Text(
                        text = if (uiState.selectedGoal != null) uiState.selectedGoal.name
                        else "Goals & Habits"
                    )
                },
                navigationIcon = {
                    if (uiState.selectedGoal != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (uiState.selectedGoal != null) {
                        IconButton(onClick = onEditClick) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit goal")
                        }
                        IconButton(onClick = onDeleteClick) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete goal")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.selectedGoal == null) {
                FloatingActionButton(onClick = onCreateClick) {
                    Icon(Icons.Default.Add, contentDescription = "Create goal")
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            AnimatedVisibility(
                visible = uiState.selectedGoal == null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                GoalListContent(
                    goals = uiState.goals,
                    goalProgressMap = uiState.goalProgressMap,
                    isLoading = uiState.isLoading,
                    onGoalClick = onGoalClick
                )
            }

            AnimatedVisibility(
                visible = uiState.selectedGoal != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                uiState.selectedGoal?.let { goal ->
                    GoalDetailContent(
                        goal = goal,
                        checklistItems = uiState.selectedGoalChecklist,
                        habitTracks = uiState.selectedGoalHabitTracks,
                        onAddChecklistItem = onAddChecklistItem,
                        onToggleChecklistItem = onToggleChecklistItem,
                        onDeleteChecklistItem = onDeleteChecklistItem
                    )
                }
            }
        }
    }

    // Dialogs
    if (uiState.showCreateDialog) {
        GoalFormDialog(
            title = "Create Goal",
            initialName = "",
            initialCategory = "",
            initialKeywords = emptyList(),
            onDismiss = onDismissCreate,
            onConfirm = { name, category, keywords -> onCreateGoal(name, category, keywords) }
        )
    }

    if (uiState.showEditDialog && uiState.selectedGoal != null) {
        GoalFormDialog(
            title = "Edit Goal",
            initialName = uiState.selectedGoal.name,
            initialCategory = uiState.selectedGoal.category,
            initialKeywords = uiState.selectedGoal.keywords,
            onDismiss = onDismissEdit,
            onConfirm = { name, category, keywords -> onUpdateGoal(name, category, keywords) }
        )
    }

    if (uiState.showDeleteDialog && uiState.selectedGoal != null) {
        DeleteGoalDialog(
            goalName = uiState.selectedGoal.name,
            dependents = uiState.deleteDialogDependents,
            onDismiss = onDismissDelete,
            onConfirm = onDeleteGoal
        )
    }
}

// === Goal List ===

@Composable
private fun GoalListContent(
    goals: List<GoalProfile>,
    goalProgressMap: Map<UUID, GoalProgress>,
    isLoading: Boolean,
    onGoalClick: (UUID) -> Unit
) {
    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        goals.isEmpty() -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No goals yet",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
                Text(
                    text = "Create a goal to get started",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(DopaShiftTheme.spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.stackSm)
            ) {
                items(goals, key = { it.id }) { goal ->
                    GoalCard(
                        goal = goal,
                        progress = goalProgressMap[goal.id],
                        onClick = { onGoalClick(goal.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalCard(
    goal: GoalProfile,
    progress: GoalProgress?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DopaShiftTheme.spacing.gutter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Progress ring
            GoalProgressRing(
                progress = progress?.fraction ?: 0f,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.stackSm))

            // Goal info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = goal.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.base))
                    CategoryBadge(category = goal.category)
                }
                if (progress != null && progress.totalItems > 0) {
                    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.compact))
                    Text(
                        text = "${progress.completedItems}/${progress.totalItems} tasks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (goal.keywords.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.compact),
                        verticalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.compact)
                    ) {
                        goal.keywords.take(4).forEach { keyword ->
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = keyword,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                        if (goal.keywords.size > 4) {
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = "+${goal.keywords.size - 4}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Circular progress ring using Canvas for the goal card.
 * Shows checklist completion percentage with the DopaShift Momentum Teal color.
 */
@Composable
private fun GoalProgressRing(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val progressColor = DopaShiftTheme.colors.momentumTeal
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val percentText = "${(progress * 100).toInt()}%"

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 4.dp.toPx()
            val arcSize = Size(
                size.width - strokeWidth,
                size.height - strokeWidth
            )
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

            // Background track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Progress arc
            if (progress > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
        Text(
            text = percentText,
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}

/**
 * Category badge — a small tonal chip showing the goal's category.
 */
@Composable
private fun CategoryBadge(category: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            text = category,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(
                horizontal = DopaShiftTheme.spacing.base,
                vertical = DopaShiftTheme.spacing.compact
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// === Goal Detail ===

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalDetailContent(
    goal: GoalProfile,
    checklistItems: List<GoalChecklistItem>,
    habitTracks: List<HabitTrack>,
    onAddChecklistItem: (String) -> Unit,
    onToggleChecklistItem: (GoalChecklistItem) -> Unit,
    onDeleteChecklistItem: (UUID) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(DopaShiftTheme.spacing.gutter)
    ) {
        // Category
        Text(
            text = goal.category,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))

        // Keywords section
        if (goal.keywords.isNotEmpty()) {
            Text(
                text = "Keywords",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base),
                verticalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.compact)
            ) {
                goal.keywords.forEach { keyword ->
                    AssistChip(
                        onClick = {},
                        label = { Text(keyword) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))
        }

        // Checklist section (Requirement 1.3)
        Text(
            text = "Checklist",
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

        ChecklistSection(
            items = checklistItems,
            onAddItem = onAddChecklistItem,
            onToggleItem = onToggleChecklistItem,
            onDeleteItem = onDeleteChecklistItem
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))

        // Habit Tracks section
        Text(
            text = "Habit Tracks",
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

        if (habitTracks.isEmpty()) {
            Text(
                text = "No active habit tracks for this goal",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            habitTracks.forEach { track ->
                HabitTrackCard(track = track)
                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
            }
        }
    }
}

@Composable
private fun ChecklistSection(
    items: List<GoalChecklistItem>,
    onAddItem: (String) -> Unit,
    onToggleItem: (GoalChecklistItem) -> Unit,
    onDeleteItem: (UUID) -> Unit
) {
    var newItemText by rememberSaveable { mutableStateOf("") }

    Column {
        items.forEach { item ->
            ChecklistItemRow(
                item = item,
                onToggle = { onToggleItem(item) },
                onDelete = { onDeleteItem(item.id) }
            )
        }

        // Add new item inline
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newItemText,
                onValueChange = { newItemText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Add checklist item") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (newItemText.isNotBlank()) {
                            onAddItem(newItemText.trim())
                            newItemText = ""
                        }
                    }
                )
            )
            IconButton(
                onClick = {
                    if (newItemText.isNotBlank()) {
                        onAddItem(newItemText.trim())
                        newItemText = ""
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add item")
            }
        }
    }
}

@Composable
private fun ChecklistItemRow(
    item: GoalChecklistItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = DopaShiftTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = item.isCompleted,
            onCheckedChange = { onToggle() }
        )
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
            color = if (item.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete item",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun HabitTrackCard(track: HabitTrack) {
    val completedDays = track.checkpoints.count { it.status == CheckpointStatus.COMPLETED }
    val progress = completedDays / 30f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(DopaShiftTheme.spacing.stackSm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Day ${track.currentDay}/30",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = if (track.isFinished) "Completed" else "Active",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (track.isFinished) DopaShiftTheme.colors.successGreen
                    else DopaShiftTheme.colors.momentumTeal
                )
            }
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = DopaShiftTheme.colors.momentumTeal,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.compact))
            Text(
                text = "$completedDays of 30 days completed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// === Dialogs ===

/**
 * Dialog for creating or editing a goal.
 * Validates name (1-100 chars), category (1-50 chars), keywords (max 20, each 1-50 chars).
 */
@Composable
private fun GoalFormDialog(
    title: String,
    initialName: String,
    initialCategory: String,
    initialKeywords: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, category: String, keywords: List<String>) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var category by rememberSaveable { mutableStateOf(initialCategory) }
    var keywordsText by rememberSaveable { mutableStateOf(initialKeywords.joinToString(", ")) }

    val nameError = when {
        name.isBlank() -> "Name is required"
        name.length > 100 -> "Name must be at most 100 characters"
        else -> null
    }

    val categoryError = when {
        category.isBlank() -> "Category is required"
        category.length > 50 -> "Category must be at most 50 characters"
        else -> null
    }

    val parsedKeywords = keywordsText
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    val keywordsError = when {
        parsedKeywords.isEmpty() -> "At least one keyword is required"
        parsedKeywords.size > 20 -> "Maximum 20 keywords allowed"
        parsedKeywords.any { it.length > 50 } -> "Each keyword must be at most 50 characters"
        else -> null
    }

    val isValid = nameError == null && categoryError == null && keywordsError == null
        && name.isNotBlank() && category.isNotBlank() && parsedKeywords.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    isError = name.isNotEmpty() && nameError != null,
                    supportingText = {
                        if (name.isNotEmpty() && nameError != null) Text(nameError)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    isError = category.isNotEmpty() && categoryError != null,
                    supportingText = {
                        if (category.isNotEmpty() && categoryError != null) Text(categoryError)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
                OutlinedTextField(
                    value = keywordsText,
                    onValueChange = { keywordsText = it },
                    label = { Text("Keywords (comma-separated)") },
                    isError = keywordsText.isNotEmpty() && keywordsError != null,
                    supportingText = {
                        if (keywordsText.isNotEmpty() && keywordsError != null) Text(keywordsError)
                        else Text("${parsedKeywords.size}/20 keywords")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), category.trim(), parsedKeywords) },
                enabled = isValid
            ) {
                Text(if (title == "Create Goal") "Create" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Delete confirmation dialog showing dependent items per Requirement 1.5.
 * User must choose: delete all dependents or reassign to another goal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteGoalDialog(
    goalName: String,
    dependents: DeleteDependents?,
    onDismiss: () -> Unit,
    onConfirm: (DeleteGoalAction) -> Unit
) {
    var selectedAction by remember { mutableStateOf<DeleteGoalAction>(DeleteGoalAction.DeleteAll) }
    var reassignTargetId by remember { mutableStateOf<UUID?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val hasReassignTargets = dependents?.availableGoals?.isNotEmpty() == true
    val hasDependents = dependents != null &&
        (dependents.checklistItems.isNotEmpty() || dependents.habitTracks.isNotEmpty())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Goal") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Are you sure you want to delete \"$goalName\"?",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (hasDependents) {
                    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))
                    Text(
                        text = "This goal has dependent items:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

                    if (dependents!!.checklistItems.isNotEmpty()) {
                        Text(
                            text = "\u2022 ${dependents.checklistItems.size} checklist item(s)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (dependents.habitTracks.isNotEmpty()) {
                        Text(
                            text = "\u2022 ${dependents.habitTracks.size} habit track(s)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))
                    Text(
                        text = "Choose an action for dependent items:",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

                    // Delete All option
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedAction = DeleteGoalAction.DeleteAll }
                            .padding(vertical = DopaShiftTheme.spacing.compact)
                    ) {
                        RadioButton(
                            selected = selectedAction is DeleteGoalAction.DeleteAll,
                            onClick = { selectedAction = DeleteGoalAction.DeleteAll }
                        )
                        Text(
                            text = "Delete all dependent items",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Reassign option (only if other goals available)
                    if (hasReassignTargets) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val firstGoal = dependents.availableGoals.first()
                                    reassignTargetId = firstGoal.id
                                    selectedAction = DeleteGoalAction.Reassign(firstGoal.id)
                                }
                                .padding(vertical = DopaShiftTheme.spacing.compact)
                        ) {
                            RadioButton(
                                selected = selectedAction is DeleteGoalAction.Reassign,
                                onClick = {
                                    val firstGoal = dependents.availableGoals.first()
                                    reassignTargetId = firstGoal.id
                                    selectedAction = DeleteGoalAction.Reassign(firstGoal.id)
                                }
                            )
                            Text(
                                text = "Reassign to another goal",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // Goal selection dropdown (shown when Reassign is selected)
                        if (selectedAction is DeleteGoalAction.Reassign) {
                            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
                            ExposedDropdownMenuBox(
                                expanded = dropdownExpanded,
                                onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = dependents.availableGoals
                                        .find { it.id == reassignTargetId }?.name ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Target goal") },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                                    },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = dropdownExpanded,
                                    onDismissRequest = { dropdownExpanded = false }
                                ) {
                                    dependents.availableGoals.forEach { goal ->
                                        DropdownMenuItem(
                                            text = { Text(goal.name) },
                                            onClick = {
                                                reassignTargetId = goal.id
                                                selectedAction = DeleteGoalAction.Reassign(goal.id)
                                                dropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedAction) },
                enabled = when (selectedAction) {
                    is DeleteGoalAction.DeleteAll -> true
                    is DeleteGoalAction.Reassign -> reassignTargetId != null
                }
            ) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
