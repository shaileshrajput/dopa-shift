package com.dopashift.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dopashift.ui.R
import com.dopashift.ui.adaptive.TopDestination
import com.dopashift.ui.components.CompletionRing
import com.dopashift.ui.components.DopaShiftBottomNavBar
import com.dopashift.ui.components.KineticCard
import com.dopashift.ui.components.KineticChip
import com.dopashift.ui.components.QuickCreateFab
import com.dopashift.ui.state.GoalCardUi
import com.dopashift.ui.state.GoalsUiState
import com.dopashift.ui.theme.DopaShiftTokens

/**
 * Callback contract for the Goals & Habits overview screen (AUI-4).
 *
 * The screen is a stateless renderer of [GoalsUiState]; every user gesture is dispatched back to
 * the host view-model through this interface, keeping the composable free of any business logic or
 * parallel data path (design → "presentation-only, no parallel data path").
 *
 * @property onGoalClicked invoked with a goal card's opaque id when the card is activated (AUI-4.1).
 * @property onQuickCreate invoked when the persistent Quick-Create FAB is tapped (AUI-4.3).
 * @property onNavigate invoked with the chosen bottom-nav destination (AUI-4.4, AUI-7.6).
 */
interface GoalsActions {
    fun onGoalClicked(id: String)
    fun onQuickCreate()
    fun onNavigate(destination: TopDestination)
}

/**
 * The **Goals & Habits overview** screen (AUI-4).
 *
 * Renders each [GoalCardUi] in [GoalsUiState.goals] as a summary [KineticCard] (AUI-4.1):
 *
 *  1. a mini [CompletionRing] with the integer completion percentage and the goal's accent color;
 *  2. a bold goal title and a category pill badge ([KineticChip] on the elevated surface with a
 *     teal accent);
 *  3. a subtitle counter (`pendingTaskLabel`);
 *  4. keyword chips ([KineticChip] per keyword);
 *  5. a bottom dot-matrix streak row (completed days in Momentum Teal, remaining in a subtle
 *     completed-text gray) plus a roadmap status line (AUI-4.2).
 *
 * A persistent 56dp [QuickCreateFab] is anchored bottom-right (AUI-4.3) and the
 * [DopaShiftBottomNavBar] hosts the screen with [TopDestination.GOALS] active (AUI-4.4).
 *
 * All colors, sizes, and text roles are sourced from [DopaShiftTokens]; all user-facing copy is
 * resolved via [stringResource] (AUI-8.1, AUI-8.6).
 *
 * @param state the rendered goals-overview state.
 * @param on the callback contract for user gestures.
 * @param modifier optional layout modifier for the screen root.
 */
@Composable
fun GoalsHabitsScreen(
    state: GoalsUiState,
    on: GoalsActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DopaShiftTokens.Colors.background,
        floatingActionButton = {
            QuickCreateFab(
                onClick = on::onQuickCreate,
                contentDescription = stringResource(R.string.aui_goals_quick_create_content_description),
            )
        },
        bottomBar = {
            DopaShiftBottomNavBar(
                current = TopDestination.GOALS,
                onNavigate = on::onNavigate,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = DopaShiftTokens.Spacing.marginMobile),
            verticalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space16),
            contentPadding = PaddingValues(vertical = DopaShiftTokens.Spacing.space16),
        ) {
            if (state.goals.isEmpty()) {
                item {
                    KineticCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.aui_goals_empty),
                            style = DopaShiftTokens.TextRoles.bodyMedium,
                            color = DopaShiftTokens.Colors.textSecondary,
                        )
                    }
                }
            } else {
                items(items = state.goals, key = { it.id }) { goal ->
                    GoalSummaryCard(
                        goal = goal,
                        onClick = { on.onGoalClicked(goal.id) },
                    )
                }
            }
        }
    }
}

