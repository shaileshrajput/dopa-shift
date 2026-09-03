package com.dopashift.ui.quickcreate

import androidx.compose.runtime.Immutable
import com.dopashift.domain.creation.GoalId
import com.dopashift.domain.entity.GoalProfile

/**
 * Immutable state driving [HabitCreationSheet] (DQC-3).
 *
 * The sheet has two possible first steps, decided entirely by [goals]:
 * - When the user has one or more active [GoalProfile]s, the first step is **goal selection**,
 *   defaulting to the most-recently-updated active goal (DQC-3.1). The default is computed by the
 *   shared [com.dopashift.domain.presentation.DefaultHabitGoalSelector] so the UI never
 *   reimplements the choice.
 * - When the user has **zero** active goals, the first step is **Inline_Goal_Capture** — the sheet
 *   collects the minimum valid goal fields inline (DQC-3.2). There is no blocking error and the
 *   user is never navigated to a separate goal screen; [isInlineGoalCapture] is `true`.
 *
 * All three authoring modes are offered (DQC-3.5). The LLM mode is enabled only when an AI provider
 * is configured; when [llmConfigured] is `false` the LLM mode is disabled with an explanatory label
 * and the selection defaults to Template (DQC-3.7).
 *
 * Disclosures the sheet surfaces before saving:
 * - [existingActiveTrackForSelectedGoal] drives the precedence disclosure (DQC-3.11).
 * - The manual auto-fill disclosure (DQC-3.9) is derived in the composable from how many of the 30
 *   checkpoints the user actually authored.
 *
 * A daily checkpoint [reminderEnabled] toggle is *offered, not required* (DQC-3.12).
 *
 * @property goals the user's active goals, used for the selection step and to detect the zero-goal
 *   case; empty means Inline_Goal_Capture.
 * @property defaultGoalId the pre-selected goal id (most-recently-updated active goal), or `null`
 *   when there are no active goals (DQC-3.1).
 * @property presetCategories preset goal categories offered in Inline_Goal_Capture (DQC-2.2).
 * @property suggestedKeywords keyword suggestions for the currently entered/selected category,
 *   pre-populated and individually editable/removable in Inline_Goal_Capture (DQC-2.3).
 * @property templateCategory the category used to resolve the bundled template in Template mode;
 *   for a selected goal it is that goal's category, otherwise the inline goal's category.
 * @property llmConfigured whether an AI provider is configured (gates LLM mode — DQC-3.7).
 * @property existingActiveTrackForSelectedGoal whether the selected goal already has an active
 *   habit track, driving the precedence disclosure (DQC-3.11).
 * @property isSaving whether a save is in flight (disables inputs and the save action).
 */
@Immutable
data class HabitSheetState(
    val goals: List<GoalProfile> = emptyList(),
    val defaultGoalId: GoalId? = null,
    val presetCategories: List<String> = emptyList(),
    val suggestedKeywords: List<String> = emptyList(),
    val templateCategory: String? = null,
    val llmConfigured: Boolean = false,
    val existingActiveTrackForSelectedGoal: Boolean = false,
    val isSaving: Boolean = false,
) {
    /**
     * Whether the sheet opens with Inline_Goal_Capture (the user has zero active goals) rather than
     * goal selection (DQC-3.2). Derived from [goals] so the two are never inconsistent.
     */
    val isInlineGoalCapture: Boolean get() = goals.isEmpty()
}

/**
 * The three habit authoring modes offered by [HabitCreationSheet] (DQC-3.5).
 *
 * Each resolves to exactly 30 checkpoints before persistence (handled in the domain layer). This
 * enum models only the user's *selection* in the UI; the concrete
 * [com.dopashift.domain.creation.HabitAuthoring] payload is assembled at save time.
 */
enum class HabitAuthoringMode {
    /** A bundled 30-day template for the goal category; works offline (DQC-3.6). */
    TEMPLATE,

    /** An AI-generated 30-day plan; enabled only when a provider is configured (DQC-3.7). */
    LLM,

    /** User-authored checkpoints; fewer than 30 are auto-filled with the last one (DQC-3.9). */
    MANUAL,
}

/**
 * Callbacks [HabitCreationSheet] invokes. The sheet owns transient form state (text fields, chosen
 * mode, manual checkpoints); these callbacks hand the *committed* action back to the caller.
 *
 * @property onSave commits the habit. [goalId] is non-null when an existing goal was selected
 *   (routes to `CreateHabitTrackUseCase`); it is `null` in the Inline_Goal_Capture case, in which
 *   case [inlineGoal] carries the inline goal fields (routes to `CreateGoalWithHabitUseCase`) —
 *   DQC-3.2, DQC-3.4. [authoring] is the resolved authoring payload, and [withReminder] reflects
 *   the offered-not-required daily reminder toggle (DQC-3.12).
 * @property onDismiss requested when the sheet is dismissed with no unsaved input (creates nothing).
 * @property onDismissWithUnsavedInput requested when the sheet is dismissed while it holds unsaved
 *   input, so the caller can confirm-before-discard (DQC-1.9).
 * @property onCategoryChanged notifies the caller the Inline_Goal_Capture category changed, so it
 *   can refresh [HabitSheetState.suggestedKeywords] and [HabitSheetState.templateCategory].
 * @property onGoalSelected notifies the caller a different existing goal was selected, so it can
 *   refresh [HabitSheetState.existingActiveTrackForSelectedGoal] and the template category.
 */
@Immutable
data class HabitSheetCallbacks(
    val onSave: (
        goalId: GoalId?,
        authoring: com.dopashift.domain.creation.HabitAuthoring,
        withReminder: Boolean,
        inlineGoal: InlineGoalFields?,
    ) -> Unit,
    val onDismiss: () -> Unit,
    val onDismissWithUnsavedInput: () -> Unit,
    val onCategoryChanged: (String) -> Unit = {},
    val onGoalSelected: (GoalId) -> Unit = {},
)
