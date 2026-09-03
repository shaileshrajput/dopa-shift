package com.dopashift.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dopashift.app.R
import com.dopashift.domain.entity.GoalChecklistItem
import com.dopashift.ui.goals.GoalsViewModel
import com.dopashift.ui.screens.GoalDetailActions
import com.dopashift.ui.screens.GoalDetailScreen
import com.dopashift.ui.state.GoalDetailUiState
import com.dopashift.ui.state.TodoRowUi
import java.util.UUID

/**
 * Presentation bridge hosting the Kinetic [GoalDetailScreen] (AUI-5) as a pushed destination on the
 * [KineticRoutes.GOAL_DETAIL] route (spec `android-ui-upgrade`, task 11.1).
 *
 * It selects [goalId] on the shared [GoalsViewModel], then maps the selected goal, its checklist,
 * and its active habit track into the presentation-only [GoalDetailUiState] the Kinetic screen
 * renders. Checklist toggles / adds / deletes and the goal delete are dispatched back to the
 * view-model; the app-bar back arrow pops the back stack (AUI-5.1). No parallel data path is
 * introduced. While the selected goal has not resolved yet, a lightweight loading placeholder is
 * shown rather than a blank screen.
 *
 * @param goalId the string id of the goal to inspect, carried as a nav argument; `null`/unresolved
 *   renders the loading placeholder.
 * @param onBack pops back to the previous destination (AUI-5.1).
 * @param viewModel the shared goals view-model (Hilt-provided).
 */
@Composable
fun KineticGoalDetailHost(
    goalId: String?,
    onBack: () -> Unit,
    viewModel: GoalsViewModel = hiltViewModel(),
) {
    val goalUuid = rememberGoalUuid(goalId)

    LaunchedEffect(goalUuid) {
        if (goalUuid != null) viewModel.selectGoal(goalUuid)
    }

    val vmState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val selected = vmState.selectedGoal
    if (goalUuid == null || selected == null || selected.id != goalUuid) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val track = vmState.selectedGoalHabitTracks.firstOrNull()
    val completedDays = (track?.currentDay ?: 0).coerceIn(0, ROADMAP_TOTAL_DAYS)

    val state = GoalDetailUiState(
        goalId = selected.id.toString(),
        title = selected.name,
        category = selected.category,
        keywords = selected.keywords,
        checklist = vmState.selectedGoalChecklist.map { item ->
            TodoRowUi(
                id = item.id.toString(),
                text = item.text,
                done = item.isCompleted,
                deletable = true,
            )
        },
        roadmapDayLabel = context.getString(R.string.kinetic_roadmap_day_label, completedDays),
        roadmapActive = track?.let { !it.isFinished } ?: false,
        roadmapCompletedDays = completedDays,
        roadmapSubCaption = context.getString(R.string.kinetic_roadmap_sub_caption, completedDays),
    )

    val checklist = vmState.selectedGoalChecklist

    val actions = GoalDetailActions(
        onBack = onBack,
        onEdit = { viewModel.showEditDialog() },
        onDelete = { viewModel.showDeleteDialog() },
        onChecklistItemToggled = { id, _ ->
            checklist.findByStringId(id)?.let(viewModel::toggleChecklistItem)
        },
        onChecklistItemDeleted = { id ->
            runCatching { UUID.fromString(id) }.getOrNull()?.let(viewModel::deleteChecklistItem)
        },
        onAddChecklistItem = { text -> viewModel.addChecklistItem(text) },
    )

    GoalDetailScreen(state = state, on = actions)
}

/** Parses [goalId] into a [UUID] once per id change; `null` when absent or malformed. */
@Composable
private fun rememberGoalUuid(goalId: String?): UUID? =
    androidx.compose.runtime.remember(goalId) {
        goalId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    }

/** Locates a checklist item by its string id within the selected goal's checklist. */
private fun List<GoalChecklistItem>.findByStringId(id: String): GoalChecklistItem? =
    firstOrNull { it.id.toString() == id }
