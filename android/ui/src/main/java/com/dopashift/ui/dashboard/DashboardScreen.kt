package com.dopashift.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.ui.R
import com.dopashift.ui.theme.DopaShiftTheme
import com.dopashift.ui.theme.minTaskRowHeight
import com.dopashift.ui.theme.politeLiveRegion
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Dashboard screen implementing Requirements 20.1–20.15.
 *
 * Layout matches the wireframe exactly (mobile single-column):
 * 1. "Overview" display heading + subtitle
 * 2. Efficiency Score card with circular progress ring
 * 3. "Active Goals" heading + full-width stacked goal cards
 * 4. "Today's Focus" card with todo items + inline quick-add
 * 5. Activity feed (paginated)
 *
 * Reactive data: Room -> Repository (Flow) -> ViewModel (combine) -> UI.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateToInterceptionRules: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sectionData by viewModel.sectionData.collectAsStateWithLifecycle()

    DashboardContent(
        uiState = uiState,
        sectionData = sectionData,
        onTodoCheckedChange = { todoId, isCompleted -> viewModel.setTodoCompleted(todoId, isCompleted) },
        onHabitComplete = { trackId -> viewModel.completeHabitCheckpoint(trackId) },
        onLoadMoreActivity = { viewModel.loadMoreActivityFeed() },
        onAddTodo = { text -> viewModel.addTodo(text) },
        onNavigateToInterceptionRules = onNavigateToInterceptionRules
    )
}

@Composable
internal fun DashboardContent(
    uiState: DashboardUiState,
    sectionData: DashboardSectionData,
    onTodoCheckedChange: (UUID, Boolean) -> Unit,
    onHabitComplete: (UUID) -> Unit,
    onLoadMoreActivity: () -> Unit,
    onAddTodo: (String) -> Unit,
    onNavigateToInterceptionRules: () -> Unit
) {
    // DUX-3 AC3: skeleton-first loading (full-screen spinner is a placeholder until the
    // SectionSkeleton composables land in task 10.2).
    if (uiState is DashboardUiState.Loading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val content = uiState as DashboardUiState.Content

    val isEmpty = sectionData.goalProgressSummaries.isEmpty() &&
        sectionData.todayTodos.isEmpty() &&
        sectionData.overdueTodos.isEmpty() &&
        sectionData.activeHabitTracks.isEmpty()

    if (isEmpty) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(DopaShiftTheme.spacing.margin),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.dux_dashboard_empty_title),
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
                Text(
                    text = stringResource(R.string.dux_dashboard_empty_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = DopaShiftTheme.spacing.margin),
        verticalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.margin)
    ) {
        item { Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base)) }

        // === 1. "Overview" heading + subtitle (wireframe: large display text) ===
        item {
            Column {
                Text(
                    text = stringResource(R.string.dux_dashboard_overview_title),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
                Text(
                    text = stringResource(R.string.dux_dashboard_overview_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // === Manage App Limits entry (Requirement 2 — App Selection & Time Picker) ===
        item {
            ManageAppLimitsEntry(onClick = onNavigateToInterceptionRules)
        }

        // === 2. Efficiency Score Card (wireframe: ring left, text right) ===
        item {
            EfficiencyScoreCard(
                score = content.efficiencyScore,
                previousScore = sectionData.previousDayScore,
                trend = sectionData.efficiencyTrend
            )
        }

        // === 3. "Active Goals" heading + full-width stacked goal cards ===
        if (sectionData.goalProgressSummaries.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.dux_dashboard_active_goals_heading),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(sectionData.goalProgressSummaries, key = { it.goal.id }) { summary ->
                GoalCard(summary = summary)
            }
        }

        // === 4. "Today's Focus" card with todos + quick-add ===
        item {
            TodaysFocusCard(
                todos = sectionData.todayTodos,
                onTodoCheckedChange = onTodoCheckedChange,
                onAddTodo = onAddTodo
            )
        }

        // === Quick-Action Habit Completion (Req 20.4b) ===
        if (sectionData.activeHabitTracks.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.dux_dashboard_active_habits_heading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(sectionData.activeHabitTracks, key = { it.id }) { track ->
                HabitQuickActionItem(
                    track = track,
                    onComplete = { onHabitComplete(track.id) }
                )
            }
        }

        // === Activity Feed (Req 20.6, 20.10) ===
        if (sectionData.activityFeed.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.dux_dashboard_activity_heading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(sectionData.activityFeed, key = { it.id }) { entry ->
                ActivityFeedItem(entry = entry)
            }
            if (sectionData.activityFeedHasMore) {
                item {
                    TextButton(
                        onClick = onLoadMoreActivity,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.dux_dashboard_load_more))
                    }
                }
            }
        }

        // Bottom spacing for nav bar
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

// =====================================================================================
// Manage App Limits entry (Requirement 2 — App Selection & Time Picker)
// Tappable card that navigates to the Rule Authoring screen.
// =====================================================================================

@Composable
private fun ManageAppLimitsEntry(onClick: () -> Unit) {
    val entryDescription = stringResource(R.string.rule_authoring_settings_entry)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = entryDescription },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DopaShiftTheme.spacing.gutter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.stackSm)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.rule_authoring_settings_entry),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.rule_authoring_settings_entry_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// =====================================================================================
