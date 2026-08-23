package com.dopashift.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dopashift.domain.entity.CheckpointStatus
import com.dopashift.domain.entity.HabitCheckpoint
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.ui.theme.DopaShiftTheme
import java.util.UUID

/**
 * Habit Roadmap screen implementing Requirements 6.1–6.6.
 *
 * Displays:
 * - All active habit tracks for the user
 * - 30-day visualization grid showing status per day (PENDING/COMPLETED/MISSED)
 * - Current day indicator
 * - Mark current day's checkpoint as complete (button)
 * - Earliest-start-date track shown prominently at the top (Req 6.5)
 *
 * Reactive data propagation: Room -> Repository (Flow) -> ViewModel (combine) -> UI.
 * No polling; Room's invalidation tracker triggers re-emission within 2 seconds (Req 14.4).
 */
@Composable
fun HabitRoadmapScreen(
    viewModel: HabitRoadmapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HabitRoadmapContent(
        uiState = uiState,
        onCompleteCheckpoint = { trackId -> viewModel.completeCheckpoint(trackId) },
        getTodayDayNumber = { track -> viewModel.getTodayDayNumber(track) }
    )
}

@Composable
private fun HabitRoadmapContent(
    uiState: HabitRoadmapUiState,
    onCompleteCheckpoint: (UUID) -> Unit,
    getTodayDayNumber: (HabitTrack) -> Int?
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

    if (uiState.isEmpty) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(DopaShiftTheme.spacing.margin),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No Active Habit Tracks",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
                Text(
                    text = "Activate a habit track from a goal to start your 30-day journey",
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
        item { Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base)) }

        // Screen header
        item {
            Text(
                text = "Habit Roadmap",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Legend for status colors
        item {
            StatusLegend()
        }

        // Req 6.5: Prominent track (earliest start date) displayed first
        uiState.prominentTrack?.let { prominentTrackWithGoal ->
            item {
                ProminentHabitTrackCard(
                    trackWithGoal = prominentTrackWithGoal,
                    todayDayNumber = getTodayDayNumber(prominentTrackWithGoal.track),
                    onCompleteCheckpoint = onCompleteCheckpoint
                )
            }
        }

        // Remaining tracks (excluding the prominent one already shown)
        val remainingTracks = uiState.habitTracksWithGoals.drop(1)
        if (remainingTracks.isNotEmpty()) {
            item {
                Text(
                    text = "Other Active Tracks",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = DopaShiftTheme.spacing.base)
                )
            }
            items(remainingTracks, key = { it.track.id }) { trackWithGoal ->
                HabitTrackCard(
                    trackWithGoal = trackWithGoal,
                    todayDayNumber = getTodayDayNumber(trackWithGoal.track),
                    onCompleteCheckpoint = onCompleteCheckpoint
                )
            }
        }

        item { Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.section)) }
    }
}

// =====================================================================================
// Prominent Habit Track Card (Req 6.5 — earliest start date shown prominently)
// =====================================================================================

