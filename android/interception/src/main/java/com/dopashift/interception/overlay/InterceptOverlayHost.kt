package com.dopashift.interception.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.LaunchedEffect
import com.dopashift.ui.screens.InterceptOverlayScreen
import com.dopashift.ui.screens.OverlayActions
import com.dopashift.ui.theme.DopaShiftTheme
import java.util.UUID

/**
 * Hosts the DopaShift Kinetic `InterceptOverlayScreen` (owned by spec `android-ui-upgrade`, task
 * 6.1) inside the interception module's overlay window (task 11.2).
 *
 * This composable is the wiring seam between the interception engine and the presentation layer:
 *
 *  - It observes the [OverlayViewModel]'s existing [OverlayUiState] — the view-model already runs
 *    the single engagement timer, loads the rescue habit, and loads the pending to-dos.
 *  - It maps that state into the presentation-only `ui`-module `OverlayUiState` via
 *    [toKineticOverlayState], deriving the cooldown from the timer the view-model already runs.
 *    **No parallel timer or cooldown logic is introduced here** (design → "Intercept Overlay").
 *  - It forwards the Kinetic overlay's [OverlayActions] to the existing view-model methods, so the
 *    Continue / Switch routing, the Change_Log completion pipeline, and the Focus_Extension
 *    engagement signal all remain owned by `screen-time-interception-engine`.
 *
 * @param viewModel the interception [OverlayViewModel] exposing the overlay state and actions.
 * @param onDismiss invoked to tear down the overlay window (Continue / Switch both dismiss).
 */
@Composable
fun InterceptOverlayHost(
    viewModel: OverlayViewModel,
    onDismiss: () -> Unit,
) {
    val engineState by viewModel.uiState.collectAsState()

    DopaShiftTheme(darkTheme = true) {
        // DUX-2.11: the Intercept_Overlay appearance is one of the permitted haptic classes. Fire a
        // single light haptic when the overlay first composes; the countdown itself is driven by
        // the view-model's timer, not here.
        val haptics = LocalHapticFeedback.current
        LaunchedEffect(Unit) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }

        InterceptOverlayScreen(
            state = engineState.toKineticOverlayState(),
            on = OverlayActions(
                onSwitchToDopaShift = {
                    // Signal the engine to launch DopaShift, then tear down the overlay (AUI-2.8).
                    viewModel.onSwitchToDopaShift()
                    onDismiss()
                },
                onContinueToApp = {
                    // The Kinetic button is disabled until the cooldown elapses, so a click here
                    // already implies the minimum-engagement window has passed. Guard defensively
                    // against a stale click and route through the engine's Continue behavior.
                    if (engineState.canDismiss) {
                        viewModel.onContinueToApp()
                        onDismiss()
                    }
                },
                onRescueHabitToggled = { checked ->
                    // Completing the rescue micro-habit routes through the same checkpoint pipeline
                    // the rest of the app uses (Change_Log parity); it is a one-way completion.
                    if (checked) viewModel.completeHabitCheckpoint()
                },
                onPendingTaskToggled = { taskId, checked ->
                    // Completing a pending to-do routes through the Change_Log completion handler.
                    if (checked) runCatching { UUID.fromString(taskId) }
                        .getOrNull()
                        ?.let(viewModel::completeTodo)
                },
            ),
        )
    }
}
