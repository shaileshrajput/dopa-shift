package com.dopashift.ui.dashboard

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
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.ui.theme.DopaShiftTheme
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Dashboard screen implementing Requirements 20.1–20.15.
 *
 * Displays:
 * - Today's Status card (pending todos, habit status, goal progress, efficiency score)
 * - Efficiency score card with trend indicator (improving/declining/stable)
 * - Goal cards grid with progress rings
 * - Quick-action todo checkboxes (optimistic UI within 200ms)
 * - Quick-action habit completion
 * - Paginated activity feed (20 items per page)
 *
 * Reactive data propagation: Room -> Repository (Flow) -> ViewModel (combine) -> UI (collectAsStateWithLifecycle).
 * No polling required; Room's invalidation tracker triggers re-emission.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DashboardContent(
        uiState = uiState,
        onTodoCheckedChange = { todoId, isCompleted ->
            if (isCompleted) viewModel.markTodoComplete(todoId)
            else viewModel.markTodoIncomplete(todoId)
        },
        onHabitComplete = { trackId -> viewModel.completeHabitCheckpoint(trackId) },
        onLoadMoreActivity = { viewModel.loadMoreActivityFeed() }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardContent(
    uiState: DashboardUiState,
    onTodoCheckedChange: (UUID, Boolean) -> Unit,
    onHabitComplete: (UUID) -> Unit,
    onLoadMoreActivity: () -> Unit
) {
    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // Req 20.11: Empty state when no goals, todos, or habits
    if (uiState.isEmpty) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(DopaShiftTheme.spacing.margin),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Welcome to DopaShift",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
                Text(
                    text = "Create a goal to get started",
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
            .padding(horizontal = DopaShiftTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.gutter)
    ) {
        // Spacing at top
        item { Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base)) }

        // === Today's Status Card (Req 20.2) ===
        item {
            TodaysStatusCard(uiState = uiState)
        }

        // === Efficiency Score Card with Trend (Req 20.2d, 20.3) ===
        item {
            EfficiencyScoreCard(
                score = uiState.todayScore,
                previousScore = uiState.previousDayScore,
                trend = uiState.efficiencyTrend
            )
        }

        // === Goal Cards Grid (Req 20.2c) ===
        if (uiState.goalProgressSummaries.isNotEmpty()) {
            item {
                SectionHeader(title = "Goals", count = uiState.goalProgressSummaries.size)
            }
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base),
                    verticalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base),
                    maxItemsInEachRow = 2
                ) {
                    uiState.goalProgressSummaries.forEach { summary ->
                        GoalProgressCard(
                            summary = summary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Padding item if odd number
                    if (uiState.goalProgressSummaries.size % 2 != 0) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // === Quick-Action Todo Checkboxes (Req 20.4a, 20.5) ===
        if (uiState.todayTodos.isNotEmpty()) {
            item {
                SectionHeader(title = "Today's Tasks", count = uiState.todayTodos.size)
            }
            items(uiState.todayTodos, key = { it.id }) { todo ->
                TodoQuickActionItem(
                    todo = todo,
                    onCheckedChange = { isCompleted -> onTodoCheckedChange(todo.id, isCompleted) }
                )
            }
        }

        // === Quick-Action Habit Completion (Req 20.4b) ===
        if (uiState.activeHabitTracks.isNotEmpty()) {
            item {
                SectionHeader(title = "Active Habits", count = uiState.activeHabitTracks.size)
            }
            items(uiState.activeHabitTracks, key = { it.id }) { track ->
                HabitQuickActionItem(
                    track = track,
                    onComplete = { onHabitComplete(track.id) }
                )
            }
        }

        // === Activity Feed (Req 20.6, 20.10) ===
        if (uiState.activityFeed.isNotEmpty()) {
            item {
                SectionHeader(title = "Activity", count = uiState.activityFeed.size)
            }
            items(uiState.activityFeed, key = { it.id }) { entry ->
                ActivityFeedItem(entry = entry)
            }
            // Load More button for pagination (Req 20.10)
            if (uiState.activityFeedHasMore) {
                item {
                    TextButton(
                        onClick = onLoadMoreActivity,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Load more")
                    }
                }
            }
        }

        // Bottom spacing
        item { Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.section)) }
    }
}

// =====================================================================================
// Today's Status Card (Req 20.2: pending todos, active habits status, goal progress)
// =====================================================================================

