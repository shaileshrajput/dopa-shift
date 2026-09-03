package com.dopashift.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.dopashift.ui.R
import com.dopashift.ui.components.KineticCard
import com.dopashift.ui.components.KineticChip
import com.dopashift.ui.components.MomentumCheckbox
import com.dopashift.ui.render.continueEnabled
import com.dopashift.ui.state.AnchorHabitUi
import com.dopashift.ui.state.OverlayUiState
import com.dopashift.ui.state.TodoRowUi
import com.dopashift.ui.theme.DopaShiftTokens

/**
 * The set of user actions dispatched from the Intercept Overlay (AUI-2).
 *
 * The overlay composable is stateless: it renders the [OverlayUiState] it is given and forwards
 * user gestures through this callback surface to the owning view-model
 * (`screen-time-interception-engine`). It runs no timer and mutates no state itself (design →
 * "Intercept Overlay (AUI-2)").
 *
 * @property onSwitchToDopaShift invoked when the primary "Switch to DopaShift" button is
 *   activated; the host opens the Dashboard and clears the overlay (AUI-2.8).
 * @property onContinueToApp invoked when the (enabled) secondary "Continue to app" button is
 *   activated; dismisses the overlay and returns to the blocked app.
 * @property onRescueHabitToggled invoked with the new checked state when the rescue micro-habit
 *   checkbox is toggled (AUI-2.4).
 * @property onPendingTaskToggled invoked with the task id and its new checked state when a pending
 *   focus task is toggled (AUI-2.5).
 */
data class OverlayActions(
    val onSwitchToDopaShift: () -> Unit = {},
    val onContinueToApp: () -> Unit = {},
    val onRescueHabitToggled: (Boolean) -> Unit = {},
    val onPendingTaskToggled: (String, Boolean) -> Unit = { _, _ -> },
)

/** Maximum number of pending focus tasks rendered on the overlay (AUI-2.5). */
private const val MAX_PENDING_TASKS = 3

/**
 * The full-screen **Intercept Overlay** (AUI-2).
 *
 * Rendered over a blocked application when its allowance is depleted. The surface is a firm-yet-calm
 * intervention that offers an immediate micro-habit rescue and pending focus tasks before allowing
 * the user to proceed. This composable is **presentation-only**: it renders [state] (supplied by
 * `screen-time-interception-engine`) and forwards gestures via [on]; it does not run the cooldown
 * timer (design → "Intercept Overlay").
 *
 * Layout, top to bottom:
 *  1. A 90% deep-slate scrim backdrop over the blocked app (AUI-2.1).
 *  2. Header — a Momentum-Teal `• DOPAMINE INTERCEPT` [KineticChip], a centered brain glyph in
 *     Productive Blue, a 24sp bold headline, and body copy naming [OverlayUiState.targetAppName]
 *     (AUI-2.2).
 *  3. A monospace cooldown badge on an elevated surface showing [OverlayUiState.cooldownSeconds]
 *     (AUI-2.3).
 *  4. A Behavioral Rescue [KineticCard] with a Momentum-Teal accent stroke, a `Quick Micro-Habit
 *     (Day N/30)` label, a [MomentumCheckbox] carrying the habit description, and a reward-prompt
 *     line in Momentum Teal (AUI-2.4).
 *  5. Up to three pending focus tasks, each with an estimated-time badge (AUI-2.5).
 *  6. Two stacked 52dp action buttons — primary "Switch to DopaShift" (Productive Blue) and
 *     secondary "Continue to app (Ns)" (surface), the latter disabled while the cooldown is
 *     non-zero (AUI-2.6, AUI-2.7).
 *
 * The secondary button's enabled state is derived by [continueEnabled], the shared pure helper, so
 * the on-device gating and the off-device property test (Property 1) agree by construction. All
 * colors, sizes, and text roles come from [DopaShiftTokens]; every user-facing string is a
 * [stringResource] (AUI-8.1, AUI-8.6), and the brain glyph plus the scrim carry content
 * descriptions for TalkBack (AUI-8.5).
 *
 * @param state the overlay render state supplied by the interception engine.
 * @param on the user-action callbacks dispatched to the owning view-model.
 * @param modifier layout modifier applied to the overlay root.
 */
