package com.dopashift.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dopashift.app.R
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.ui.adaptive.TopDestination
import com.dopashift.ui.goals.GoalsViewModel
import com.dopashift.ui.screens.GoalsActions
import com.dopashift.ui.screens.GoalsHabitsScreen
import com.dopashift.ui.state.GoalCardUi
import com.dopashift.ui.state.GoalsUiState

/**
 * Presentation bridge hosting the Kinetic [GoalsHabitsScreen] (AUI-4) on the [KineticRoutes.GOALS]
 * route (spec `android-ui-upgrade`, task 11.1).
 *
 * It observes the existing [GoalsViewModel] and maps its goal list + per-goal progress into the
 * presentation-only [GoalsUiState] the Kinetic screen renders. Tapping a card pushes Goal Detail
 * (AUI-4.1 → AUI-5); the FAB opens the Quick-Create surface (AUI-4.3); the bottom nav navigates via
 * [onNavigate] with Goals active (AUI-4.4). No parallel data path is introduced.
 *
 * @param onNavigate invoked with the chosen bottom-nav [TopDestination] (AUI-4.4).
 * @param onOpenQuickCreate opens the Quick-Create surface (AUI-4.3).
 * @param onOpenGoalDetail opens Goal Detail for a tapped goal card (AUI-4.1 → AUI-5).
 * @param viewModel the existing goals view-model (Hilt-provided).
 */
@Composable
fun KineticGoalsHost(
    onNavigate: (TopDestination) -> Unit,
    onOpenQuickCreate: () -> Unit,
    onOpenGoalDetail: (String) -> Unit,
    viewModel: GoalsViewModel = hiltViewModel(),
) {
    val vmState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val state = GoalsUiState(
        goals = vmState.goals.map { goal ->
            val progress = vmState.goalProgressMap[goal.id]
            val completed = progress?.completedItems ?: 0
            val total = progress?.totalItems ?: 0
            val streak = progress?.activeHabitTracks?.let { if (it > 0) 1 else 0 } ?: 0
            GoalCardUi(
                id = goal.id.toString(),
                title = goal.name,
                category = goal.category,
                completionPercent = ((progress?.fraction ?: 0f) * 100).toInt().coerceIn(0, 100),
                pendingTaskLabel = context.getString(
                    R.string.kinetic_pending_tasks_count,
                    (total - completed).coerceAtLeast(0),
                    total,
                ),
                keywords = goal.keywords,
                accentColor = accentFor(goal.category),
                streakCompletedDays = streak.coerceIn(0, ROADMAP_TOTAL_DAYS),
                streakTotalDays = ROADMAP_TOTAL_DAYS,
                roadmapStatusLabel = context.getString(R.string.kinetic_roadmap_status, streak),
            )
        },
    )

    val actions = object : GoalsActions {
        override fun onGoalClicked(id: String) = onOpenGoalDetail(id)
        override fun onQuickCreate() = onOpenQuickCreate()
        override fun onNavigate(destination: TopDestination) = onNavigate(destination)
    }

    GoalsHabitsScreen(state = state, on = actions)
}

/** Convenience accessor kept for symmetry with other hosts; the goal list is on the ui-state. */
internal fun GoalsUiState.goalIds(): List<String> = goals.map(GoalCardUi::id)

/** Locates a domain goal by its string id within the view-model goal list. */
internal fun List<GoalProfile>.findByStringId(id: String): GoalProfile? =
    firstOrNull { it.id.toString() == id }
