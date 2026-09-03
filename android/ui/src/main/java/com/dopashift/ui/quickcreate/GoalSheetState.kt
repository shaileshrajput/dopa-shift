package com.dopashift.ui.quickcreate

import androidx.compose.runtime.Immutable

/**
 * Immutable state driving [GoalCreationSheet] (DQC-2).
 *
 * The sheet collects the three required goal fields — name, category, and keywords (DQC-2.1) —
 * with inline, per-field validation via the shared [com.dopashift.domain.creation.GoalValidator]
 * so client-side rules match the dedicated Goals screen exactly and never form a parallel
 * implementation (DQC-1.10, DQC-1.11).
 *
 * ### Category (DQC-2.2)
 * [presetCategories] backs a preset picker (Career Growth, Fitness, Build Business, Clear Exam,
 * Learning, Wellbeing, ...); the same category field also accepts free-text entry of a category
 * not in the preset list.
 *
 * ### Keywords (DQC-2.3)
 * When the user picks a preset category the caller refreshes [suggestedKeywords] with the bundled
 * mapping for that category; the sheet pre-populates them and lets the user accept, edit (remove +
 * re-add), or individually remove each one before saving.
 *
 * ### Uniqueness (DQC-2.6)
 * [existingGoalNamesLower] are the user's existing goal names, already lower-cased, so the sheet can
 * show an inline duplicate-name error and disable saving before submission — matching the shared
 * validator's case-insensitive uniqueness rule. The Backend re-checks on sync (DQC-2.11).
 *
 * ### Suggest with AI (DQC-2.5)
 * [llmConfigured] gates the "Suggest with AI" affordance: shown/enabled when an AI provider is
 * configured, otherwise shown disabled with an explanatory label rather than an action that errors
 * on tap. [isSuggesting] is `true` while an AI suggestion request is in flight.
 *
 * @property presetCategories preset category names for the picker; also allows free text (DQC-2.2).
 * @property suggestedKeywords bundled keyword suggestions for the currently chosen category,
 *   pre-populated and individually editable/removable (DQC-2.3).
 * @property existingGoalNamesLower the user's existing goal names, lower-cased, for inline
 *   case-insensitive uniqueness (DQC-2.6).
 * @property llmConfigured whether an AI provider is configured (gates "Suggest with AI" — DQC-2.5).
 * @property isSuggesting whether an AI suggestion request is currently in flight.
 * @property isSaving whether a save is in flight (disables inputs and the save action).
 */
@Immutable
data class GoalSheetState(
    val presetCategories: List<String> = emptyList(),
    val suggestedKeywords: List<String> = emptyList(),
    val existingGoalNamesLower: Set<String> = emptySet(),
    val llmConfigured: Boolean = false,
    val isSuggesting: Boolean = false,
    val isSaving: Boolean = false,
)

/**
 * Callbacks [GoalCreationSheet] invokes. The sheet owns transient form state (the text fields and
 * the working keyword list); these callbacks hand the *committed* action or the events that need
 * the caller (ViewModel) back to it.
 *
 * @property onSave commits the goal, routing to [QuickCreateViewModel.saveGoal] and thus the same
 *   `CreateGoalUseCase` the dedicated Goals screen uses (DQC-1.10). [keywords] is the working list
 *   after any edits/removals; [description] is optional (may be filled by an AI suggestion).
 * @property onDismiss requested on dismissal with no unsaved input (creates nothing — DQC-1.8).
 * @property onDismissWithUnsavedInput requested on dismissal while the sheet holds unsaved input so
 *   the caller can confirm-before-discard (DQC-1.9).
 * @property onCategoryChanged notifies the caller the category changed (preset tap or free text) so
 *   it can refresh [GoalSheetState.suggestedKeywords] from the bundled mapping (DQC-2.3).
 * @property onSuggestWithAi requests AI keyword/description suggestions, sending only the current
 *   name, description, and keywords as context (DQC-2.4). Only offered when
 *   [GoalSheetState.llmConfigured] is `true` (DQC-2.5).
 */
@Immutable
data class GoalSheetCallbacks(
    val onSave: (
        name: String,
        category: String,
        keywords: List<String>,
        description: String?,
    ) -> Unit,
    val onDismiss: () -> Unit,
    val onDismissWithUnsavedInput: () -> Unit,
    val onCategoryChanged: (String) -> Unit = {},
    val onSuggestWithAi: (name: String, description: String?, keywords: List<String>) -> Unit = { _, _, _ -> },
)
