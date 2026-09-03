package com.dopashift.ui.quickcreate

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.dopashift.domain.creation.UndoToken

/**
 * Which Dashboard Creation_Sheet is currently presented (DQC-1.2, DQC-1.3).
 *
 * The Quick_Create_Menu exposes exactly three creation actions — New Goal, New Habit,
 * New To-Do — each of which opens the corresponding sheet. [None] means no sheet is
 * showing (the Dashboard is idle or only the menu is expanded). Modeled as a sealed
 * hierarchy so the composition can present exactly one sheet and so a dismissal always
 * returns to a single, unambiguous idle state.
 */
@Stable
sealed interface ActiveSheet {

    /** No Creation_Sheet is presented. */
    data object None : ActiveSheet

    /** The goal Creation_Sheet is presented (DQC-2). */
    data object Goal : ActiveSheet

    /** The habit Creation_Sheet is presented, hosting Inline_Goal_Capture when needed (DQC-3). */
    data object Habit : ActiveSheet

    /** The full to-do Creation_Sheet is presented (DQC-4.4). */
    data object Todo : ActiveSheet
}

/**
 * A transient, non-modal confirmation shown after a successful Dashboard creation, carrying
 * the sync-safe Undo affordance (DQC-1.6, DQC-1.7).
 *
 * The Undo action must remain available for at least [MIN_VISIBLE_MILLIS] (5 s). Activating
 * it hands [undoToken] back to the ViewModel, which deletes the created entity through the
 * standard delete path so a Change_Log deletion event flows through the Sync_Engine — never a
 * local-only rollback.
 *
 * @property message a short, already-localized confirmation message describing what was created.
 * @property undoToken the token identifying the entity/entities to remove if Undo is activated.
 */
@Immutable
data class UndoSnackbar(
    val message: String,
    val undoToken: UndoToken
) {
    companion object {
        /** Minimum time the Undo action must remain available after a creation (DQC-1.6). */
        const val MIN_VISIBLE_MILLIS: Long = 5_000L
    }
}

/**
 * The three creation actions the Quick_Create_Menu expands to, in the exact order the menu
 * presents them: New Goal, New Habit, New To-Do (DQC-1.2). Declaration order is significant —
 * [entries] is used verbatim to lay the menu out, so reordering here reorders the menu.
 *
 * Each action carries an icon **and** a text label in the UI, never icon-only (DQC-1.2); the
 * label/icon pairing is resolved by the composable so this enum stays free of Compose types.
 * Selecting an action opens the corresponding [ActiveSheet] via
 * [QuickCreateViewModel.openQuickCreate].
 */
enum class QuickCreateAction {

    /** Opens the goal Creation_Sheet (DQC-2). */
    NEW_GOAL,

    /** Opens the habit Creation_Sheet, hosting Inline_Goal_Capture when needed (DQC-3). */
    NEW_HABIT,

    /** Opens the full to-do Creation_Sheet (DQC-4.4). */
    NEW_TODO,

    /** Opens the Manage App Limits screen (Requirement 2). Creates nothing. */
    MANAGE_APP_LIMITS,
}

/**
 * UI state for the persistent FAB and its Quick_Create_Menu (DQC-1.1, DQC-1.2, DQC-1.8).
 *
 * The FAB is always rendered — it is reachable without scrolling in every scroll position, window
 * size class, and both Zero_State and Partial_State (DQC-1.1) — so this state only tracks whether
 * the menu is currently [expanded]. The menu's three actions are fixed and ordered
 * ([QuickCreateAction.entries]); they are not carried in state so the menu can never render a
 * partial or reordered action set.
 *
 * Expanding/collapsing the menu creates nothing on its own; only selecting an action opens a
 * sheet, and dismissal (back gesture, scrim tap, FAB re-tap) merely collapses the menu (DQC-1.8).
 *
 * @property expanded whether the menu is currently showing its three actions.
 */
@Immutable
data class QuickCreateMenuState(
    val expanded: Boolean = false,
)
