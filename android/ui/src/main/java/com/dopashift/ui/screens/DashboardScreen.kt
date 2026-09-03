package com.dopashift.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dopashift.ui.R
import com.dopashift.ui.adaptive.TopDestination
import com.dopashift.ui.components.CompletionRing
import com.dopashift.ui.components.DopaShiftBottomNavBar
import com.dopashift.ui.components.KineticCard
import com.dopashift.ui.components.KineticChip
import com.dopashift.ui.components.MomentumCheckbox
import com.dopashift.ui.components.QuickCreateFab
import com.dopashift.ui.state.AnchorHabitUi
import com.dopashift.ui.state.DashboardUiState
import com.dopashift.ui.state.GoalCardUi
import com.dopashift.ui.state.TodoRowUi
import com.dopashift.ui.theme.DopaShiftTokens

/**
 * Callback contract for the Home Dashboard (AUI-1).
 *
 * The screen is a stateless renderer of [DashboardUiState]; every user gesture is dispatched back
 * to the host view-model through this interface, keeping the composable free of any business logic
 * or parallel data path (design → "presentation-only, no parallel data path").
 *
 * @property onAnchorHabitToggled invoked when the Primary Anchor Habit checkbox is toggled
 *   (AUI-1.3); the new checked state is supplied.
 * @property onQuickAddTodo invoked with the trimmed text of the inline quick-add field to append a
 *   to-do to Today's Focus (AUI-1.4).
 * @property onTodoToggled invoked with a focus to-do's opaque id and its new completion state
 *   (AUI-1.5).
 * @property onGoalClicked invoked with a goal card's opaque id when the card is activated (AUI-1.6).
 * @property onQuickCreate invoked when the persistent Quick-Create FAB is tapped (AUI-1.7).
 * @property onNavigate invoked with the chosen bottom-nav destination (AUI-1.8, AUI-7.6).
 */
interface DashboardActions {
    fun onAnchorHabitToggled(checked: Boolean)
    fun onQuickAddTodo(text: String)
    fun onTodoToggled(id: String, done: Boolean)
    fun onGoalClicked(id: String)
    fun onQuickCreate()
    fun onNavigate(destination: TopDestination)
}

/**
 * The **Home Dashboard** screen (AUI-1).
 *
 * Renders, from top to bottom, the four dashboard surfaces described by [state]:
 *
 *  1. a header [KineticCard] with the amber streak [KineticChip], greeting, calming sub-caption,
 *     and a 64dp efficiency [CompletionRing] (AUI-1.1);
 *  2. the Primary Anchor Habit [KineticCard] with a teal accent stroke, a 48dp
 *     [MomentumCheckbox], the habit title/sub-line, and a monospace estimated-time pill — or a
 *     start-a-habit prompt when [DashboardUiState.anchorHabit] is `null` (AUI-1.2);
 *  3. the "Today's Focus" [KineticCard] with an inline quick-add row and [TodoRowUi] rows
 *     (AUI-1.4, AUI-1.5);
 *  4. a 2-column grid of active-goal cards (AUI-1.6).
 *
 * A persistent 56dp [QuickCreateFab] is anchored bottom-right (AUI-1.7) and the
 * [DopaShiftBottomNavBar] hosts the screen with [TopDestination.HOME] active (AUI-1.8).
 *
 * All colors, sizes, and text roles are sourced from [DopaShiftTokens]; all user-facing copy is
 * resolved via [stringResource] (AUI-8.1, AUI-8.6).
 *
 * @param state the rendered dashboard state.
 * @param on the callback contract for user gestures.
 * @param modifier optional layout modifier for the screen root.
 */
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    on: DashboardActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DopaShiftTokens.Colors.background,
        floatingActionButton = {
            QuickCreateFab(
                onClick = on::onQuickCreate,
                contentDescription = stringResource(R.string.aui_dashboard_quick_create_content_description),
            )
        },
        bottomBar = {
            DopaShiftBottomNavBar(
                current = TopDestination.HOME,
                onNavigate = on::onNavigate,
            )
        },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(GOAL_GRID_COLUMNS),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = DopaShiftTokens.Spacing.marginMobile),
            verticalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space16),
            horizontalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.gutterGrid),
            contentPadding = PaddingValues(vertical = DopaShiftTokens.Spacing.space16),
        ) {
            // --- Header card (spans the full grid width) ---
            fullSpanItem {
                DashboardHeaderCard(state = state)
            }

            // --- Primary Anchor Habit ---
            fullSpanItem {
                AnchorHabitCard(anchor = state.anchorHabit, on = on)
            }

            // --- Today's Focus ---
            fullSpanItem {
                TodaysFocusCard(todos = state.focusTodos, on = on)
            }

            // --- Active goals heading ---
            fullSpanItem {
                Text(
                    text = stringResource(R.string.aui_dashboard_goals_heading),
                    style = DopaShiftTokens.TextRoles.titleMedium,
                    color = DopaShiftTokens.Colors.textPrimary,
                )
            }

            // --- Active goals 2-column grid ---
            items(items = state.activeGoals, key = { it.id }) { goal ->
                GoalGridCard(goal = goal, onClick = { on.onGoalClicked(goal.id) })
            }
        }
    }
}