@Composable
fun InterceptOverlayScreen(
    state: OverlayUiState,
    on: OverlayActions,
    modifier: Modifier = Modifier,
) {
    val scrimDescription = stringResource(R.string.aui_overlay_scrim_content_description)

    Box(
        modifier = modifier
            .fillMaxSize()
            // 90% deep-slate scrim over the blocked app (AUI-2.1).
            .background(DopaShiftTokens.Colors.scrimOverlay)
            .semantics { contentDescription = scrimDescription }
            .padding(DopaShiftTokens.Spacing.space24),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space16),
        ) {
            OverlayHeader(targetAppName = state.targetAppName)

            CooldownBadge(cooldownSeconds = state.cooldownSeconds)

            state.rescueHabit?.let { habit ->
                RescueCard(habit = habit, onToggle = on.onRescueHabitToggled)
            }

            if (state.pendingTasks.isNotEmpty()) {
                PendingTasks(
                    tasks = state.pendingTasks.take(MAX_PENDING_TASKS),
                    onToggle = on.onPendingTaskToggled,
                )
            }

            OverlayActionButtons(state = state, on = on)
        }
    }
}

/** Header: intercept pill, centered brain glyph, headline, and app-named body copy (AUI-2.2). */
@Composable
private fun OverlayHeader(targetAppName: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space12),
    ) {
        KineticChip(
            text = stringResource(R.string.aui_overlay_intercept_pill),
            accent = DopaShiftTokens.Colors.momentumTeal,
        )

        Icon(
            imageVector = Icons.Filled.Psychology,
            contentDescription = stringResource(R.string.aui_overlay_brain_glyph_content_description),
            tint = DopaShiftTokens.Colors.primaryBlue,
            modifier = Modifier.size(BRAIN_GLYPH_SIZE),
        )

        Text(
            text = stringResource(R.string.aui_overlay_headline),
            style = DopaShiftTokens.TextRoles.headlineMedium,
            color = DopaShiftTokens.Colors.textPrimary,
            textAlign = TextAlign.Center,
        )

        Text(
            text = stringResource(R.string.aui_overlay_body, targetAppName),
            style = DopaShiftTokens.TextRoles.bodyMedium,
            color = DopaShiftTokens.Colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** Monospace cooldown badge on an elevated surface counting down to dismiss (AUI-2.3). */
@Composable
private fun CooldownBadge(cooldownSeconds: Int) {
    val badgeDescription =
        stringResource(R.string.aui_overlay_cooldown_content_description, cooldownSeconds)
    Text(
        text = stringResource(R.string.aui_overlay_cooldown_badge, cooldownSeconds),
        style = DopaShiftTokens.TextRoles.labelMono,
        color = DopaShiftTokens.Colors.warningAmber,
        modifier = Modifier
            .background(
                DopaShiftTokens.Colors.surfaceElevated,
                RoundedCornerShape(DopaShiftTokens.Radii.pill),
            )
            .padding(
                horizontal = DopaShiftTokens.Spacing.space16,
                vertical = DopaShiftTokens.Spacing.space8,
            )
            .semantics { contentDescription = badgeDescription },
    )
}

/** Behavioral Rescue Card: teal-accented card with a micro-habit checkbox + reward prompt (AUI-2.4). */
@Composable
private fun RescueCard(habit: AnchorHabitUi, onToggle: (Boolean) -> Unit) {
    KineticCard(
        modifier = Modifier.fillMaxWidth(),
        accentStroke = DopaShiftTokens.Colors.momentumTeal,
    ) {
        Text(
            text = stringResource(R.string.aui_overlay_rescue_label, habit.dayLabel),
            style = DopaShiftTokens.TextRoles.labelMono,
            color = DopaShiftTokens.Colors.momentumTeal,
        )

        Spacer(modifier = Modifier.height(DopaShiftTokens.Spacing.space8))

        MomentumCheckbox(
            checked = habit.completed,
            onCheckedChange = onToggle,
            label = habit.title,
            modifier = Modifier.fillMaxWidth(),
        )

        habit.rewardPrompt?.let { prompt ->
            Spacer(modifier = Modifier.height(DopaShiftTokens.Spacing.space8))
            Text(
                text = prompt,
                style = DopaShiftTokens.TextRoles.bodySmall,
                color = DopaShiftTokens.Colors.momentumTeal,
            )
        }
    }
}

/** Up to three pending focus tasks, each with an estimated-time badge (AUI-2.5). */
@Composable
private fun PendingTasks(tasks: List<TodoRowUi>, onToggle: (String, Boolean) -> Unit) {
    KineticCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.aui_overlay_pending_title),
            style = DopaShiftTokens.TextRoles.titleMedium,
            color = DopaShiftTokens.Colors.textPrimary,
        )

        tasks.forEach { task ->
            Spacer(modifier = Modifier.height(DopaShiftTokens.Spacing.space8))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space8),
            ) {
                MomentumCheckbox(
                    checked = task.done,
                    onCheckedChange = { checked -> onToggle(task.id, checked) },
                    label = task.text,
                    modifier = Modifier.weight(1f),
                )
                task.estimatedTimeLabel?.let { estimate ->
                    KineticChip(text = estimate, accent = DopaShiftTokens.Colors.clarityCyan)
                }
            }
        }
    }
}

