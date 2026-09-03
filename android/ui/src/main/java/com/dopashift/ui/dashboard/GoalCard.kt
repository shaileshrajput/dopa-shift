package com.dopashift.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dopashift.domain.presentation.MotionPolicy
import com.dopashift.ui.R
import com.dopashift.ui.theme.DopaShiftTheme
import com.dopashift.ui.theme.MotionTokens
import com.dopashift.ui.theme.rememberReducedMotion

/**
 * A single active-goal card for the Dashboard "Active Goals" section (DUX-3 AC7 / parent
 * Requirement 20, AC13).
 *
 * Renders one active [GoalProfile][com.dopashift.domain.entity.GoalProfile] (via its
 * [GoalProgressSummary]) as a card exposing:
 * - the goal **name**,
 * - a **category icon** (the category initial in a tonal container, standing in for a
 *   per-category glyph so state is never conveyed by an image alone),
 * - the current **streak count**,
 * - a **progress ring with its numeric percentage** (completed vs. total checklist items),
 * - a brief **description** (the goal category — `GoalProfile` carries no separate blurb).
 *
 * Styling references theme tokens only — no raw color or dimension literals in the layout:
 * the card uses the `rounded.lg` (16dp) card shape ([DopaShiftTheme.shapes] `large`, i.e.
 * `Shapes.medium`) per DUX-4 AC4, the progress ring uses the momentum-teal progress accent
 * ([MaterialTheme.colorScheme.secondary]) with rounded stroke caps, and spacing uses the 8dp
 * rhythm tokens from [DopaShiftTheme.spacing].
 *
 * Motion: the ring animates from 0 to its value once, using [MotionTokens.Emphasized] resolved
 * through [MotionPolicy.effective] + [rememberReducedMotion], so under the OS reduced-motion
 * setting it collapses to at most `motion-instant` while still showing the final value
 * (DUX-2 AC5, DUX-2 AC9).
 *
 * Accessibility: the whole card exposes a single content description summarizing the goal name,
 * completion progress, and streak; the decorative child nodes are hidden from the a11y tree.
 * Completion state carries a non-color indicator (a check icon / explicit text) so it is not
 * conveyed by color alone (DUX-5 AC7).
 *
 * @param summary the active goal's progress summary (goal, completed/total items, streak).
 * @param modifier layout modifier applied to the card.
 * @param onClick optional click handler; when non-null the card becomes clickable.
 */
@Composable
fun GoalCard(
    summary: GoalProgressSummary,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val goalName = summary.goal.name
    val category = summary.goal.category
    val percent = summary.progressPercent
    val isComplete = summary.totalItems > 0 && summary.completedItems >= summary.totalItems

    val streakText = if (summary.streakDays > 0) {
        stringResource(R.string.dux_goal_streak_days, summary.streakDays)
    } else {
        stringResource(R.string.dux_goal_no_streak)
    }

    // Aggregate content description. Accessibility semantics wiring is task 16.1; here we only
    // ensure the text is localized by reusing the shared "name: N% complete" resource plus the
    // localized streak text.
    val description = stringResource(
        R.string.dux_goal_content_description,
        goalName,
        percent,
    ) + ", " + streakText + if (isComplete) {
        ", " + stringResource(R.string.dux_goal_complete)
    } else {
        ""
    }

    val cardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )
    val cardModifier = modifier
        .fillMaxWidth()
        .clearAndSetSemantics { contentDescription = description }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            shape = RoundedCornerShape(DopaShiftTheme.shapes.large),
            colors = cardColors,
        ) {
            GoalCardContent(
                goalName = goalName,
                category = category,
                percent = percent,
                completedItems = summary.completedItems,
                totalItems = summary.totalItems,
                streakDays = summary.streakDays,
                streakText = streakText,
                isComplete = isComplete,
            )
        }
    } else {
        Card(
            modifier = cardModifier,
            shape = RoundedCornerShape(DopaShiftTheme.shapes.large),
            colors = cardColors,
        ) {
            GoalCardContent(
                goalName = goalName,
                category = category,
                percent = percent,
                completedItems = summary.completedItems,
                totalItems = summary.totalItems,
                streakDays = summary.streakDays,
                streakText = streakText,
                isComplete = isComplete,
            )
        }
    }
}

