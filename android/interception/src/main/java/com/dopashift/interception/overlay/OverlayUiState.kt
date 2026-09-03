package com.dopashift.interception.overlay

import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.entity.HabitCheckpoint
import com.dopashift.domain.entity.HabitTrack
import java.util.UUID

/**
 * UI state for the Intercept Overlay.
 *
 * @property pendingTodos Incomplete todo items for today (max display: all pending).
 * @property currentHabitTrack The earliest-start-date active habit track for the overlay.
 * @property currentCheckpoint The current day's pending checkpoint (if any).
 * @property videoSuggestionUrl URL for the recommended video (from cache or LLM pipeline).
 * @property videoTitle Display title for the video recommendation.
 * @property elapsedSeconds Seconds since the overlay was displayed (for engagement timer).
 * @property canDismiss Whether the user has satisfied the minimum engagement requirement.
 * @property isLoading Whether data is still being loaded.
 * @property triggeringPackageName The package name of the app that triggered this overlay.
 * @property hasZeroGoals Whether the intercepted user has zero active [com.dopashift.domain.entity.GoalProfile]s.
 *   When true the overlay offers Inline_Goal_Capture instead of dead-ending (DQC-5.10).
 */
data class OverlayUiState(
    val pendingTodos: List<DailyTodoItem> = emptyList(),
    val currentHabitTrack: HabitTrack? = null,
    val currentCheckpoint: HabitCheckpoint? = null,
    val videoSuggestionUrl: String? = null,
    val videoTitle: String? = null,
    val elapsedSeconds: Int = 0,
    val canDismiss: Boolean = false,
    val isLoading: Boolean = true,
    val triggeringPackageName: String = "",
    val hasZeroGoals: Boolean = false
) {
    companion object {
        /** Minimum seconds before the overlay can be dismissed (Requirement 2.5). */
        const val MIN_ENGAGEMENT_SECONDS = 30
    }

    /**
     * Whether any alternative action has been performed (todo completed or habit checked).
     * This also qualifies for dismissal per Requirement 2.5.
     */
    val hasPerformedAction: Boolean
        get() = pendingTodos.any { it.isCompleted } ||
                currentCheckpoint?.status == com.dopashift.domain.entity.CheckpointStatus.COMPLETED
}