@Composable
private fun ProminentHabitTrackCard(
    trackWithGoal: HabitTrackWithGoal,
    todayDayNumber: Int?,
    onCompleteCheckpoint: (UUID) -> Unit
) {
    val track = trackWithGoal.track
    val currentCheckpoint = track.checkpoints.find { it.dayNumber == track.currentDay }
    val canComplete = currentCheckpoint?.status == CheckpointStatus.PENDING

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Primary habit track: ${trackWithGoal.goalName}, day ${track.currentDay} of 30"
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(DopaShiftTheme.spacing.gutter)
        ) {
            // Header with star icon indicating this is the primary track
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Primary track",
                            tint = DopaShiftTheme.colors.warningAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.compact))
                        Text(
                            text = "Primary Track",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.compact))
                    Text(
                        text = trackWithGoal.goalName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Progress ring
                CircularProgressIndicator(
                    progress = { track.currentDay / 30f },
                    modifier = Modifier.size(48.dp),
                    color = DopaShiftTheme.colors.momentumTeal,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                    strokeWidth = 4.dp,
                    strokeCap = StrokeCap.Round,
                )
            }

            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))

            // Current day info
            Text(
                text = "Day ${track.currentDay} of 30",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            // Current checkpoint description
            if (currentCheckpoint != null) {
                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.compact))
                Text(
                    text = currentCheckpoint.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))

            // 30-day grid visualization
            ThirtyDayGrid(
                checkpoints = track.checkpoints,
                currentDay = track.currentDay,
                todayDayNumber = todayDayNumber,
                isProminent = true
            )

            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))

            // Complete checkpoint button (Req 6.3)
            if (canComplete) {
                Button(
                    onClick = { onCompleteCheckpoint(track.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DopaShiftTheme.colors.momentumTeal
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.base))
                    Text(
                        text = "Complete Today's Checkpoint",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// =====================================================================================
// Standard Habit Track Card
// =====================================================================================

@Composable
private fun HabitTrackCard(
    trackWithGoal: HabitTrackWithGoal,
    todayDayNumber: Int?,
    onCompleteCheckpoint: (UUID) -> Unit
) {
    val track = trackWithGoal.track
    val currentCheckpoint = track.checkpoints.find { it.dayNumber == track.currentDay }
    val canComplete = currentCheckpoint?.status == CheckpointStatus.PENDING

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Habit track: ${trackWithGoal.goalName}, day ${track.currentDay} of 30"
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(DopaShiftTheme.spacing.gutter)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trackWithGoal.goalName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Day ${track.currentDay} of 30",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                CircularProgressIndicator(
                    progress = { track.currentDay / 30f },
                    modifier = Modifier.size(36.dp),
                    color = DopaShiftTheme.colors.focusIndigo,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = 3.dp,
                    strokeCap = StrokeCap.Round,
                )
            }

            // Current checkpoint description
            if (currentCheckpoint != null) {
                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
                Text(
                    text = currentCheckpoint.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))

            // 30-day grid visualization
            ThirtyDayGrid(
                checkpoints = track.checkpoints,
                currentDay = track.currentDay,
                todayDayNumber = todayDayNumber,
                isProminent = false
            )

            // Complete checkpoint button (Req 6.3)
            if (canComplete) {
                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))
                Button(
                    onClick = { onCompleteCheckpoint(track.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DopaShiftTheme.colors.momentumTeal
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.base))
                    Text(
                        text = "Complete Checkpoint",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// =====================================================================================
// 30-Day Grid Visualization (Req 6.1, 6.4)
// =====================================================================================

/**
 * Displays a 30-day grid (6 rows x 5 columns) showing checkpoint status.
 *
 * Colors:
 * - COMPLETED: Momentum Teal (success/green)
 * - MISSED: Error Rose (red)
 * - PENDING: Surface variant (neutral/gray)
 * - Current day: Outlined with primary color border
 * - Today: Additional indicator ring
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThirtyDayGrid(
    checkpoints: List<HabitCheckpoint>,
    currentDay: Int,
    todayDayNumber: Int?,
    isProminent: Boolean
) {
    val checkpointMap = checkpoints.associateBy { it.dayNumber }
    val cellSize = if (isProminent) 36.dp else 28.dp

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "30-day habit progress grid" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        maxItemsInEachRow = 10
    ) {
        for (day in 1..30) {
            val checkpoint = checkpointMap[day]
            val status = checkpoint?.status ?: CheckpointStatus.PENDING
            val isCurrentDay = day == currentDay
            val isToday = day == todayDayNumber

            DayCell(
                day = day,
                status = status,
                isCurrentDay = isCurrentDay,
                isToday = isToday,
                size = cellSize
            )
        }
    }
}

/**
 * A single day cell in the 30-day grid.
 *
 * Visual encoding:
 * - Background color reflects status (COMPLETED=teal, MISSED=rose, PENDING=gray)
 * - Current day has a primary-colored border
 * - Today has an additional highlight ring
 */
@Composable
private fun DayCell(
    day: Int,
    status: CheckpointStatus,
    isCurrentDay: Boolean,
    isToday: Boolean,
    size: androidx.compose.ui.unit.Dp
) {
    val backgroundColor = when (status) {
        CheckpointStatus.COMPLETED -> DopaShiftTheme.colors.momentumTeal
        CheckpointStatus.MISSED -> DopaShiftTheme.colors.errorRose
        CheckpointStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = when (status) {
        CheckpointStatus.COMPLETED -> Color.White
        CheckpointStatus.MISSED -> Color.White
        CheckpointStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val borderModifier = when {
        isToday -> Modifier.border(
            width = 2.dp,
            color = DopaShiftTheme.colors.productiveBlue,
            shape = RoundedCornerShape(6.dp)
        )
        isCurrentDay -> Modifier.border(
            width = 1.5.dp,
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(6.dp)
        )
        else -> Modifier
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .then(borderModifier)
            .background(backgroundColor, RoundedCornerShape(6.dp))
            .semantics {
                contentDescription = "Day $day: ${status.name.lowercase()}${if (isToday) ", today" else ""}${if (isCurrentDay) ", current" else ""}"
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isCurrentDay || isToday) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
    }
}

// =====================================================================================
// Status Legend
// =====================================================================================

@Composable
private fun StatusLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.gutter),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(
            color = DopaShiftTheme.colors.momentumTeal,
            label = "Completed"
        )
        LegendItem(
            color = DopaShiftTheme.colors.errorRose,
            label = "Missed"
        )
        LegendItem(
            color = MaterialTheme.colorScheme.surfaceVariant,
            label = "Pending"
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.compact)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
