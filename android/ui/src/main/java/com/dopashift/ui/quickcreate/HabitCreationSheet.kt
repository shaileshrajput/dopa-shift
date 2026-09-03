package com.dopashift.ui.quickcreate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dopashift.domain.creation.GoalId
import com.dopashift.domain.creation.GoalValidator
import com.dopashift.domain.creation.HabitAuthoring
import com.dopashift.ui.R
import com.dopashift.ui.theme.DopaShiftTheme

/**
 * The habit Creation_Sheet (DQC-3), presented as a Material 3 [ModalBottomSheet] over the
 * Dashboard so the user never leaves the screen (DQC-1.5).
 *
 * ### First step depends on whether the user has a goal
 * - **>= 1 active goal** ([HabitSheetState.isInlineGoalCapture] `false`): the first step is goal
 *   selection, defaulting to the most-recently-updated active goal supplied as
 *   [HabitSheetState.defaultGoalId] (computed by the shared
 *   [com.dopashift.domain.presentation.DefaultHabitGoalSelector] — DQC-3.1). Saving routes to
 *   `CreateHabitTrackUseCase` via [HabitSheetCallbacks.onSave] with a non-null `goalId`.
 * - **zero active goals** ([HabitSheetState.isInlineGoalCapture] `true`): the first step is
 *   **Inline_Goal_Capture**, collecting the minimum valid goal fields (name, category, >= 1
 *   keyword — DQC-2.1) inline. There is **no blocking error** and **no navigation away** (DQC-3.2).
 *   Saving routes to `CreateGoalWithHabitUseCase` via [HabitSheetCallbacks.onSave] with a `null`
 *   `goalId` and the collected [InlineGoalFields] (DQC-3.4).
 *
 * ### Authoring modes (DQC-3.5, 3.7, 3.9)
 * Three modes are offered — Template, AI plan, and Manual. The AI mode is disabled with an
 * explanatory label and the selection defaults to Template when [HabitSheetState.llmConfigured] is
 * `false` (DQC-3.7). Manual authoring below 30 checkpoints discloses the auto-fill behavior before
 * saving (DQC-3.9).
 *
 * ### Disclosures
 * - When [HabitSheetState.existingActiveTrackForSelectedGoal] is `true`, the sheet shows which
 *   track takes precedence in the Intercept_Overlay before saving (DQC-3.11).
 * - A daily checkpoint reminder is *offered, not required* via a toggle (DQC-3.12).
 *
 * All user-facing copy comes from string resources so the DUX governance gate is satisfied
 * (DUX-4.11); colors and spacing come from [DopaShiftTheme].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitCreationSheet(
    state: HabitSheetState,
    callbacks: HabitSheetCallbacks,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // --- Transient form state owned by the sheet -------------------------------------

    // Goal selection (only meaningful when the user has active goals).
    var selectedGoalId by rememberSaveable(state.defaultGoalId) {
        mutableStateOf(state.defaultGoalId)
    }

    // Inline_Goal_Capture fields (only meaningful when there are zero goals).
    var goalName by rememberSaveable { mutableStateOf("") }
    var goalCategory by rememberSaveable { mutableStateOf("") }
    val keywords = remember { mutableStateListOf<String>() }
    var keywordDraft by rememberSaveable { mutableStateOf("") }

    // Authoring mode; defaults to Template, and never LLM when unconfigured (DQC-3.7).
    var mode by rememberSaveable {
        mutableStateOf(HabitAuthoringMode.TEMPLATE)
    }
    val manualCheckpoints = remember { mutableStateListOf<String>() }
    var manualDraft by rememberSaveable { mutableStateOf("") }

    // Daily reminder — offered, not required (DQC-3.12).
    var reminderEnabled by rememberSaveable { mutableStateOf(false) }

    // Whether any input has been entered, to trigger confirm-before-discard on dismissal (DQC-1.9).
    val hasUnsavedInput = goalName.isNotBlank() ||
        goalCategory.isNotBlank() ||
        keywords.isNotEmpty() ||
        keywordDraft.isNotBlank() ||
        manualCheckpoints.isNotEmpty() ||
        manualDraft.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = {
            if (hasUnsavedInput) callbacks.onDismissWithUnsavedInput() else callbacks.onDismiss()
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DopaShiftTheme.spacing.margin)
                .navigationBarsPadding(),
        ) {
            HabitSheetHeader(
                onClose = {
                    if (hasUnsavedInput) callbacks.onDismissWithUnsavedInput() else callbacks.onDismiss()
                },
            )

            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

            // ---- First step: goal selection OR Inline_Goal_Capture ----------------
            if (state.isInlineGoalCapture) {
                InlineGoalCaptureStep(
                    state = state,
                    name = goalName,
                    onNameChange = { goalName = it.take(GoalValidator.NAME_MAX) },
                    category = goalCategory,
                    onCategoryChange = {
                        val clamped = it.take(GoalValidator.CATEGORY_MAX)
                        goalCategory = clamped
                        callbacks.onCategoryChanged(clamped)
                    },
                    onPresetCategory = { preset ->
                        goalCategory = preset
                        callbacks.onCategoryChanged(preset)
                    },
                    keywords = keywords,
                    onRemoveKeyword = { keywords.remove(it) },
                    keywordDraft = keywordDraft,
                    onKeywordDraftChange = { keywordDraft = it.take(GoalValidator.KEYWORD_MAX_LEN) },
                    onAddKeyword = {
                        val trimmed = keywordDraft.trim()
                        if (trimmed.isNotEmpty() &&
                            keywords.size < GoalValidator.KEYWORDS_MAX &&
                            !keywords.contains(trimmed)
                        ) {
                            keywords.add(trimmed)
                            keywordDraft = ""
                        }
                    },
                    suggestedKeywords = state.suggestedKeywords,
                    onAddSuggestedKeyword = { suggestion ->
                        if (keywords.size < GoalValidator.KEYWORDS_MAX && !keywords.contains(suggestion)) {
                            keywords.add(suggestion)
                        }
                    },
                )
            } else {
                GoalSelectionStep(
                    state = state,
                    selectedGoalId = selectedGoalId,
                    onSelect = { goalId ->
                        selectedGoalId = goalId
                        callbacks.onGoalSelected(goalId)
                    },
                )
            }

            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))

            // ---- Authoring modes (DQC-3.5, 3.7, 3.9) -------------------------------
            AuthoringModeStep(
                state = state,
                selectedMode = mode,
                onModeChange = { mode = it },
                manualCheckpoints = manualCheckpoints,
                manualDraft = manualDraft,
                onManualDraftChange = { manualDraft = it.take(HABIT_CHECKPOINT_MAX) },
                onAddManualCheckpoint = {
                    val trimmed = manualDraft.trim()
                    if (trimmed.isNotEmpty() && manualCheckpoints.size < HABIT_CHECKPOINT_COUNT) {
                        manualCheckpoints.add(trimmed)
                        manualDraft = ""
                    }
                },
            )

            // ---- Precedence disclosure (DQC-3.11) ----------------------------------
            if (!state.isInlineGoalCapture && state.existingActiveTrackForSelectedGoal) {
                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))
                DisclosureBanner(text = stringResource(R.string.qc_habit_precedence_disclosure))
            }

            // ---- Reminder toggle — offered, not required (DQC-3.12) ----------------
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))
            ReminderToggle(
                enabled = reminderEnabled,
                onToggle = { reminderEnabled = it },
            )

            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))

            // ---- Save / Cancel ------------------------------------------------------
            val canSave = canSave(
                state = state,
                selectedGoalId = selectedGoalId,
                goalName = goalName,
                goalCategory = goalCategory,
                keywords = keywords,
                mode = mode,
                manualCheckpoints = manualCheckpoints,
            )

            HabitSheetActions(
                canSave = canSave && !state.isSaving,
                onCancel = {
                    if (hasUnsavedInput) callbacks.onDismissWithUnsavedInput() else callbacks.onDismiss()
                },
                onSave = {
                    val authoring = resolveAuthoring(
                        mode = mode,
                        llmConfigured = state.llmConfigured,
                        templateCategory = state.templateCategory,
                        manualCheckpoints = manualCheckpoints.toList(),
                    )
                    if (state.isInlineGoalCapture) {
                        callbacks.onSave(
                            null,
                            authoring,
                            reminderEnabled,
                            InlineGoalFields(
                                name = goalName.trim(),
                                category = goalCategory.trim(),
                                keywords = keywords.toList(),
                            ),
                        )
                    } else {
                        callbacks.onSave(
                            selectedGoalId,
                            authoring,
                            reminderEnabled,
                            null,
                        )
                    }
                },
            )

            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.margin))
        }
    }
}

// =====================================================================================
// Header
// =====================================================================================

@Composable
private fun HabitSheetHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.qc_habit_sheet_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.qc_habit_sheet_close_content_description),
            )
        }
    }
}

// =====================================================================================
// Goal selection step (DQC-3.1)
// =====================================================================================

@Composable
private fun GoalSelectionStep(
    state: HabitSheetState,
    selectedGoalId: GoalId?,
    onSelect: (GoalId) -> Unit,
) {
    Column(modifier = Modifier.selectableGroup()) {
        Text(
            text = stringResource(R.string.qc_habit_goal_step_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.compact))
        Text(
            text = stringResource(R.string.qc_habit_goal_step_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

        state.goals.forEach { goal ->
            val selected = goal.id == selectedGoalId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(goal.id) },
                    )
                    .padding(vertical = DopaShiftTheme.spacing.compact),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = null)
                Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.base))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = goal.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = goal.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (goal.id == state.defaultGoalId) {
                    AssistChip(
                        onClick = { onSelect(goal.id) },
                        label = { Text(text = stringResource(R.string.qc_habit_goal_default_badge)) },
                    )
                }
            }
        }
    }
}

// =====================================================================================
// Inline_Goal_Capture step (DQC-3.2)
// =====================================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineGoalCaptureStep(
    state: HabitSheetState,
    name: String,
    onNameChange: (String) -> Unit,
    category: String,
    onCategoryChange: (String) -> Unit,
    onPresetCategory: (String) -> Unit,
    keywords: List<String>,
    onRemoveKeyword: (String) -> Unit,
    keywordDraft: String,
    onKeywordDraftChange: (String) -> Unit,
    onAddKeyword: () -> Unit,
    suggestedKeywords: List<String>,
    onAddSuggestedKeyword: (String) -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.qc_habit_inline_goal_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.compact))
        Text(
            text = stringResource(R.string.qc_habit_inline_goal_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))

        // Goal name
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.qc_habit_inline_goal_name_label)) },
            placeholder = { Text(stringResource(R.string.qc_habit_inline_goal_name_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            supportingText = counterSupportingText(name.length, GoalValidator.NAME_MAX),
        )

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

        // Category (free text) + preset picker
        OutlinedTextField(
            value = category,
            onValueChange = onCategoryChange,
            label = { Text(stringResource(R.string.qc_habit_inline_goal_category_label)) },
            placeholder = { Text(stringResource(R.string.qc_habit_inline_goal_category_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            supportingText = counterSupportingText(category.length, GoalValidator.CATEGORY_MAX),
        )

        if (state.presetCategories.isNotEmpty()) {
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base),
                verticalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.compact),
            ) {
                state.presetCategories.forEach { preset ->
                    FilterChip(
                        selected = category == preset,
                        onClick = { onPresetCategory(preset) },
                        label = { Text(preset) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.gutter))

        // Keyword entry
        OutlinedTextField(
            value = keywordDraft,
            onValueChange = onKeywordDraftChange,
            label = { Text(stringResource(R.string.qc_habit_inline_goal_keyword_label)) },
            placeholder = { Text(stringResource(R.string.qc_habit_inline_goal_keyword_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAddKeyword() }),
            trailingIcon = {
                IconButton(onClick = onAddKeyword) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.qc_habit_inline_goal_keyword_add),
                    )
                }
            },
        )

        // Suggested keywords (bundled), individually addable (DQC-2.3).
        val unusedSuggestions = suggestedKeywords.filter { it !in keywords }
        if (unusedSuggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base),
                verticalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.compact),
            ) {
                unusedSuggestions.forEach { suggestion ->
                    AssistChip(
                        onClick = { onAddSuggestedKeyword(suggestion) },
                        label = { Text(suggestion) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
        }

        // Chosen keywords, individually removable (DQC-2.3).
        if (keywords.isNotEmpty()) {
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base),
                verticalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.compact),
            ) {
                keywords.forEach { keyword ->
                    InputChip(
                        selected = false,
                        onClick = { onRemoveKeyword(keyword) },
                        label = { Text(keyword) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(
                                    R.string.qc_habit_inline_goal_keyword_remove_content_description,
                                    keyword,
                                ),
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.compact))
            Text(
                text = stringResource(R.string.qc_habit_inline_goal_keywords_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// =====================================================================================
// Authoring mode step (DQC-3.5, 3.7, 3.9)
// =====================================================================================

@Composable
private fun AuthoringModeStep(
    state: HabitSheetState,
    selectedMode: HabitAuthoringMode,
    onModeChange: (HabitAuthoringMode) -> Unit,
    manualCheckpoints: List<String>,
    manualDraft: String,
    onManualDraftChange: (String) -> Unit,
    onAddManualCheckpoint: () -> Unit,
) {
    Column(modifier = Modifier.selectableGroup()) {
        Text(
            text = stringResource(R.string.qc_habit_authoring_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))

        AuthoringModeRow(
            mode = HabitAuthoringMode.TEMPLATE,
            selected = selectedMode == HabitAuthoringMode.TEMPLATE,
            enabled = true,
            title = stringResource(R.string.qc_habit_mode_template),
            description = stringResource(R.string.qc_habit_mode_template_description),
            onSelect = { onModeChange(HabitAuthoringMode.TEMPLATE) },
        )

        // AI plan — disabled with explanatory label when no provider is configured (DQC-3.7).
        AuthoringModeRow(
            mode = HabitAuthoringMode.LLM,
            selected = selectedMode == HabitAuthoringMode.LLM,
            enabled = state.llmConfigured,
            title = stringResource(R.string.qc_habit_mode_llm),
            description = if (state.llmConfigured) {
                stringResource(R.string.qc_habit_mode_llm_description)
            } else {
                stringResource(R.string.qc_habit_mode_llm_disabled)
            },
            onSelect = { if (state.llmConfigured) onModeChange(HabitAuthoringMode.LLM) },
        )

        AuthoringModeRow(
            mode = HabitAuthoringMode.MANUAL,
            selected = selectedMode == HabitAuthoringMode.MANUAL,
            enabled = true,
            title = stringResource(R.string.qc_habit_mode_manual),
            description = stringResource(R.string.qc_habit_mode_manual_description),
            onSelect = { onModeChange(HabitAuthoringMode.MANUAL) },
        )

        if (selectedMode == HabitAuthoringMode.MANUAL) {
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
            OutlinedTextField(
                value = manualDraft,
                onValueChange = onManualDraftChange,
                label = { Text(stringResource(R.string.qc_habit_manual_input_label)) },
                placeholder = { Text(stringResource(R.string.qc_habit_manual_input_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAddManualCheckpoint() }),
                supportingText = counterSupportingText(manualDraft.length, HABIT_CHECKPOINT_MAX),
                trailingIcon = {
                    IconButton(
                        onClick = onAddManualCheckpoint,
                        enabled = manualCheckpoints.size < HABIT_CHECKPOINT_COUNT,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.qc_habit_manual_add),
                        )
                    }
                },
            )
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.compact))
            Text(
                text = stringResource(R.string.qc_habit_manual_count, manualCheckpoints.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Auto-fill disclosure before saving (DQC-3.9): shown when 1..29 written.
            if (manualCheckpoints.isNotEmpty() && manualCheckpoints.size < HABIT_CHECKPOINT_COUNT) {
                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
                DisclosureBanner(
                    text = stringResource(
                        R.string.qc_habit_autofill_disclosure,
                        manualCheckpoints.size,
                    ),
                )
            }
        }
    }
}

@Composable
private fun AuthoringModeRow(
    mode: HabitAuthoringMode,
    selected: Boolean,
    enabled: Boolean,
    title: String,
    description: String,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(vertical = DopaShiftTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.base))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// =====================================================================================
// Reminder toggle (DQC-3.12) & disclosure banner
// =====================================================================================

@Composable
private fun ReminderToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.qc_habit_reminder_label),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.qc_habit_reminder_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.base))
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun DisclosureBanner(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(DopaShiftTheme.spacing.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(DopaShiftTheme.spacing.base))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

// =====================================================================================
// Actions
// =====================================================================================

@Composable
private fun HabitSheetActions(
    canSave: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base),
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f),
        ) {
            Text(text = stringResource(R.string.qc_habit_cancel))
        }
        Button(
            onClick = onSave,
            enabled = canSave,
            modifier = Modifier.weight(1f),
        ) {
            Text(text = stringResource(R.string.qc_habit_save))
        }
    }
}

// =====================================================================================
// Helpers
// =====================================================================================

/** A per-field character counter shown when within 20 characters of [max] (DQC-2.7). */
private fun counterSupportingText(length: Int, max: Int): (@Composable () -> Unit)? =
    if (length >= max - COUNTER_THRESHOLD) {
        {
            Text(
                text = stringResource(R.string.qc_habit_char_counter, length, max),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    } else {
        null
    }

/**
 * Whether the sheet currently holds enough valid input to save.
 *
 * - Goal-selection path: a goal must be selected.
 * - Inline_Goal_Capture path: name/category/keywords must pass the shared [GoalValidator] with no
 *   existing-name conflict knowledge here (server + ViewModel re-check on save — DQC-1.11).
 * - Manual authoring additionally requires at least one written checkpoint (the rest auto-fill).
 */
private fun canSave(
    state: HabitSheetState,
    selectedGoalId: GoalId?,
    goalName: String,
    goalCategory: String,
    keywords: List<String>,
    mode: HabitAuthoringMode,
    manualCheckpoints: List<String>,
): Boolean {
    val firstStepValid = if (state.isInlineGoalCapture) {
        GoalValidator.validate(
            name = goalName,
            category = goalCategory,
            keywords = keywords,
            existingNamesLower = emptySet(),
        ).isEmpty()
    } else {
        selectedGoalId != null
    }

    val authoringValid = when (mode) {
        HabitAuthoringMode.MANUAL -> manualCheckpoints.isNotEmpty()
        HabitAuthoringMode.TEMPLATE, HabitAuthoringMode.LLM -> true
    }

    return firstStepValid && authoringValid
}

/**
 * Assemble the [HabitAuthoring] payload from the selected [mode]. LLM mode falls back to Template
 * when no provider is configured (DQC-3.7). Manual carries the authored checkpoints (auto-fill to
 * 30 happens in the domain resolver — DQC-3.9); Template references the goal category so the
 * bundled default template is chosen at resolution time.
 */
private fun resolveAuthoring(
    mode: HabitAuthoringMode,
    llmConfigured: Boolean,
    templateCategory: String?,
    manualCheckpoints: List<String>,
): HabitAuthoring = when (mode) {
    HabitAuthoringMode.MANUAL -> HabitAuthoring.Manual(manualCheckpoints)
    HabitAuthoringMode.LLM ->
        if (llmConfigured) {
            // Descriptions are generated asynchronously by the ViewModel/use case; an empty list
            // is a placeholder the LLM coordinator replaces before resolution. When somehow
            // unconfigured, fall back to a Template payload (DQC-3.7).
            HabitAuthoring.Llm(emptyList())
        } else {
            HabitAuthoring.Template(templateId = templateCategory.orEmpty().ifBlank { DEFAULT_TEMPLATE_ID })
        }
    HabitAuthoringMode.TEMPLATE ->
        HabitAuthoring.Template(templateId = templateCategory.orEmpty().ifBlank { DEFAULT_TEMPLATE_ID })
}

/** Number of checkpoints every habit resolves to. Mirrors HabitTemplate.CHECKPOINT_COUNT. */
private const val HABIT_CHECKPOINT_COUNT = 30

/** Maximum length of a single checkpoint description. Mirrors HabitTemplate.DESCRIPTION_MAX. */
private const val HABIT_CHECKPOINT_MAX = 200

/** Show the character counter within this many characters of a field's limit (DQC-2.7). */
private const val COUNTER_THRESHOLD = 20

/**
 * Placeholder template id used when the category has no specific bundled template; the domain
 * [HabitAuthoringResolver] falls back to the category's default template for a blank/unknown id.
 */
private const val DEFAULT_TEMPLATE_ID = "default"