@Composable
private fun TodaysStatusCard(uiState: DashboardUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Today's status summary" },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(DopaShiftTheme.spacing.gutter)) {
            Text(
                text = "Today's Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))

            // Req 20.2a: Pending todos count
            StatusRow(
                label = "Pending Tasks",
                value = "${uiState.pendingTodoCount} of ${uiState.todayTodos.size}"
            )
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.compact))

            // Req 20.2b: Habit tracks status
            StatusRow(
                label = "Habits",
                value = "${uiState.completedHabitCount} done, ${uiState.pendingHabitCount} pending" +
                    if (uiState.missedHabitCount > 0) ", ${uiState.missedHabitCount} missed" else ""
            )
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.compact))

            // Req 20.2c: Goal progress (count of active goals)
            StatusRow(
                label = "Active Goals",
                value = "${uiState.goals.size}"
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

// =====================================================================================
// Efficiency Score Card (Req 20.2d, 20.3)
// =====================================================================================

@Composable
private fun EfficiencyScoreCard(
    score: Int?,
    previousScore: Int?,
    trend: EfficiencyTrend
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Efficiency score card" },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DopaShiftTheme.spacing.gutter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Efficiency Score",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.compact))
                Text(
                    text = if (score != null) "$score%" else "No data",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (previousScore != null && score != null) {
                    val diff = score - previousScore
                    val diffText = if (diff >= 0) "+$diff" else "$diff"
                    Text(
                        text = "$diffText from yesterday",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            // Trend indicator (Req 20.3)
            TrendIndicator(trend = trend)
        }
    }
}

@Composable
private fun TrendIndicator(trend: EfficiencyTrend) {
    val (icon, label, color) = when (trend) {
        EfficiencyTrend.IMPROVING -> Triple(
            Icons.AutoMirrored.Filled.TrendingUp,
            "Improving",
            DopaShiftTheme.colors.successGreen
        )
        EfficiencyTrend.DECLINING -> Triple(
            Icons.AutoMirrored.Filled.TrendingDown,
            "Declining",
            DopaShiftTheme.colors.errorRose
        )
        EfficiencyTrend.STABLE -> Triple(
            Icons.AutoMirrored.Filled.TrendingFlat,
            "Stable",
            DopaShiftTheme.colors.warningAmber
        )
        EfficiencyTrend.NOT_ENOUGH_DATA -> Triple(
            Icons.Default.Remove,
            "Not enough data",
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics { contentDescription = "Trend: $label" }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.compact))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

// =====================================================================================
// Goal Cards Grid (Req 20.2c)
// =====================================================================================

@Composable
private fun GoalProgressCard(
    summary: GoalProgressSummary,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.semantics {
            contentDescription = "${summary.goal.name}: ${summary.progressPercent}% complete"
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(DopaShiftTheme.spacing.stackSm)
        ) {
            Text(
                text = summary.goal.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.compact))
            Text(
                text = summary.goal.category,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

            // Progress indicator
            LinearProgressIndicator(
                progress = { summary.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.compact))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${summary.completedItems}/${summary.totalItems} tasks",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${summary.progressPercent}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// =====================================================================================
// Todo Quick-Action Items (Req 20.4a, 20.5 — optimistic UI within 200ms)
// =====================================================================================

@Composable
private fun TodoQuickActionItem(
    todo: DailyTodoItem,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = DopaShiftTheme.spacing.compact,
                    end = DopaShiftTheme.spacing.stackSm,
                    top = DopaShiftTheme.spacing.compact,
                    bottom = DopaShiftTheme.spacing.compact
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox for quick-action completion (Req 20.4a)
            Checkbox(
                checked = todo.isCompleted,
                onCheckedChange = onCheckedChange
            )
            Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.base))
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
            if (todo.isCompleted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Completed",
                    tint = DopaShiftTheme.colors.successGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// =====================================================================================
// Habit Quick-Action Items (Req 20.4b)
// =====================================================================================

@Composable
private fun HabitQuickActionItem(
    track: HabitTrack,
    onComplete: () -> Unit
) {
    val currentCheckpoint = track.checkpoints.find { it.dayNumber == track.currentDay }
    val isPending = currentCheckpoint?.status == com.dopashift.domain.entity.CheckpointStatus.PENDING

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = DopaShiftTheme.spacing.compact,
                    end = DopaShiftTheme.spacing.stackSm,
                    top = DopaShiftTheme.spacing.compact,
                    bottom = DopaShiftTheme.spacing.compact
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = !isPending,
                onCheckedChange = { if (isPending) onComplete() },
                enabled = isPending
            )
            Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.base))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentCheckpoint?.description ?: "Day ${track.currentDay}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Day ${track.currentDay}/30",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Progress ring for the habit track
            CircularProgressIndicator(
                progress = { track.currentDay / 30f },
                modifier = Modifier.size(32.dp),
                color = DopaShiftTheme.colors.momentumTeal,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeWidth = 3.dp,
                strokeCap = StrokeCap.Round,
            )
        }
    }
}

// =====================================================================================
// Activity Feed (Req 20.6, 20.10 — paginated 20 items per page)
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
                text = entry.actionType,
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
            text = formatRelativeTime(entry.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// =====================================================================================
// Section Header
// =====================================================================================

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// =====================================================================================
// Utilities
// =====================================================================================

/**
 * Formats an [Instant] as a relative timestamp string (Req 20.6: relative timestamp).
 */
private fun formatRelativeTime(timestamp: Instant): String {
    val now = Instant.now()
    val duration = Duration.between(timestamp, now)

    return when {
        duration.toMinutes() < 1 -> "just now"
        duration.toMinutes() < 60 -> "${duration.toMinutes()}m ago"
        duration.toHours() < 24 -> "${duration.toHours()}h ago"
        duration.toDays() < 7 -> "${duration.toDays()}d ago"
        else -> "${duration.toDays() / 7}w ago"
    }
}
