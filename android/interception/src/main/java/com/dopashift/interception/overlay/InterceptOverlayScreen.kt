package com.dopashift.interception.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dopashift.domain.entity.CheckpointStatus
import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.entity.HabitCheckpoint
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.ui.theme.DopaShiftTheme
import java.util.UUID

/**
 * Full-screen Intercept Overlay composable.
 *
 * Renders as a TYPE_APPLICATION_OVERLAY window covering the entire screen.
 * Displays pending daily todos, current habit checkpoint, and a video suggestion
 * to redirect the user from a tracked (depleted) app to productive activity.
 *
 * Design: Glassmorphism-inspired dark overlay with blurred background appearance.
 *         Uses DopaShift Kinetic design system tokens.
 *
 * Requirements: 2.4, 2.5, 2.9, 3.1, 3.6, 6.2, 17.7
 *
 * @param viewModel The [OverlayViewModel] managing state and actions.
 * @param onDismiss Callback invoked when the overlay is dismissed.
 */
@Composable
fun InterceptOverlayScreen(
    viewModel: OverlayViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    DopaShiftTheme(darkTheme = true) {
        InterceptOverlayContent(
            state = uiState,
            onTodoChecked = { todoId -> viewModel.completeTodo(todoId) },
            onHabitChecked = { viewModel.completeHabitCheckpoint() },
            onDismiss = {
                if (uiState.canDismiss) {
                    onDismiss()
                }
            }
        )
    }
}

/**
 * Stateless overlay content composable for preview/testing purposes.
 */