// Efficiency Score Card
// Wireframe: surfaceContainerLow card, circular ring (left), "Efficiency" + trend text (right)
// =====================================================================================

@Composable
private fun EfficiencyScoreCard(
    score: Int?,
    previousScore: Int?,
    trend: EfficiencyTrend
) {
    val cardDescription = stringResource(R.string.dux_efficiency_card_content_description)
    val ringLabel = if (score != null) {
        stringResource(R.string.dux_efficiency_percent_label, score)
    } else {
        stringResource(R.string.dux_efficiency_no_score_label)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // DUX-5.8: announce efficiency-value updates to screen readers as a polite live region.
            .politeLiveRegion()
            .semantics { contentDescription = cardDescription },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DopaShiftTheme.spacing.gutter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.gutter)
        ) {
            // Circular progress ring
            CircularProgressRing(
                progress = (score ?: 0) / 100f,
                label = ringLabel,
                size = 56.dp,
                ringColor = DopaShiftTheme.colors.productiveBlue,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeWidth = 6f
            )

            Column {
                Text(
                    text = stringResource(R.string.dux_efficiency_card_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium
                )
                if (previousScore != null && score != null) {
                    val diff = score - previousScore
                    val diffText = if (diff >= 0) {
                        stringResource(R.string.dux_efficiency_diff_positive, diff)
                    } else {
                        stringResource(R.string.dux_efficiency_diff_negative, diff)
                    }
                    Text(
                        text = diffText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = DopaShiftTheme.colors.momentumTeal
                    )
                } else {
                    Text(
                        text = when (trend) {
                            EfficiencyTrend.NOT_ENOUGH_DATA ->
                                stringResource(R.string.dux_efficiency_trend_not_enough_data)
                            EfficiencyTrend.IMPROVING ->
                                stringResource(R.string.dux_efficiency_trend_improving)
                            EfficiencyTrend.DECLINING ->
                                stringResource(R.string.dux_efficiency_trend_declining)
                            EfficiencyTrend.STABLE ->
                                stringResource(R.string.dux_efficiency_trend_stable)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// =====================================================================================
// Goal Card — full width, single column (matches wireframe stacked layout)
// Icon top-left, streak badge top-right, name, description, then ring + progress bar
// =====================================================================================

@Composable
private fun GoalCard(summary: GoalProgressSummary) {
    val gradientColor = if (summary.progressPercent >= 50) {
        DopaShiftTheme.colors.momentumTeal
    } else {
        DopaShiftTheme.colors.productiveBlue
    }

    val goalDescription = stringResource(
        R.string.dux_goal_content_description,
        summary.goal.name,
        summary.progressPercent
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = goalDescription
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box {
            // Subtle gradient overlay
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                gradientColor.copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier.padding(
                    horizontal = DopaShiftTheme.spacing.margin,
                    vertical = DopaShiftTheme.spacing.gutter
                )
            ) {
                // Row 1: category icon (left) + streak badge (right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category icon in colored container
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = summary.goal.category.take(1).uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // Streak badge pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (summary.streakDays > 0) {
                                stringResource(R.string.dux_goal_days_streak, summary.streakDays)
                            } else {
                                stringResource(R.string.dux_goal_in_progress)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))

                // Row 2: Goal name
                Text(
                    text = summary.goal.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.compact))

                // Row 3: Description / category
                Text(
                    text = summary.goal.category,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))

                // Row 4: Circular ring + "X% Complete" + linear progress bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.stackSm)
                ) {
                    CircularProgressRing(
                        progress = summary.progressPercent / 100f,
                        label = stringResource(
                            R.string.dux_goal_progress_fraction,
                            summary.completedItems,
                            summary.totalItems
                        ),
                        size = 48.dp,
                        ringColor = DopaShiftTheme.colors.momentumTeal,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        strokeWidth = 5f
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                R.string.dux_goal_percent_complete_caps,
                                summary.progressPercent
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
                        // Linear progress bar with rounded ends
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(
                                        fraction = (summary.progressPercent / 100f).coerceIn(0f, 1f)
                                    )
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(gradientColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================================
// Today's Focus Card
// Wireframe: surfaceContainer card, "Today's Focus" heading + teal add button,
// task items with square checkboxes, completed = teal check + strikethrough + dimmed,
// quick-add input at bottom
// =====================================================================================

/**
 * Max number of Today's Focus to-dos shown while the card is collapsed. Additional items are
 * revealed in place via the "Show all" toggle (kept on the Overview page — no separate route).
 */
private const val MAX_COLLAPSED_FOCUS_TODOS = 3

@Composable
private fun TodaysFocusCard(
    todos: List<DailyTodoItem>,
    onTodoCheckedChange: (UUID, Boolean) -> Unit,
    onAddTodo: (String) -> Unit
) {
    var quickAddText by remember { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val focusCardDescription = stringResource(R.string.dux_todays_focus_content_description)

    // Cap the collapsed list to the first few tasks; the rest are revealed in place via the
    // "Show all" toggle so the user never leaves the Overview page (no dedicated to-dos route).
    val visibleTodos = if (expanded) todos else todos.take(MAX_COLLAPSED_FOCUS_TODOS)
    val hasMoreTodos = todos.size > MAX_COLLAPSED_FOCUS_TODOS

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = focusCardDescription },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(DopaShiftTheme.spacing.margin)) {
            // Header row: "Today's Focus" + circular add button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dux_todays_focus_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base)
                ) {
                    // "Show all (N)" / "Show less" toggle — only when the list exceeds the cap.
                    if (hasMoreTodos) {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(
                                text = if (expanded) {
                                    stringResource(R.string.dux_todays_focus_show_less)
                                } else {
                                    stringResource(R.string.dux_todays_focus_show_all, todos.size)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = DopaShiftTheme.colors.momentumTeal
                            )
                        }
                    }
                    IconButton(
                        onClick = { /* focus the input */ },
                        modifier = Modifier
                            .size(32.dp)
                            .border(
                                width = 1.5.dp,
                                color = DopaShiftTheme.colors.momentumTeal,
                                shape = CircleShape
                            ),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = DopaShiftTheme.colors.momentumTeal
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.dux_todays_focus_add_task),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))

            // Task items
            if (todos.isEmpty()) {
                Text(
                    text = stringResource(R.string.dux_todays_focus_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = DopaShiftTheme.spacing.stackSm)
                )
            } else {
                visibleTodos.forEach { todo ->
                    TodoItem(
                        todo = todo,
                        onCheckedChange = { isCompleted ->
                            onTodoCheckedChange(todo.id, isCompleted)
                        }
                    )
                    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))
                }
            }

            // Divider + Quick-add input
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = DopaShiftTheme.spacing.stackSm)
            )

            OutlinedTextField(
                value = quickAddText,
                onValueChange = { quickAddText = it },
                placeholder = {
                    Text(
                        stringResource(R.string.dux_todays_focus_add_placeholder),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedBorderColor = DopaShiftTheme.colors.productiveBlue
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (quickAddText.isNotBlank()) {
                            onAddTodo(quickAddText.trim())
                            quickAddText = ""
                            focusManager.clearFocus()
                        }
                    }
                )
            )
        }
    }
}

