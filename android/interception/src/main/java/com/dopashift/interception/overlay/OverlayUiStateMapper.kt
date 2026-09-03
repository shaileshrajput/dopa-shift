package com.dopashift.interception.overlay

import com.dopashift.domain.entity.HabitCheckpoint
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.ui.render.continueEnabled
import com.dopashift.ui.state.AnchorHabitUi
import com.dopashift.ui.state.TodoRowUi
import com.dopashift.ui.state.OverlayUiState as KineticOverlayUiState

/**
 * Maps the interception module's [OverlayUiState] (owned by `screen-time-interception-engine`, and
 * produced by [OverlayViewModel]) into the presentation-only [KineticOverlayUiState] that the
 * `ui`-module `InterceptOverlayScreen` renders (spec `android-ui-upgrade`, task 11.2).
 *
 * This is the seam that lets the DopaShift Kinetic overlay composable be hosted in the interception
 * module's overlay window **without a parallel data path**: the cooldown, rescue habit, and pending
 * tasks are all derived from state the service already exposes. In particular:
 *
 *  - **No new timer.** [OverlayViewModel] already runs the single engagement timer and exposes
 *    [OverlayUiState.elapsedSeconds]; the remaining cooldown is computed here as
 *    `MIN_ENGAGEMENT_SECONDS - elapsedSeconds` (floored at zero). The composable renders the value
 *    it is handed and never counts down itself (design → "Intercept Overlay (AUI-2)").
 *  - **Gating stays a pure function of state.** `continueEnabled` is derived by the shared
 *    [continueEnabled] helper from the mapped `cooldownSeconds`, so the on-device gating and the
 *    off-device property test (Property 1, AUI-2.7) agree by construction.
 */

/**
 * Converts this interception [OverlayUiState] into the Kinetic [KineticOverlayUiState] the
 * `ui`-module overlay composable renders.
 *
 * @param remainingCooldownSeconds the remaining cooldown, already derived from the engagement
 *   timer via [remainingCooldownSeconds]; passed in so the value is computed once per emission.
 * @return the presentation-only overlay state for the Kinetic `InterceptOverlayScreen`.
 */
fun OverlayUiState.toKineticOverlayState(): KineticOverlayUiState {
    val cooldown = remainingCooldownSeconds()
    return KineticOverlayUiState(
        targetAppName = triggeringPackageName,
        cooldownSeconds = cooldown,
        continueEnabled = continueEnabled(cooldown),
        rescueHabit = rescueHabitUi(),
        pendingTasks = pendingTodos.map { it.toTodoRowUi() },
    )
}

/**
 * The remaining cooldown in seconds, derived from the engagement timer the [OverlayViewModel]
 * already runs. Never negative: once [OverlayUiState.elapsedSeconds] reaches the minimum, the
 * cooldown is zero and the "Continue to app" button becomes enabled (AUI-2.7).
 */
internal fun OverlayUiState.remainingCooldownSeconds(): Int =
    (OverlayUiState.MIN_ENGAGEMENT_SECONDS - elapsedSeconds).coerceAtLeast(0)

/**
 * The rescue micro-habit card checkpoint for the overlay (AUI-2.4), or `null` when there is no
 * active checkpoint to surface. Built from the current [HabitTrack] and pending [HabitCheckpoint]
 * the view-model already loaded.
 */
private fun OverlayUiState.rescueHabitUi(): AnchorHabitUi? {
    val track = currentHabitTrack ?: return null
    val checkpoint = currentCheckpoint ?: return null
    return checkpoint.toAnchorHabitUi(track)
}

/** Maps a domain [HabitCheckpoint] + its [HabitTrack] into the Kinetic rescue-card view-model. */
private fun HabitCheckpoint.toAnchorHabitUi(track: HabitTrack): AnchorHabitUi = AnchorHabitUi(
    id = id.toString(),
    title = description,
    subtitle = "",
    estimatedTimeLabel = "",
    dayLabel = "Day ${track.currentDay}/30",
    completed = status == com.dopashift.domain.entity.CheckpointStatus.COMPLETED,
    rewardPrompt = null,
)

/** Maps a domain pending to-do into the Kinetic overlay pending-task row (AUI-2.5). */
private fun com.dopashift.domain.entity.DailyTodoItem.toTodoRowUi(): TodoRowUi = TodoRowUi(
    id = id.toString(),
    text = text,
    done = isCompleted,
)