@Composable
internal fun InterceptOverlayContent(
    state: OverlayUiState,
    onTodoChecked: (UUID) -> Unit,
    onHabitChecked: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = DopaShiftTheme.colors
    val spacing = DopaShiftTheme.spacing

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                // Glassmorphism-inspired gradient: deep navy overlay at ~95% opacity
                Brush.verticalGradient(
                    colors = listOf(
                        colors.interceptOverlay,
                        colors.interceptOverlay.copy(alpha = 0.98f)
                    )
                )
            )
            .padding(spacing.margin)
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header section
                OverlayHeader(
                    elapsedSeconds = state.elapsedSeconds,
                    canDismiss = state.canDismiss
                )

                Spacer(modifier = Modifier.height(spacing.stackLg))

                // Main content — scrollable
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.section)
                ) {
                    // Section 1: Pending Todos
                    if (state.pendingTodos.isNotEmpty()) {
                        item {
                            PendingTodosSection(
                                todos = state.pendingTodos,
                                onTodoChecked = onTodoChecked
                            )
                        }
                    }

                    // Section 2: Habit Track Checkpoint
                    if (state.currentHabitTrack != null && state.currentCheckpoint != null) {
                        item {
                            HabitCheckpointSection(
                                habitTrack = state.currentHabitTrack,
                                checkpoint = state.currentCheckpoint,
                                onChecked = onHabitChecked
                            )
                        }
                    }

                    // Section 3: Video Suggestion
                    if (state.videoSuggestionUrl != null) {
                        item {
                            VideoSuggestionSection(
                                videoUrl = state.videoSuggestionUrl,
                                videoTitle = state.videoTitle
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(spacing.gutter))

                // Footer: Dismiss button (only enabled after 30s or action)
                DismissButton(
                    canDismiss = state.canDismiss,
                    elapsedSeconds = state.elapsedSeconds,
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@Composable
private fun OverlayHeader(
    elapsedSeconds: Int,
    canDismiss: Boolean
) {
    val spacing = DopaShiftTheme.spacing

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Time to refocus",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(spacing.base))

        Text(
            text = "Your screen-time allowance is up. Complete a task or watch something productive.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!canDismiss) {
            Spacer(modifier = Modifier.height(spacing.stackSm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = DopaShiftTheme.colors.warningAmber
                )
                Spacer(modifier = Modifier.width(spacing.compact))
                Text(
                    text = "${OverlayUiState.MIN_ENGAGEMENT_SECONDS - elapsedSeconds}s until you can dismiss",
                    style = MaterialTheme.typography.labelMedium,
                    color = DopaShiftTheme.colors.warningAmber
                )
            }
        }
    }
}

@Composable
private fun PendingTodosSection(
    todos: List<DailyTodoItem>,
    onTodoChecked: (UUID) -> Unit
) {
    val spacing = DopaShiftTheme.spacing
    val colors = DopaShiftTheme.colors

    GlassCard {
        Column(modifier = Modifier.padding(spacing.gutter)) {
            Text(
                text = "Pending Tasks",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(spacing.stackSm))

            todos.forEach { todo ->
                TodoItemRow(
                    todo = todo,
                    onChecked = { onTodoChecked(todo.id) }
                )
                Spacer(modifier = Modifier.height(spacing.base))
            }
        }
    }
}

@Composable
private fun TodoItemRow(
    todo: DailyTodoItem,
    onChecked: () -> Unit
) {
    val spacing = DopaShiftTheme.spacing
    val isCompleted = todo.isCompleted

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isCompleted,
            onCheckedChange = { if (!isCompleted) onChecked() },
            colors = CheckboxDefaults.colors(
                checkedColor = DopaShiftTheme.colors.momentumTeal,
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkmarkColor = Color.White
            )
        )

        Spacer(modifier = Modifier.width(spacing.base))

        Text(
            text = todo.text,
            style = MaterialTheme.typography.bodyMedium.let {
                if (isCompleted) it.copy(textDecoration = TextDecoration.LineThrough) else it
            },
            color = if (isCompleted) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HabitCheckpointSection(
    habitTrack: HabitTrack,
    checkpoint: HabitCheckpoint,
    onChecked: () -> Unit
) {
    val spacing = DopaShiftTheme.spacing
    val colors = DopaShiftTheme.colors
    val isChecked = checkpoint.status == CheckpointStatus.COMPLETED

    GlassCard {
        Column(modifier = Modifier.padding(spacing.gutter)) {
            Text(
                text = "Today's Micro-Habit (Day ${habitTrack.currentDay}/30)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(spacing.stackSm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { if (!isChecked) onChecked() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = colors.focusIndigo,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkmarkColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.width(spacing.base))

                Text(
                    text = checkpoint.description,
                    style = MaterialTheme.typography.bodyMedium.let {
                        if (isChecked) it.copy(textDecoration = TextDecoration.LineThrough) else it
                    },
                    color = if (isChecked) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            if (isChecked) {
                Spacer(modifier = Modifier.height(spacing.base))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.successGreen
                    )
                    Spacer(modifier = Modifier.width(spacing.compact))
                    Text(
                        text = "Great work! Habit checked off.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.successGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoSuggestionSection(
    videoUrl: String,
    videoTitle: String?
) {
    val spacing = DopaShiftTheme.spacing
    val colors = DopaShiftTheme.colors

    GlassCard {
        Column(modifier = Modifier.padding(spacing.gutter)) {
            Text(
                text = "Suggested Learning",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(spacing.stackSm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play video",
                    modifier = Modifier.size(24.dp),
                    tint = colors.productiveBlue
                )

                Spacer(modifier = Modifier.width(spacing.base))

                Text(
                    text = videoTitle ?: "Watch a recommended video",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.productiveBlue,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // TODO: Embed WebView or ExoPlayer for in-overlay playback (Requirement 3.6)
            // For now, display the URL as a clickable target.
            // The actual video player integration will need platform-specific handling.
        }
    }
}

@Composable
private fun DismissButton(
    canDismiss: Boolean,
    elapsedSeconds: Int,
    onDismiss: () -> Unit
) {
    val colors = DopaShiftTheme.colors

    AnimatedVisibility(
        visible = canDismiss,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Text(
                text = "Continue to app",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }

    if (!canDismiss) {
        Button(
            onClick = { /* Disabled — no-op */ },
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Wait ${OverlayUiState.MIN_ENGAGEMENT_SECONDS - elapsedSeconds}s or complete a task",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/**
 * Glassmorphism-styled card container.
 * Semi-transparent surface with rounded corners, simulating frosted glass.
 */
@Composable
private fun GlassCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.06f)
        )
    ) {
        content()
    }
}