// =====================================================================================
// Todo Item — matches wireframe exactly:
// - Incomplete: rounded row, square border checkbox (empty), normal text
// - Completed: teal filled square checkbox with checkmark, strikethrough text, dimmed
// =====================================================================================

@Composable
private fun TodoItem(
    todo: DailyTodoItem,
    onCheckedChange: (Boolean) -> Unit
) {
    val containerColor = if (todo.isCompleted) {
        MaterialTheme.colorScheme.surfaceContainerLowest
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // DUX-5.1: task-list rows are at least 56dp tall.
            .minTaskRowHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .border(
                width = 1.dp,
                color = if (todo.isCompleted) {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(12.dp)
            )
            .padding(
                horizontal = DopaShiftTheme.spacing.stackSm,
                vertical = DopaShiftTheme.spacing.stackSm
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.stackSm)
    ) {
        // Custom visual checkbox matching wireframe + clickable via M3 Checkbox
        Checkbox(
            checked = todo.isCompleted,
            onCheckedChange = onCheckedChange
        )

        Text(
            text = todo.text,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else null,
            color = if (todo.isCompleted) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

// =====================================================================================
// Habit Quick-Action Item (Req 20.4b)
// =====================================================================================

@Composable
private fun HabitQuickActionItem(
    track: HabitTrack,
    onComplete: () -> Unit
) {
    val currentCheckpoint = track.checkpoints.find { it.dayNumber == track.currentDay }
    val isPending = currentCheckpoint?.status == com.dopashift.domain.entity.CheckpointStatus.PENDING
    val isCompleted = currentCheckpoint?.status == com.dopashift.domain.entity.CheckpointStatus.COMPLETED

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // DUX-5.1: habit checkpoint rows are at least 56dp tall.
                .minTaskRowHeight()
                .padding(DopaShiftTheme.spacing.stackSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.stackSm)
        ) {
            Checkbox(
                checked = isCompleted,
                onCheckedChange = { if (isPending) onComplete() },
                enabled = isPending
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentCheckpoint?.description
                        ?: stringResource(R.string.dux_habit_day_label, track.currentDay),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.dux_habit_day_of_total, track.currentDay),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Circular ring for habit progress
            CircularProgressRing(
                progress = track.currentDay / 30f,
                label = stringResource(R.string.dux_habit_progress_fraction, track.currentDay),
                size = 36.dp,
                ringColor = DopaShiftTheme.colors.momentumTeal,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeWidth = 4f
            )
        }
    }
}

// =====================================================================================
// Activity Feed Item (Req 20.6)
// =====================================================================================

@Composable
private fun ActivityFeedItem(entry: ActivityFeedEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = DopaShiftTheme.spacing.compact),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = activityActionLabel(entry),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = entry.entityName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = rememberRelativeTime(entry.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// =====================================================================================
// Circular Progress Ring — Canvas-drawn arc matching wireframe SVG rings
// Used for: Efficiency score, goal progress, habit progress
// =====================================================================================

@Composable
private fun CircularProgressRing(
    progress: Float,
    label: String,
    size: androidx.compose.ui.unit.Dp,
    ringColor: Color,
    trackColor: Color,
    strokeWidth: Float
) {
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sweepAngle = progress.coerceIn(0f, 1f) * 360f
            val inset = strokeWidth / 2f
            val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
            val topLeft = Offset(inset, inset)

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
            if (sweepAngle > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (size <= 40.dp) 9.sp else 12.sp
        )
    }
}

// =====================================================================================
// Utilities
// =====================================================================================

/** Resolves the localized activity-feed action label for [entry] (DUX-4.11). */
@Composable
private fun activityActionLabel(entry: ActivityFeedEntry): String = when (entry.actionType) {
    ActivityActionType.TASK_COMPLETED -> stringResource(R.string.dux_activity_task_completed)
    ActivityActionType.TASK_UPDATED -> stringResource(R.string.dux_activity_task_updated)
    ActivityActionType.TASK_DELETED -> stringResource(R.string.dux_activity_task_deleted)
    ActivityActionType.GOAL_TASK_COMPLETED ->
        stringResource(R.string.dux_activity_goal_task_completed)
    ActivityActionType.GOAL_TASK_UPDATED ->
        stringResource(R.string.dux_activity_goal_task_updated)
    ActivityActionType.HABIT_COMPLETED -> stringResource(R.string.dux_activity_habit_completed)
    ActivityActionType.GOAL_DELETED -> stringResource(R.string.dux_activity_goal_deleted)
    ActivityActionType.GOAL_CREATED -> stringResource(R.string.dux_activity_goal_created)
    ActivityActionType.GENERIC ->
        stringResource(R.string.dux_activity_generic, entry.genericEntityType, entry.genericField)
}

@Composable
private fun rememberRelativeTime(timestamp: Instant): String {
    val now = Instant.now()
    val duration = Duration.between(timestamp, now)

    return when {
        duration.toMinutes() < 1 -> stringResource(R.string.dux_time_just_now)
        duration.toMinutes() < 60 ->
            stringResource(R.string.dux_time_minutes_ago, duration.toMinutes().toInt())
        duration.toHours() < 24 ->
            stringResource(R.string.dux_time_hours_ago, duration.toHours().toInt())
        duration.toDays() < 7 ->
            stringResource(R.string.dux_time_days_ago, duration.toDays().toInt())
        else ->
            stringResource(R.string.dux_time_weeks_ago, (duration.toDays() / 7).toInt())
    }
}