/**
 * Adds a single grid item that spans the full row width, used for the header, anchor, focus, and
 * section-heading rows so they stack above the 2-column goal grid.
 */
private fun LazyGridScope.fullSpanItem(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

/**
 * The Dashboard header (AUI-1.1): the amber streak pill, greeting headline, calming sub-caption, and
 * the 64dp efficiency [CompletionRing].
 */
@Composable
private fun DashboardHeaderCard(state: DashboardUiState) {
    KineticCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space8),
            ) {
                KineticChip(
                    text = stringResource(
                        R.string.aui_dashboard_streak_pill,
                        stringResource(R.string.aui_dashboard_streak_glyph),
                        state.streakDays,
                    ),
                    accent = DopaShiftTokens.Colors.warningAmber,
                )
                Text(
                    text = state.greeting,
                    style = DopaShiftTokens.TextRoles.headlineMedium,
                    color = DopaShiftTokens.Colors.textPrimary,
                )
                Text(
                    text = state.subCaption,
                    style = DopaShiftTokens.TextRoles.bodySmall,
                    color = DopaShiftTokens.Colors.textSecondary,
                )
            }
            Spacer(modifier = Modifier.width(DopaShiftTokens.Spacing.space16))
            CompletionRing(
                percent = state.efficiencyPercent,
                contentDescription = stringResource(
                    R.string.aui_dashboard_efficiency_ring_content_description,
                    state.efficiencyPercent.coerceIn(0, 100),
                ),
                size = EFFICIENCY_RING_SIZE,
            )
        }
    }
}

/**
 * The Primary Anchor Habit section (AUI-1.2). When [anchor] is non-null it renders a teal-accented
 * card with a 48dp [MomentumCheckbox], the habit title, a category/notes sub-line, and a monospace
 * estimated-time pill. When `null` it renders the start-a-habit prompt.
 */
@Composable
private fun AnchorHabitCard(anchor: AnchorHabitUi?, on: DashboardActions) {
    KineticCard(
        modifier = Modifier.fillMaxWidth(),
        accentStroke = DopaShiftTokens.Colors.momentumTeal,
    ) {
        Text(
            text = stringResource(R.string.aui_dashboard_anchor_heading),
            style = DopaShiftTokens.TextRoles.titleMedium,
            color = DopaShiftTokens.Colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(DopaShiftTokens.Spacing.space12))

        if (anchor == null) {
            Text(
                text = stringResource(R.string.aui_dashboard_anchor_empty_title),
                style = DopaShiftTokens.TextRoles.bodyLarge,
                color = DopaShiftTokens.Colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(DopaShiftTokens.Spacing.space4))
            Text(
                text = stringResource(R.string.aui_dashboard_anchor_empty_body),
                style = DopaShiftTokens.TextRoles.bodySmall,
                color = DopaShiftTokens.Colors.textSecondary,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    MomentumCheckbox(
                        checked = anchor.completed,
                        onCheckedChange = on::onAnchorHabitToggled,
                        label = anchor.title,
                    )
                    Text(
                        text = anchor.subtitle,
                        style = DopaShiftTokens.TextRoles.bodySmall,
                        color = DopaShiftTokens.Colors.textSecondary,
                        modifier = Modifier.padding(
                            start = DopaShiftTokens.TouchTargets.minTouchTarget,
                        ),
                    )
                }
                Spacer(modifier = Modifier.width(DopaShiftTokens.Spacing.space8))
                KineticChip(text = anchor.estimatedTimeLabel)
            }
        }
    }
}

