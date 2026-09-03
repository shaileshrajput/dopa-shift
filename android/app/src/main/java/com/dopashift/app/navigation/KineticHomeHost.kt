package com.dopashift.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dopashift.app.R
import com.dopashift.domain.entity.CheckpointStatus
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.ui.adaptive.TopDestination
import com.dopashift.ui.dashboard.DashboardSectionData
import com.dopashift.ui.dashboard.DashboardViewModel
import com.dopashift.ui.screens.DashboardActions
import com.dopashift.ui.screens.DashboardScreen
import com.dopashift.ui.state.AnchorHabitUi
import com.dopashift.ui.state.DashboardUiState
import com.dopashift.ui.state.GoalCardUi
import com.dopashift.ui.state.TodoRowUi
import com.dopashift.ui.theme.DopaShiftTokens
import java.util.UUID

/**
 * Presentation bridge hosting the Kinetic [DashboardScreen] (AUI-1) on the [KineticRoutes.HOME]
 * route (spec `android-ui-upgrade`, task 11.1).
 *
 * It observes the existing [DashboardViewModel] — which renders strictly from the local store — and
 * maps its [DashboardSectionData] into the presentation-only [DashboardUiState] the Kinetic screen
 * renders. Gestures are dispatched straight back to that view-model's use cases; the FAB opens the
 * `dashboard-quick-create` surface via [onOpenQuickCreate] (AUI-1.7) and the bottom nav navigates
 * via [onNavigate] (AUI-1.8). No parallel data path is introduced.
 *
 * @param onNavigate invoked with the chosen bottom-nav [TopDestination] (AUI-1.8).
 * @param onOpenQuickCreate opens the Quick-Create surface (AUI-1.7).
 * @param onOpenGoalDetail opens Goal Detail for a tapped goal card (AUI-1.6 → AUI-5).
 * @param viewModel the existing dashboard view-model (Hilt-provided).
 */
@Composable
fun KineticHomeHost(
    onNavigate: (TopDestination) -> Unit,
    onOpenQuickCreate: () -> Unit,
    onOpenGoalDetail: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val sectionData by viewModel.sectionData.collectAsStateWithLifecycle()

    val greeting = stringResource(R.string.kinetic_dashboard_greeting)
    val subCaption = stringResource(R.string.kinetic_dashboard_sub_caption)
    val context = LocalContext.current

    val efficiencyPercent = sectionData.efficiencyScore ?: 0

    val state = DashboardUiState(
        greeting = greeting,
        subCaption = subCaption,
        streakDays = highestStreak(sectionData),
        efficiencyPercent = efficiencyPercent.coerceIn(0, 100),
        anchorHabit = sectionData.anchorHabitOrNull { day ->
            context.getString(R.string.kinetic_roadmap_day_label, day)
        },
        focusTodos = sectionData.todayTodos.map { todo ->
            TodoRowUi(id = todo.id.toString(), text = todo.text, done = todo.isCompleted)
        },
        activeGoals = sectionData.goalProgressSummaries.map { summary ->
            GoalCardUi(
                id = summary.goal.id.toString(),
                title = summary.goal.name,
                category = summary.goal.category,
                completionPercent = summary.progressPercent.coerceIn(0, 100),
                pendingTaskLabel = context.getString(
                    R.string.kinetic_pending_tasks_count,
                    (summary.totalItems - summary.completedItems).coerceAtLeast(0),
                    summary.totalItems,
                ),
                keywords = summary.goal.keywords,
                accentColor = accentFor(summary.goal.category),
                streakCompletedDays = summary.streakDays.coerceIn(0, ROADMAP_TOTAL_DAYS),
                streakTotalDays = ROADMAP_TOTAL_DAYS,
                roadmapStatusLabel = context.getString(
                    R.string.kinetic_roadmap_status,
                    summary.streakDays.coerceIn(0, ROADMAP_TOTAL_DAYS),
                ),
            )
        },
    )

    val actions = object : DashboardActions {
        override fun onAnchorHabitToggled(checked: Boolean) {
            currentAnchorTrackId(sectionData)?.let(viewModel::completeHabitCheckpoint)
        }

        override fun onQuickAddTodo(text: String) = viewModel.addTodo(text)

        override fun onTodoToggled(id: String, done: Boolean) {
            runCatching { UUID.fromString(id) }.getOrNull()?.let { uuid ->
                viewModel.setTodoCompleted(uuid, done)
            }
        }

        override fun onGoalClicked(id: String) = onOpenGoalDetail(id)

        override fun onQuickCreate() = onOpenQuickCreate()

        override fun onNavigate(destination: TopDestination) = onNavigate(destination)
    }

    DashboardScreen(state = state, on = actions)
}

/**
 * Derives today's Primary Anchor Habit from the active habit tracks: the current-day checkpoint of
 * the first track that still has one. Returns `null` when none is due (the screen then renders the
 * start-a-habit prompt, AUI-1.2).
 */
private fun DashboardSectionData.anchorHabitOrNull(
    dayLabel: (Int) -> String,
): AnchorHabitUi? {
    val track = activeHabitTracks.firstOrNull { it.currentCheckpointOrNull() != null } ?: return null
    val checkpoint = track.currentCheckpointOrNull() ?: return null
    return AnchorHabitUi(
        id = checkpoint.id.toString(),
        title = checkpoint.description,
        subtitle = "",
        estimatedTimeLabel = "",
        dayLabel = dayLabel(track.currentDay),
        completed = checkpoint.status == CheckpointStatus.COMPLETED,
    )
}

/** The current-day checkpoint of a track, or `null` when the track has none for today. */
private fun HabitTrack.currentCheckpointOrNull() =
    checkpoints.firstOrNull { it.dayNumber == currentDay }

/** The track id backing today's anchor habit, used to dispatch the checkpoint completion. */
private fun currentAnchorTrackId(data: DashboardSectionData): UUID? =
    data.activeHabitTracks.firstOrNull { it.currentCheckpointOrNull() != null }?.id

/** The largest streak (current day) across active tracks, shown in the header streak pill. */
private fun highestStreak(data: DashboardSectionData): Int =
    data.activeHabitTracks.maxOfOrNull { it.currentDay } ?: 0

/**
 * A stable accent color for a goal category, sourced from [DopaShiftTokens] (never a raw literal,
 * AUI-8.1). The mapping is deterministic so the same category always tints the same.
 */
internal fun accentFor(category: String): Color {
    val palette = listOf(
        DopaShiftTokens.Colors.momentumTeal,
        DopaShiftTokens.Colors.primaryBlue,
        DopaShiftTokens.Colors.clarityCyan,
    )
    val index = (category.hashCode() % palette.size + palette.size) % palette.size
    return palette[index]
}

/** The fixed 30-day roadmap span shared with the domain→UI mappers. */
internal const val ROADMAP_TOTAL_DAYS: Int = 30