/**
 * A single goal summary card (AUI-4.1, AUI-4.2). The whole card is clickable and dispatches
 * [onClick]; its interior renders the mini ring, title, category pill, subtitle counter, keyword
 * chips, and the bottom streak dot-matrix + roadmap status row.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalSummaryCard(goal: GoalCardUi, onClick: () -> Unit) {
    KineticCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DopaShiftTokens.Radii.card))
            .clickable(onClick = onClick),
    ) {
        // --- Ring + title / category / subtitle counter ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompletionRing(
                percent = goal.completionPercent,
                contentDescription = stringResource(
                    R.string.aui_goals_ring_content_description,
                    goal.title,
                    goal.completionPercent.coerceIn(0, 100),
                ),
                size = GOAL_RING_SIZE,
                progressColor = goal.accentColor,
            )
            Spacer(modifier = Modifier.width(DopaShiftTokens.Spacing.space16))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space4),
            ) {
                Text(
                    text = goal.title,
                    style = DopaShiftTokens.TextRoles.titleMedium,
                    color = DopaShiftTokens.Colors.textPrimary,
                )
                KineticChip(
                    text = goal.category,
                    accent = DopaShiftTokens.Colors.momentumTeal,
                )
                Text(
                    text = goal.pendingTaskLabel,
                    style = DopaShiftTokens.TextRoles.bodySmall,
                    color = DopaShiftTokens.Colors.textSecondary,
                )
            }
        }

        // --- Keyword chips ---
        if (goal.keywords.isNotEmpty()) {
            Spacer(modifier = Modifier.height(DopaShiftTokens.Spacing.space12))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space8),
                verticalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space8),
            ) {
                goal.keywords.forEach { keyword ->
                    KineticChip(
                        text = keyword,
                        accent = goal.accentColor,
                    )
                }
            }
        }

        // --- Bottom streak dot-matrix + roadmap status line (AUI-4.2) ---
        Spacer(modifier = Modifier.height(DopaShiftTokens.Spacing.space12))
        StreakRoadmapRow(goal = goal)
    }
}

/**
 * The bottom row of a goal card (AUI-4.2): a dot-matrix streak indicator followed by the roadmap
 * status line.
 *
 * The dot-matrix draws [GoalCardUi.streakTotalDays] dots: the first
 * [GoalCardUi.streakCompletedDays] filled in Momentum Teal and the remainder in a subtle
 * completed-text gray. The whole dot matrix is collapsed into one aggregate content description so
 * TalkBack announces "Streak: N of M days completed" rather than reading each decorative dot.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StreakRoadmapRow(goal: GoalCardUi) {
    val completed = goal.streakCompletedDays.coerceIn(0, goal.streakTotalDays.coerceAtLeast(0))
    val total = goal.streakTotalDays.coerceAtLeast(0)
    val streakDescription = stringResource(
        R.string.aui_goals_streak_content_description,
        completed,
        total,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FlowRow(
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics { contentDescription = streakDescription },
            horizontalArrangement = Arrangement.spacedBy(STREAK_DOT_SPACING),
            verticalArrangement = Arrangement.spacedBy(STREAK_DOT_SPACING),
        ) {
            repeat(total) { index ->
                val dotColor = if (index < completed) {
                    DopaShiftTokens.Colors.momentumTeal
                } else {
                    DopaShiftTokens.Colors.textCompleted
                }
                StreakDot(color = dotColor)
            }
        }
        Spacer(modifier = Modifier.width(DopaShiftTokens.Spacing.space12))
        Text(
            text = goal.roadmapStatusLabel,
            style = DopaShiftTokens.TextRoles.labelMono,
            color = DopaShiftTokens.Colors.textSecondary,
        )
    }
}

/** A single streak dot in the dot-matrix; a small filled circle in [color]. */
@Composable
private fun StreakDot(color: Color) {
    Box(
        modifier = Modifier
            .size(STREAK_DOT_SIZE)
            .clip(CircleShape)
            .background(color, CircleShape),
    )
}

// -------------------------------------------------------------------------------------------------
// Component dimensions (not design-system spacing tokens)
// -------------------------------------------------------------------------------------------------

/** The mini goal-card completion ring size (AUI-4.1). */
private val GOAL_RING_SIZE: Dp = 48.dp

/** The diameter of a single streak dot in the dot-matrix (AUI-4.2). */
private val STREAK_DOT_SIZE: Dp = 8.dp

/** The spacing between adjacent streak dots (AUI-4.2). */
private val STREAK_DOT_SPACING: Dp = 4.dp