/**
 * The "Today's Focus" card (AUI-1.4, AUI-1.5): an inline quick-add row (36dp input) with an Add
 * control, followed by the [TodoRowUi] rows or an empty-state line.
 */
@Composable
private fun TodaysFocusCard(todos: List<TodoRowUi>, on: DashboardActions) {
    // Keep the collapsed card focused on a few tasks; the rest are revealed in place via the
    // "Show all" toggle so the user stays on the Dashboard (there is no separate to-dos route).
    var expanded by rememberSaveable { mutableStateOf(false) }
    val visibleTodos = if (expanded) todos else todos.take(MAX_COLLAPSED_FOCUS_TODOS)
    val hasMoreTodos = todos.size > MAX_COLLAPSED_FOCUS_TODOS

    KineticCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.aui_dashboard_focus_title),
                style = DopaShiftTokens.TextRoles.titleMedium,
                color = DopaShiftTokens.Colors.textPrimary,
            )
            // "Show all (N)" / "Show less" toggle — only when the list exceeds the collapsed cap.
            if (hasMoreTodos) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        text = if (expanded) {
                            stringResource(R.string.aui_dashboard_focus_show_less)
                        } else {
                            stringResource(R.string.aui_dashboard_focus_show_all, todos.size)
                        },
                        style = DopaShiftTokens.TextRoles.labelLarge,
                        color = DopaShiftTokens.Colors.momentumTeal,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(DopaShiftTokens.Spacing.space12))

        QuickAddRow(onAdd = on::onQuickAddTodo)

        Spacer(modifier = Modifier.height(DopaShiftTokens.Spacing.space12))

        if (todos.isEmpty()) {
            Text(
                text = stringResource(R.string.aui_dashboard_focus_empty),
                style = DopaShiftTokens.TextRoles.bodySmall,
                color = DopaShiftTokens.Colors.textSecondary,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space4)) {
                visibleTodos.forEach { todo ->
                    FocusTodoRow(
                        todo = todo,
                        onToggled = { done -> on.onTodoToggled(todo.id, done) },
                    )
                }
            }
        }
    }
}

/**
 * Max number of Today's Focus to-dos shown while [TodaysFocusCard] is collapsed. Additional items
 * are revealed in place via the "Show all" toggle (AUI-1.4) — keeping the Dashboard uncluttered.
 */
private const val MAX_COLLAPSED_FOCUS_TODOS = 3

/**
 * The inline quick-add row for Today's Focus (AUI-1.4): a 36dp-tall input with an Add control that
 * emits the trimmed, non-blank text and clears the field.
 */
