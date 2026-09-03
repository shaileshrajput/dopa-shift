package com.dopashift.ui.quickcreate

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.dopashift.domain.presentation.MotionPolicy
import com.dopashift.ui.R
import com.dopashift.ui.theme.LocalDopaShiftSpacing
import com.dopashift.ui.theme.MotionTokens
import com.dopashift.ui.theme.rememberReducedMotion

/**
 * The Dashboard's persistent floating action button and its Quick_Create_Menu (DQC-1.1, DQC-1.2,
 * DQC-1.3, DQC-1.8).
 *
 * ### Persistence & reachability (DQC-1.1)
 * This composable is designed to be layered in the Dashboard's top-level [Box] (or a `Scaffold`
 * `floatingActionButton` slot), anchored to the bottom-end corner. It renders unconditionally —
 * independent of scroll offset, [window size class][androidx.compose.material3.windowsizeclass],
 * and Zero_State/Partial_State — so the FAB is always reachable without scrolling. The caller must
 * place it *over* scrolling content (not inside the scrollable list) so scroll position can never
 * move it off-screen.
 *
 * ### Menu content & order (DQC-1.2)
 * When expanded, the menu shows its actions in the fixed order [QuickCreateAction.NEW_GOAL],
 * [QuickCreateAction.NEW_HABIT], [QuickCreateAction.NEW_TODO], then [QuickCreateAction.MANAGE_APP_LIMITS]
 * (which navigates to the Rule Authoring screen, Requirement 2) — each rendered with an icon **and** a
 * text label, never icon-only. The set is taken verbatim from [QuickCreateAction.entries]; there is no
 * code path that can render a partial or reordered set.
 *
 * ### Presentation (DQC-1.3)
 * Selecting an action invokes [onAction] synchronously on touch-up; the host ViewModel's
 * `openQuickCreate` then flips [ActiveSheet] so the corresponding sheet renders on the next frame,
 * well within the 300 ms budget. This composable does no work between the tap and the callback.
 *
 * ### Dismissal creates nothing (DQC-1.8)
 * The menu is dismissible three ways, each of which only collapses the menu (via [onDismiss]) and
 * creates no entity: the system **back gesture** ([BackHandler], active only while expanded), a
 * **scrim tap** (the dimmed full-bleed background behind the menu), and a **FAB re-tap** (tapping
 * the FAB while expanded toggles it closed). Only an explicit action tap creates anything, and
 * even then creation is the ViewModel's job — not this composable's.
 *
 * ### Motion & accessibility
 * The scrim and menu items animate with [MotionTokens.Quick], collapsed to `motion-instant` under
 * the OS reduced-motion setting via [MotionPolicy.effective]. The FAB's plus↔close icon rotates
 * with the same spec. The FAB exposes an open/close content description; each action row exposes
 * its label as its accessible name and hides its decorative icon from the a11y tree.
 *
 * @param state whether the menu is currently expanded (DQC-1.2). The FAB itself is always shown.
 * @param onToggle called when the FAB is tapped: expand when collapsed, or dismiss when expanded
 *   (FAB re-tap, DQC-1.8). Creates nothing.
 * @param onDismiss called when the menu is dismissed by back gesture or scrim tap (DQC-1.8).
 *   Creates nothing.
 * @param onAction called with the selected [QuickCreateAction] when the user taps an action row
 *   (DQC-1.3). The host opens the matching Creation_Sheet.
 * @param modifier layout modifier applied to the FAB + menu container; the caller anchors it to
 *   the bottom-end of the Dashboard.
 */
