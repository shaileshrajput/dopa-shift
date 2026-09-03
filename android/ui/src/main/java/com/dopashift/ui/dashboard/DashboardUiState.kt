package com.dopashift.ui.dashboard

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.presentation.OrderedSection
import java.time.Instant
import java.util.UUID

/**
 * Dashboard render state (DUX-3).
 *
 * Modeled as a sealed hierarchy so the composition can distinguish the skeleton-first
 * loading phase (DUX-3 AC3) from ready content, without a nullable-field grab bag:
 *  - [Loading] maps directly to per-section [com.dopashift.ui.dashboard.SectionSkeleton]s
 *    (rendered within 100 ms, never a blank screen or full-screen spinner).
 *  - [Content] carries the deterministically ordered sections (from
 *    [com.dopashift.domain.presentation.DashboardSectionOrderer]) plus the header
 *    efficiency score and the offline / refresh indicators.
 *
 * All content renders from the local store; later Flow emissions update the affected
 * section reactively within 2 s (DUX-3 AC4, AC10).
 */
@Stable
sealed interface DashboardUiState {

    /** Initial phase before the first local emission — the UI shows section skeletons. */
    data object Loading : DashboardUiState

    /**
     * Ready state.
     *
     * @property sections the six Dashboard sections in fixed priority order, each flagged
     *   as a collapsed inline prompt when empty (DUX-3 AC1).
     * @property efficiencyScore today's efficiency percentage, or `null` for the ring's
     *   "No data" state (DUX-3 AC6) — never rendered as a misleading 0%.
     * @property isSyncPending `true` while local changes are still awaiting sync, driving a
     *   persistent non-modal indicator (DUX-3 AC9).
     * @property isRefreshing `true` while a pull-to-refresh sync pull is in flight
     *   (DUX-3 AC8).
     */
    @Immutable
    data class Content(
        val sections: List<OrderedSection>,
        val efficiencyScore: Int?,
        val isSyncPending: Boolean = false,
        val isRefreshing: Boolean = false
    ) : DashboardUiState
}

/**
 * One-shot Dashboard effects that must be delivered exactly once (not re-emitted on
 * recomposition or configuration change), consumed from a channel-backed flow.
 */
sealed interface UiEvent {

    /**
     * A previously-applied optimistic update was rejected by the write/sync layer and has
     * been reverted to its authoritative value. Consumed as a non-blocking snackbar
     * naming what changed (DUX-2 AC8).
     */
    data class Reverted(val message: String) : UiEvent
}

// ---------------------------------------------------------------------------------------
// Section detail models
//
// [DashboardUiState.Content] intentionally carries only the ordered section skeleton plus
// the header/indicator fields named in the design contract. The per-section item data the
// composables render is exposed separately by DashboardViewModel as [DashboardSectionData]
// so the ordering contract stays small and the section bodies stay reactive.
// ---------------------------------------------------------------------------------------

/** Trend of the daily efficiency score relative to recent history (DUX-3 AC5). */
enum class EfficiencyTrend { IMPROVING, DECLINING, STABLE, NOT_ENOUGH_DATA }

/**
 * Progress summary for one active goal card: completed vs. total checklist items plus the
 * current streak (DUX-3 AC7).
 */
@Immutable
data class GoalProgressSummary(
    val goal: GoalProfile,
    val completedItems: Int,
    val totalItems: Int,
    val streakDays: Int = 0
) {
    val progressPercent: Int
        get() = if (totalItems == 0) 0 else ((completedItems.toFloat() / totalItems) * 100).toInt()
}

/**
 * Stable, framework-free classification of an activity-feed action. The ViewModel emits one of
 * these (never a pre-localized English string) so the human-readable label can be resolved from
 * string resources in the Composable layer (DUX-4.11). [GENERIC] carries the raw entity type /
 * field for the unmapped fallback, formatted via `dux_activity_generic`.
 */
enum class ActivityActionType {
    TASK_COMPLETED,
    TASK_UPDATED,
    TASK_DELETED,
    GOAL_TASK_COMPLETED,
    GOAL_TASK_UPDATED,
    HABIT_COMPLETED,
    GOAL_DELETED,
    GOAL_CREATED,
    GENERIC,
}

/** A single entry in the recent-activity feed (DUX-3 AC1 (f)). */
@Immutable
data class ActivityFeedEntry(
    val id: UUID,
    val actionType: ActivityActionType,
    val entityName: String,
    val timestamp: Instant,
    /** Raw entity type, used only to format [ActivityActionType.GENERIC]. */
    val genericEntityType: String = "",
    /** Raw change field, used only to format [ActivityActionType.GENERIC]. */
    val genericField: String = "",
)

/**
 * The item-level data backing each Dashboard section body, exposed reactively alongside
 * [DashboardUiState.Content]. Kept separate from the ordering contract so section content
 * updates without reshaping the ordered-section list.
 */
@Immutable
data class DashboardSectionData(
    val todayTodos: List<DailyTodoItem> = emptyList(),
    val overdueTodos: List<DailyTodoItem> = emptyList(),
    val activeHabitTracks: List<HabitTrack> = emptyList(),
    val goalProgressSummaries: List<GoalProgressSummary> = emptyList(),
    val efficiencyScore: Int? = null,
    val previousDayScore: Int? = null,
    val efficiencyTrend: EfficiencyTrend = EfficiencyTrend.NOT_ENOUGH_DATA,
    val activityFeed: List<ActivityFeedEntry> = emptyList(),
    val activityFeedHasMore: Boolean = false
)