@Composable
private fun QuickAddRow(onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val placeholder = stringResource(R.string.aui_dashboard_focus_add_placeholder)
    val addDescription = stringResource(R.string.aui_dashboard_focus_add_content_description)

    fun submit() {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) {
            onAdd(trimmed)
            text = ""
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .weight(1f)
                .height(QUICK_ADD_INPUT_HEIGHT),
            placeholder = {
                Text(
                    text = placeholder,
                    style = DopaShiftTokens.TextRoles.bodyMedium,
                    color = DopaShiftTokens.Colors.textSecondary,
                )
            },
            textStyle = DopaShiftTokens.TextRoles.bodyMedium,
            singleLine = true,
            shape = RoundedCornerShape(DopaShiftTokens.Radii.control),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DopaShiftTokens.Colors.surfaceElevated,
                unfocusedContainerColor = DopaShiftTokens.Colors.surfaceElevated,
                focusedTextColor = DopaShiftTokens.Colors.textPrimary,
                unfocusedTextColor = DopaShiftTokens.Colors.textPrimary,
                focusedBorderColor = DopaShiftTokens.Colors.momentumTeal,
                unfocusedBorderColor = DopaShiftTokens.Colors.surfaceBorder,
            ),
        )
        IconButton(
            onClick = ::submit,
            modifier = Modifier
                .size(DopaShiftTokens.TouchTargets.minTouchTarget)
                .semantics { contentDescription = addDescription },
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null, // described by the IconButton semantics above
                tint = DopaShiftTokens.Colors.momentumTeal,
            )
        }
    }
}

/**
 * A single Today's Focus to-do row (AUI-1.4, AUI-1.5): a >=56dp row hosting a 48dp
 * [MomentumCheckbox] (which handles the struck-through/dimmed completed styling) plus an optional
 * type-tag chip.
 */
@Composable
private fun FocusTodoRow(todo: TodoRowUi, onToggled: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DopaShiftTokens.TouchTargets.listRowMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MomentumCheckbox(
            checked = todo.done,
            onCheckedChange = onToggled,
            label = todo.text,
            modifier = Modifier.weight(1f),
        )
        if (todo.typeTag != null) {
            KineticChip(text = todo.typeTag)
        }
    }
}

/**
 * A single active-goal card in the 2-column grid (AUI-1.6): a colored goal-icon container, a mini
 * [CompletionRing] with integer percent, the category + title, and a pending-task counter.
 */
@Composable
private fun GoalGridCard(goal: GoalCardUi, onClick: () -> Unit) {
    val iconDescription = stringResource(R.string.aui_dashboard_goal_icon_content_description, goal.title)
    KineticCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DopaShiftTokens.Radii.card))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(GOAL_ICON_SIZE)
                    .clip(RoundedCornerShape(DopaShiftTokens.Radii.control))
                    .background(goal.accentColor)
                    .semantics { contentDescription = iconDescription },
            )
            CompletionRing(
                percent = goal.completionPercent,
                contentDescription = stringResource(
                    R.string.aui_dashboard_goal_ring_content_description,
                    goal.title,
                    goal.completionPercent.coerceIn(0, 100),
                ),
                size = GOAL_RING_SIZE,
            )
        }
        Spacer(modifier = Modifier.height(DopaShiftTokens.Spacing.space12))
        KineticChip(text = goal.category)
        Spacer(modifier = Modifier.height(DopaShiftTokens.Spacing.space4))
        Text(
            text = goal.title,
            style = DopaShiftTokens.TextRoles.titleMedium,
            color = DopaShiftTokens.Colors.textPrimary,
        )
        Text(
            text = goal.pendingTaskLabel,
            style = DopaShiftTokens.TextRoles.bodySmall,
            color = DopaShiftTokens.Colors.textSecondary,
        )
    }
}

// -------------------------------------------------------------------------------------------------
// Component dimensions (not design-system spacing tokens)
// -------------------------------------------------------------------------------------------------

/** Two-column active-goals grid (AUI-1.6). */
private const val GOAL_GRID_COLUMNS = 2

/** The 64dp efficiency header ring (AUI-1.1). */
private val EFFICIENCY_RING_SIZE: Dp = 64.dp

/** The mini goal-card completion ring (AUI-1.6). */
private val GOAL_RING_SIZE: Dp = 44.dp

/** The colored goal-icon container on a goal card (AUI-1.6). */
private val GOAL_ICON_SIZE: Dp = 40.dp

/**
 * The inline quick-add input height (AUI-1.4). The design cites a 36dp compact add control; the
 * field itself is rendered at the 56dp input-field height so it clears the 48dp touch target and
 * does not clip its content, while the paired Add control stays compact.
 */
private val QUICK_ADD_INPUT_HEIGHT: Dp = 56.dp