@Composable
fun QuickCreateMenu(
    state: QuickCreateMenuState,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    onAction: (QuickCreateAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalDopaShiftSpacing.current
    val reducedMotion = rememberReducedMotion()
    val motionSpec = MotionPolicy.effective(MotionTokens.Quick, reducedMotion)
    val duration = motionSpec.durationMillis
    val easing = MotionTokens.easingFor(motionSpec.easingName)

    // Back gesture dismisses the expanded menu without creating anything (DQC-1.8). Registered
    // only while expanded so it never intercepts the host's own back handling when collapsed.
    BackHandler(enabled = state.expanded) { onDismiss() }

    // Full-bleed dimmed scrim behind the menu. A tap anywhere on it dismisses the menu (DQC-1.8).
    // It sits below the FAB/menu column in this Box so the actions and FAB stay tappable.
    AnimatedVisibility(
        visible = state.expanded,
        enter = fadeIn(tween(durationMillis = duration, easing = easing)),
        exit = fadeOut(tween(durationMillis = duration, easing = easing)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = stringResource(R.string.dqc_menu_scrim_content_description),
                    onClick = onDismiss,
                )
                .testTag(TestTags.QUICK_CREATE_SCRIM),
        )
    }

    Box(modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(spacing.stackSm),
            modifier = Modifier.padding(spacing.gutter),
        ) {
            // The three actions, in the fixed order New Goal, New Habit, New To-Do (DQC-1.2).
            AnimatedVisibility(
                visible = state.expanded,
                enter = fadeIn(tween(durationMillis = duration, easing = easing)) +
                    slideInVertically(
                        animationSpec = tween(durationMillis = duration, easing = easing),
                        initialOffsetY = { it / 2 },
                    ),
                exit = fadeOut(tween(durationMillis = duration, easing = easing)) +
                    slideOutVertically(
                        animationSpec = tween(durationMillis = duration, easing = easing),
                        targetOffsetY = { it / 2 },
                    ),
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(spacing.stackSm),
                ) {
                    QuickCreateAction.entries.forEach { action ->
                        QuickCreateActionRow(
                            action = action,
                            onClick = { onAction(action) },
                        )
                    }
                }
            }

            // Persistent FAB — always shown (DQC-1.1). Re-tap while expanded toggles closed
            // (DQC-1.8); tap while collapsed expands the menu.
            val iconRotation by animateFloatAsState(
                targetValue = if (state.expanded) FAB_ICON_EXPANDED_ROTATION else 0f,
                animationSpec = tween(durationMillis = duration, easing = easing),
                label = "quickCreateFabIconRotation",
            )
            FloatingActionButton(
                onClick = onToggle,
                modifier = Modifier.testTag(TestTags.QUICK_CREATE_FAB),
            ) {
                val fabDescription = if (state.expanded) {
                    stringResource(R.string.dqc_fab_close_content_description)
                } else {
                    stringResource(R.string.dqc_fab_open_content_description)
                }
                // The plus rotates into an X as the menu opens; a single icon keeps the FAB stable.
                Icon(
                    imageVector = if (state.expanded) Icons.Filled.Close else Icons.Filled.Add,
                    contentDescription = fabDescription,
                    modifier = Modifier.rotate(iconRotation),
                )
            }
        }
    }
}

/**
 * A single expanded menu action: a pill carrying its text label plus a mini-FAB carrying its icon
 * (DQC-1.2 — always labeled *and* iconed, never icon-only). The whole row activates [onClick].
 *
 * The row exposes its label as its single accessible name; the decorative label/icon child nodes
 * are cleared from the a11y tree so screen readers announce "New Goal" (etc.) once, not twice.
 */
@Composable
private fun QuickCreateActionRow(
    action: QuickCreateAction,
    onClick: () -> Unit,
) {
    val spacing = LocalDopaShiftSpacing.current
    val label = stringResource(action.labelRes())
    val icon = action.icon()

    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.stackSm),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(onClick = onClick)
            // Set the testTag inside the semantics block: clearAndSetSemantics discards any
            // semantics contributed by other modifiers/children (including a plain .testTag(...)),
            // so the tag must live in the block to survive and stay queryable by the UI tests.
            .clearAndSetSemantics {
                contentDescription = label
                testTag = action.testTag()
            },
    ) {
        // Label pill — the text half of the labeled+iconed action.
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(LABEL_CORNER_RADIUS.dp),
            tonalElevation = LABEL_ELEVATION.dp,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(
                    horizontal = spacing.gutter,
                    vertical = spacing.base,
                ),
            )
        }
        // Icon mini-FAB — the icon half.
        FloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Icon(imageVector = icon, contentDescription = null)
        }
    }
}