@Composable
private fun GoalCardContent(
    goalName: String,
    category: String,
    percent: Int,
    completedItems: Int,
    totalItems: Int,
    streakDays: Int,
    streakText: String,
    isComplete: Boolean,
) {
    Column(
        modifier = Modifier.padding(
            horizontal = DopaShiftTheme.spacing.gutter,
            vertical = DopaShiftTheme.spacing.gutter,
        ),
    ) {
        // Row 1: category icon (left) + streak chip (right).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryBadge(category = category)
            StreakChip(streakDays = streakDays, streakText = streakText)
        }

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))

        // Row 2: goal name.
        Text(
            text = goalName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.compact))

        // Row 3: brief description (category — GoalProfile has no separate blurb field).
        Text(
            text = category,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))

        // Row 4: progress ring + numeric percentage + completion state indicator.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.stackSm),
        ) {
            GoalProgressRing(percent = percent)

            Column {
                Text(
                    text = stringResource(R.string.dux_goal_percent_complete, percent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.compact))
                // Non-color completion indicator: explicit icon + text, not color alone.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.compact),
                ) {
                    if (isComplete) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(GoalCardDimens.StateIcon),
                        )
                    }
                    Text(
                        text = if (isComplete) {
                            stringResource(R.string.dux_goal_complete)
                        } else {
                            pluralStringResource(
                                R.plurals.dux_goal_items_of,
                                totalItems,
                                completedItems,
                                totalItems,
                            )
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Category glyph: the category initial in a tonal primary container (stands in per category). */
@Composable
private fun CategoryBadge(category: String) {
    Box(
        modifier = Modifier
            .size(GoalCardDimens.CategoryBadge)
            .clip(RoundedCornerShape(DopaShiftTheme.shapes.medium))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = category.take(1).uppercase(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** Streak pill: fire icon + count, always paired with text so it is not color-only. */
@Composable
private fun StreakChip(streakDays: Int, streakText: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(DopaShiftTheme.shapes.chipPill))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(
                horizontal = DopaShiftTheme.spacing.stackSm,
                vertical = DopaShiftTheme.spacing.compact,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.compact),
    ) {
        if (streakDays > 0) {
            Icon(
                imageVector = Icons.Filled.LocalFireDepartment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(GoalCardDimens.StateIcon),
            )
        }
        Text(
            text = streakText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Circular progress ring for a goal card: fills to [percent]% using the momentum-teal progress
 * accent with rounded stroke caps (DUX-4 AC4), animating 0→value once via `motion-emphasized`
 * (collapsed under reduced motion).
 */
@Composable
private fun GoalProgressRing(percent: Int) {
    val targetFraction = percent.coerceIn(0, 100) / 100f

    val reducedMotion = rememberReducedMotion()
    val motionSpec = MotionPolicy.effective(MotionTokens.Emphasized, reducedMotion)

    val sweep = remember { Animatable(0f) }
    LaunchedEffect(targetFraction) {
        sweep.snapTo(0f)
        sweep.animateTo(
            targetValue = targetFraction,
            animationSpec = tween(
                durationMillis = motionSpec.durationMillis,
                easing = MotionTokens.easingFor(motionSpec.easingName),
            ),
        )
    }

    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val progressColor = MaterialTheme.colorScheme.secondary
    val labelColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier.size(GoalCardDimens.RingDiameter),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(GoalCardDimens.RingDiameter)) {
            val strokePx = GoalCardDimens.RingStroke.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = trackColor,
                startAngle = RING_START_ANGLE,
                sweepAngle = RING_FULL_SWEEP,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            drawArc(
                color = progressColor,
                startAngle = RING_START_ANGLE,
                sweepAngle = RING_FULL_SWEEP * sweep.value,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }

        Text(
            text = stringResource(R.string.dux_efficiency_percent_label, percent),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
        )
    }
}

/** Component dimensions for the goal card (not design-system spacing tokens). */
private object GoalCardDimens {
    val CategoryBadge: Dp = 40.dp
    val RingDiameter: Dp = 56.dp
    val RingStroke: Dp = 6.dp
    val StateIcon: Dp = 16.dp
}

// Ring sweep starts at 12 o'clock and runs clockwise a full turn.
private const val RING_START_ANGLE = -90f
private const val RING_FULL_SWEEP = 360f