/**
 * Two stacked 52dp action buttons (AUI-2.6, AUI-2.7).
 *
 * The primary "Switch to DopaShift" button always dispatches [OverlayActions.onSwitchToDopaShift].
 * The secondary "Continue to app" button is enabled strictly when the cooldown has elapsed —
 * gating derived via [continueEnabled] from [OverlayUiState.cooldownSeconds]; its label shows the
 * remaining seconds while disabled and drops the counter once ready.
 */
@Composable
private fun OverlayActionButtons(state: OverlayUiState, on: OverlayActions) {
    val enabled = continueEnabled(state.cooldownSeconds)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space12),
    ) {
        Button(
            onClick = on.onSwitchToDopaShift,
            modifier = Modifier
                .fillMaxWidth()
                .height(DopaShiftTokens.TouchTargets.primaryButtonHeight),
            shape = RoundedCornerShape(DopaShiftTokens.Radii.button),
            colors = ButtonDefaults.buttonColors(
                containerColor = DopaShiftTokens.Colors.primaryBlue,
                contentColor = DopaShiftTokens.Colors.onPrimaryBlue,
            ),
        ) {
            Text(
                text = stringResource(R.string.aui_overlay_switch),
                style = DopaShiftTokens.TextRoles.labelLarge,
            )
        }

        val continueLabel = if (enabled) {
            stringResource(R.string.aui_overlay_continue_ready)
        } else {
            stringResource(R.string.aui_overlay_continue, state.cooldownSeconds)
        }
        Button(
            onClick = on.onContinueToApp,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(DopaShiftTokens.TouchTargets.primaryButtonHeight),
            shape = RoundedCornerShape(DopaShiftTokens.Radii.button),
            colors = ButtonDefaults.buttonColors(
                containerColor = DopaShiftTokens.Colors.surface,
                contentColor = DopaShiftTokens.Colors.onSurfaceButton,
                disabledContainerColor = DopaShiftTokens.Colors.surface,
                disabledContentColor = DopaShiftTokens.Colors.textCompleted,
            ),
        ) {
            Text(text = continueLabel, style = DopaShiftTokens.TextRoles.labelLarge)
        }
    }
}

/** Brain glyph size in the overlay header (56dp, AUI-2.2); a component dimension. */
private val BRAIN_GLYPH_SIZE = DopaShiftTokens.TouchTargets.fabSize