/** The localized label resource for each action, in menu order (DQC-1.2). */
private fun QuickCreateAction.labelRes(): Int = when (this) {
    QuickCreateAction.NEW_GOAL -> R.string.dqc_menu_new_goal
    QuickCreateAction.NEW_HABIT -> R.string.dqc_menu_new_habit
    QuickCreateAction.NEW_TODO -> R.string.dqc_menu_new_todo
    QuickCreateAction.MANAGE_APP_LIMITS -> R.string.rule_authoring_settings_entry
}

/** The icon paired with each action (DQC-1.2 — every action is iconed). */
private fun QuickCreateAction.icon(): ImageVector = when (this) {
    QuickCreateAction.NEW_GOAL -> Icons.Outlined.Flag
    QuickCreateAction.NEW_HABIT -> Icons.Outlined.Repeat
    QuickCreateAction.NEW_TODO -> Icons.Outlined.CheckCircle
    QuickCreateAction.MANAGE_APP_LIMITS -> Icons.Outlined.Settings
}

/** Stable test tag per action row for the Compose UI tests (task 18.1). */
private fun QuickCreateAction.testTag(): String = when (this) {
    QuickCreateAction.NEW_GOAL -> TestTags.QUICK_CREATE_ACTION_GOAL
    QuickCreateAction.NEW_HABIT -> TestTags.QUICK_CREATE_ACTION_HABIT
    QuickCreateAction.NEW_TODO -> TestTags.QUICK_CREATE_ACTION_TODO
    QuickCreateAction.MANAGE_APP_LIMITS -> TestTags.QUICK_CREATE_ACTION_MANAGE_APP_LIMITS
}

/**
 * Stable Compose test tags for the FAB, scrim, and the three action rows. Consumed by the
 * Dashboard Compose UI tests (task 18.1) to assert menu content, order, and dismissal behavior.
 */
object TestTags {
    const val QUICK_CREATE_FAB = "quickCreateFab"
    const val QUICK_CREATE_SCRIM = "quickCreateScrim"
    const val QUICK_CREATE_ACTION_GOAL = "quickCreateActionGoal"
    const val QUICK_CREATE_ACTION_HABIT = "quickCreateActionHabit"
    const val QUICK_CREATE_ACTION_TODO = "quickCreateActionTodo"
    const val QUICK_CREATE_ACTION_MANAGE_APP_LIMITS = "quickCreateActionManageAppLimits"

    // DashboardBody Zero/Partial state + onboarding resume (task 15.5, consumed by task 18.1).
    const val DASHBOARD_ZERO_STATE = "dashboardZeroState"
    const val DASHBOARD_ZERO_PROMPT_GOAL = "dashboardZeroPromptGoal"
    const val DASHBOARD_ZERO_PROMPT_TODO = "dashboardZeroPromptTodo"
    const val DASHBOARD_ZERO_PROMPT_HABIT = "dashboardZeroPromptHabit"
    const val DASHBOARD_INLINE_PROMPT_GOALS = "dashboardInlinePromptGoals"
    const val DASHBOARD_INLINE_PROMPT_TODOS = "dashboardInlinePromptTodos"
    const val DASHBOARD_INLINE_PROMPT_HABITS = "dashboardInlinePromptHabits"
    const val DASHBOARD_SKIP_ONBOARDING = "dashboardSkipOnboarding"
    const val SETTINGS_RESUME_ONBOARDING = "settingsResumeOnboarding"
    const val SETTINGS_RESUME_ONBOARDING_DISMISS = "settingsResumeOnboardingDismiss"
}

/** Rotation (degrees) applied to the FAB's plus icon when the menu is expanded (plus -> X). */
private const val FAB_ICON_EXPANDED_ROTATION = 135f

/** Scrim opacity behind the expanded menu. */
private const val SCRIM_ALPHA = 0.32f

/** Action label pill corner radius (dp). */
private const val LABEL_CORNER_RADIUS = 12

/** Action label pill tonal elevation (dp). */
private const val LABEL_ELEVATION = 3
